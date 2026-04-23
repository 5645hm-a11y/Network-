package com.networkabsorb.proxy

import android.util.Log
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.cache.CachedResponse
import com.networkabsorb.logger.TrafficLogger
import com.networkabsorb.security.CertificateManager
import com.networkabsorb.vpn.InternetExtractorVpnService.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

/**
 * Local HTTP/HTTPS proxy server.
 *
 * ABSORB mode:
 *   - Forwards requests to real servers via direct sockets (our app bypasses VPN).
 *   - Caches responses in CacheEngine.
 *   - For apps with certificate pinning (Google, Apple, Facebook, …): falls back to
 *     transparent relay — traffic passes through unchanged (no caching, but app works).
 *
 * SERVE mode (Virtual SIM — no internet):
 *   - Serves HTTP/HTTPS entirely from CacheEngine.
 *   - Returns 503 for cache misses.
 *
 * HTTPS interception flow (ABSORB, non-pinned hosts):
 *   App → CONNECT hostname:443 → proxy → raw TCP to real server
 *   → TLS handshake with real server
 *   → fake cert for hostname presented to app (signed by CA)
 *   → decrypted HTTP relayed through proxy for caching
 *
 * Transparent relay (cert-pinned hosts):
 *   App → CONNECT hostname:443 → proxy → raw TCP to real server
 *   → bytes relayed bidirectionally — no TLS termination
 */
class LocalHttpProxyServer(
    private val port: Int,
    private val cacheEngine: CacheEngine,
    private val certificateManager: CertificateManager,
    private val trafficLogger: TrafficLogger,
    private val mode: Mode
) {
    companion object {
        private const val TAG = "LocalProxy"
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS       = 30_000
        private const val RELAY_TIMEOUT_MS      = 60_000L

        // Domains known to use strict certificate pinning.
        // These receive transparent relay so apps continue working during ABSORB.
        // (In SERVE mode they will fail — no cached content available for them.)
        private val PINNED_DOMAINS = setOf(
            // Google
            "google.com", "googleapis.com", "gstatic.com", "google-analytics.com",
            "googletagmanager.com", "googleusercontent.com", "googlevideo.com",
            "youtube.com", "ytimg.com", "youtu.be", "gmail.com",
            // Apple
            "apple.com", "icloud.com", "mzstatic.com", "cdn-apple.com",
            // Meta / Facebook
            "facebook.com", "fbcdn.net", "instagram.com", "cdninstagram.com",
            "whatsapp.com", "whatsapp.net",
            // Twitter / X
            "twitter.com", "twimg.com", "t.co", "x.com",
            // Amazon / AWS
            "amazon.com", "amazonaws.com", "cloudfront.net",
            // Microsoft
            "microsoft.com", "live.com", "office.com", "msn.com",
            "skype.com", "microsoftonline.com",
            // Other major pinned services
            "paypal.com", "stripe.com", "dropbox.com", "slack.com", "zoom.us"
        )
    }

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    // Hosts where MITM was rejected at runtime (cert pinning detected dynamically)
    private val bypassedHosts = ConcurrentHashMap.newKeySet<String>()

    suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        serverSocket = ServerSocket(port)
        Log.i(TAG, "Proxy started on :$port in $mode mode")
        while (running) {
            try {
                val client = serverSocket!!.accept()
                Thread { handleConnection(client) }.start()
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Accept error", e)
            }
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        Log.i(TAG, "Proxy stopped")
    }

    // ─── Connection dispatcher ────────────────────────────────────────────────

    private fun handleConnection(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        try {
            val reader    = BufferedReader(InputStreamReader(client.getInputStream()))
            val firstLine = reader.readLine() ?: return

            // Legacy routing-hint prefix (unused, kept for safety)
            val requestLine = if (firstLine.startsWith("DEST ")) {
                reader.readLine() ?: return
            } else firstLine

            when {
                requestLine.startsWith("CONNECT") ->
                    handleHttpsConnect(client, reader, requestLine)
                requestLine.startsWith("GET")    ||
                requestLine.startsWith("POST")   ||
                requestLine.startsWith("PUT")    ||
                requestLine.startsWith("DELETE") ||
                requestLine.startsWith("HEAD")   ->
                    handleHttp(client, reader, requestLine)
                else -> Log.w(TAG, "Unknown method: $requestLine")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Connection error: ${e.message}")
        } finally {
            client.runCatching { close() }
        }
    }

    // ─── Plain HTTP ───────────────────────────────────────────────────────────

    private fun handleHttp(client: Socket, reader: BufferedReader, requestLine: String) {
        val out = client.getOutputStream()
        try {
            val headers = readHeaders(reader)
            val method  = requestLine.substringBefore(' ')
            val url     = requestLine.substringAfter(' ').substringBefore(' ')

            trafficLogger.logRequest(method, url, headers)

            val cacheKey = "$method:$url"
            val cached   = cacheEngine.get(cacheKey)

            if (cached != null && (mode == Mode.SERVE || !cached.isStale())) {
                trafficLogger.logCacheHit(url)
                writeCachedResponse(out, cached)
                return
            }
            if (mode == Mode.SERVE) { writeOfflineError(out, url); return }

            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = CONNECTION_TIMEOUT_MS
            conn.readTimeout    = READ_TIMEOUT_MS
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connect()

            val statusCode = conn.responseCode
            val body       = runCatching { conn.inputStream.readBytes() }
                                 .getOrElse { conn.errorStream?.readBytes() ?: ByteArray(0) }

            trafficLogger.logResponse(url, statusCode, body.size)

            val sb = StringBuilder("HTTP/1.1 $statusCode ${conn.responseMessage}\r\n")
            conn.headerFields.forEach { (k, vs) ->
                if (k != null) sb.append("$k: ${vs.joinToString(", ")}\r\n")
            }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray())
            out.write(body)
            out.flush()

            if (statusCode in 200..299) {
                cacheEngine.put(cacheKey, CachedResponse(
                    statusCode  = statusCode,
                    headers     = conn.headerFields.entries
                                     .filter { it.key != null }
                                     .joinToString("\n") { "${it.key}::${it.value.joinToString(", ")}" },
                    body        = body,
                    url         = url,
                    method      = method,
                    contentType = conn.contentType ?: "application/octet-stream",
                    timestampMs = System.currentTimeMillis(),
                    ttlMs       = parseCacheControlMaxAge(conn.getHeaderField("Cache-Control"))
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP error", e)
            writeError(out, 502, "Bad Gateway: ${e.message}")
        }
    }

    // ─── HTTPS CONNECT ────────────────────────────────────────────────────────

    private fun handleHttpsConnect(client: Socket, reader: BufferedReader, requestLine: String) {
        val out = client.getOutputStream()
        try {
            val target   = requestLine.split(" ")[1]
            val hostname = target.substringBefore(':')
            val port     = target.substringAfter(':').toIntOrNull() ?: 443

            // Drain CONNECT request headers
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {}

            // Acknowledge the tunnel
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            out.flush()

            // ── SERVE mode: answer from cache ─────────────────────────────────
            if (mode == Mode.SERVE) {
                serveHttpsFromCache(client, hostname)
                return
            }

            // ── ABSORB mode: connect to real server ───────────────────────────
            val upstream = Socket()
            upstream.connect(InetSocketAddress(hostname, port), CONNECTION_TIMEOUT_MS)
            upstream.soTimeout = READ_TIMEOUT_MS

            if (shouldTransparentRelay(hostname)) {
                // Known cert-pinned host — relay raw bytes, no MITM
                Log.d(TAG, "Transparent relay: $hostname")
                relayTransparent(client, upstream)
                return
            }

            // ── Try MITM ──────────────────────────────────────────────────────
            try {
                val upstreamSsl = javax.net.ssl.SSLContext.getDefault()
                    .socketFactory.createSocket(upstream, hostname, port, true) as SSLSocket
                upstreamSsl.startHandshake()

                val clientSsl = certificateManager.buildSslContextForHost(hostname)
                    .socketFactory.createSocket(client, client.inetAddress.hostAddress, client.port, true) as SSLSocket
                clientSsl.useClientMode = false

                try {
                    clientSsl.startHandshake()
                } catch (e: SSLHandshakeException) {
                    // App rejected our cert → cert pinning detected at runtime
                    Log.d(TAG, "MITM rejected by $hostname → switching to transparent relay")
                    bypassedHosts.add(hostname)
                    // Connection already broken; app will retry → next attempt uses relay
                    return
                }

                // MITM successful — relay decrypted HTTP and cache it
                val cReader  = BufferedReader(InputStreamReader(clientSsl.inputStream))
                val innerReq = cReader.readLine() ?: return
                val innerHeaders = readHeaders(cReader)

                if (innerHeaders["Upgrade"]?.lowercase() == "websocket") {
                    // WebSocket — transparent relay of decrypted stream
                    Log.d(TAG, "WebSocket relay: $hostname")
                    relayTransparent(clientSsl.inputStream, upstreamSsl.outputStream,
                                     upstreamSsl.inputStream, clientSsl.outputStream)
                    return
                }

                handleDecryptedHttps(clientSsl, upstreamSsl, cReader, innerReq, innerHeaders, hostname)

            } catch (e: Exception) {
                Log.d(TAG, "HTTPS MITM error $hostname: ${e.message}")
                upstream.runCatching { close() }
            }

        } catch (e: Exception) {
            Log.e(TAG, "HTTPS CONNECT error", e)
            writeError(out, 502, "Bad Gateway: ${e.message}")
        }
    }

    private fun shouldTransparentRelay(hostname: String): Boolean {
        if (bypassedHosts.contains(hostname)) return true
        val baseDomain = hostname.split('.').let {
            if (it.size >= 2) it.takeLast(2).joinToString(".") else hostname
        }
        return PINNED_DOMAINS.contains(baseDomain)
    }

    // ─── Transparent byte relay ───────────────────────────────────────────────

    private fun relayTransparent(client: Socket, upstream: Socket) {
        relayTransparent(
            clientIn  = client.getInputStream(),
            upOut     = upstream.getOutputStream(),
            upIn      = upstream.getInputStream(),
            clientOut = client.getOutputStream()
        )
        upstream.runCatching { close() }
    }

    private fun relayTransparent(
        clientIn: InputStream, upOut: OutputStream,
        upIn: InputStream,  clientOut: OutputStream
    ) {
        val t1 = Thread {
            try { clientIn.copyTo(upOut) } catch (_: Exception) {}
            runCatching { upOut.close() }
        }
        val t2 = Thread {
            try { upIn.copyTo(clientOut) } catch (_: Exception) {}
            runCatching { clientOut.close() }
        }
        t1.start(); t2.start()
        t1.join(RELAY_TIMEOUT_MS); t2.join(RELAY_TIMEOUT_MS)
    }

    // ─── Decrypted HTTPS (post-MITM) ─────────────────────────────────────────

    private fun handleDecryptedHttps(
        clientSsl: SSLSocket,
        upstreamSsl: SSLSocket,
        reader: BufferedReader,
        requestLine: String,
        headers: Map<String, String>,
        hostname: String
    ) {
        val upOut    = upstreamSsl.outputStream
        val clientOut = clientSsl.outputStream

        try {
            val method = requestLine.substringBefore(' ')
            val path   = requestLine.substringAfter(' ').substringBefore(' ')
            val url    = "https://$hostname$path"

            trafficLogger.logRequest(method, url, headers)

            val cacheKey = "$method:$url"
            val cached   = cacheEngine.get(cacheKey)
            if (cached != null && !cached.isStale()) {
                trafficLogger.logCacheHit(url)
                writeCachedResponse(clientOut, cached)
                return
            }

            // Forward request upstream
            val reqSb = StringBuilder("$requestLine\r\n")
            headers.forEach { (k, v) -> reqSb.append("$k: $v\r\n") }
            reqSb.append("\r\n")
            upOut.write(reqSb.toString().toByteArray())
            upOut.flush()

            // Read upstream response
            val upReader = BufferedReader(InputStreamReader(upstreamSsl.inputStream))
            val statusLine  = upReader.readLine() ?: return
            val statusCode  = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 200
            val respHeaders = readHeaders(upReader)

            val body = upstreamSsl.inputStream.readBytes()
            trafficLogger.logResponse(url, statusCode, body.size)

            val respSb = StringBuilder("$statusLine\r\n")
            respHeaders.forEach { (k, v) -> respSb.append("$k: $v\r\n") }
            respSb.append("\r\n")
            clientOut.write(respSb.toString().toByteArray())
            clientOut.write(body)
            clientOut.flush()

            if (statusCode in 200..299) {
                cacheEngine.put(cacheKey, CachedResponse(
                    statusCode  = statusCode,
                    headers     = respHeaders.entries.joinToString("\n") { "${it.key}::${it.value}" },
                    body        = body,
                    url         = url,
                    method      = method,
                    contentType = respHeaders["Content-Type"] ?: "application/octet-stream",
                    timestampMs = System.currentTimeMillis(),
                    ttlMs       = parseCacheControlMaxAge(respHeaders["Cache-Control"])
                ))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Decrypted HTTPS relay error: ${e.message}")
        } finally {
            upstreamSsl.runCatching { close() }
        }
    }

    // ─── SERVE mode HTTPS ─────────────────────────────────────────────────────

    private fun serveHttpsFromCache(client: Socket, hostname: String) {
        try {
            val sslCtx    = certificateManager.buildSslContextForHost(hostname)
            val clientSsl = sslCtx.socketFactory.createSocket(
                client, client.inetAddress.hostAddress, client.port, true
            ) as SSLSocket
            clientSsl.useClientMode = false
            clientSsl.startHandshake()

            val reader      = BufferedReader(InputStreamReader(clientSsl.inputStream))
            val requestLine = reader.readLine() ?: return
            val headers     = readHeaders(reader)
            val path        = requestLine.substringAfter(' ').substringBefore(' ')
            val method      = requestLine.substringBefore(' ')
            val url         = "https://$hostname$path"
            val cached      = cacheEngine.get("$method:$url")

            if (cached != null) writeCachedResponse(clientSsl.outputStream, cached)
            else writeOfflineError(clientSsl.outputStream, url)

        } catch (e: Exception) {
            Log.d(TAG, "Serve HTTPS error $hostname: ${e.message}")
        }
    }

    // ─── Response writers ─────────────────────────────────────────────────────

    private fun writeCachedResponse(out: OutputStream, cached: CachedResponse) {
        val sb = StringBuilder("HTTP/1.1 ${cached.statusCode} OK\r\n")
        if (cached.headers.isNotEmpty()) {
            cached.headers.split("\n").forEach { entry ->
                val idx = entry.indexOf("::")
                if (idx > 0) sb.append("${entry.substring(0, idx)}: ${entry.substring(idx + 2)}\r\n")
            }
        }
        sb.append("X-Cache: HIT\r\nContent-Length: ${cached.body.size}\r\n\r\n")
        out.write(sb.toString().toByteArray())
        out.write(cached.body)
        out.flush()
    }

    private fun writeOfflineError(out: OutputStream, url: String) {
        val body = """{"error":"offline","url":"$url"}""".toByteArray()
        out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun writeError(out: OutputStream, code: Int, message: String) {
        val body = message.toByteArray()
        out.runCatching {
            write("HTTP/1.1 $code Error\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
            write(body); flush()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var line: String?
        while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
            val colon = line!!.indexOf(':')
            if (colon > 0) headers[line!!.substring(0, colon).trim()] = line!!.substring(colon + 1).trim()
        }
        return headers
    }

    private fun parseCacheControlMaxAge(header: String?): Long {
        if (header == null) return 3_600_000L
        return (Regex("max-age=(\\d+)").find(header)?.groupValues?.get(1)?.toLongOrNull() ?: 3600L) * 1000L
    }
}
