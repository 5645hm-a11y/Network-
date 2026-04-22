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
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Local HTTP/HTTPS proxy server that runs inside the app.
 *
 * In ABSORB mode:
 *   - Forwards requests to real servers
 *   - Saves responses to CacheEngine
 *   - Logs traffic for analysis
 *
 * In SERVE mode:
 *   - Looks up responses in CacheEngine
 *   - Returns cached content without touching the network
 *   - Returns 503 with explanation if cache miss
 *
 * HTTPS interception (MITM) flow:
 *   Client → CONNECT hostname:443 → proxy
 *   proxy → connects to real server (SSL)
 *   proxy → generates dynamic cert for hostname (signed by app CA)
 *   proxy → presents dynamic cert to client
 *   proxy ↔ client (decrypted) ↔ proxy ↔ server (re-encrypted)
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
    }

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        serverSocket = ServerSocket(port)
        Log.i(TAG, "Proxy started on port $port in $mode mode")

        while (running) {
            try {
                val client = serverSocket!!.accept()
                // Each connection handled in its own coroutine-friendly thread
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

    // -------------------------------------------------------------------------
    // Connection dispatcher
    // -------------------------------------------------------------------------

    private fun handleConnection(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))

            // First, check if the PacketProcessor sent a routing hint
            var destIp   = ""
            var destPort = 0
            val firstLine = reader.readLine() ?: return

            if (firstLine.startsWith("DEST ")) {
                val parts = firstLine.split(" ")
                destIp   = parts.getOrNull(1) ?: ""
                destPort = parts.getOrNull(2)?.toIntOrNull() ?: 0
                // Now read the actual HTTP request line
                val requestLine = reader.readLine() ?: return
                dispatchRequest(client, reader, requestLine, destIp, destPort)
            } else {
                // Direct connection (no routing hint from PacketProcessor)
                dispatchRequest(client, reader, firstLine, destIp, destPort)
            }

        } catch (e: Exception) {
            Log.d(TAG, "Connection error: ${e.message}")
        } finally {
            client.runCatching { close() }
        }
    }

    private fun dispatchRequest(
        client: Socket,
        reader: BufferedReader,
        requestLine: String,
        destIp: String,
        destPort: Int
    ) {
        when {
            requestLine.startsWith("CONNECT") -> handleHttpsConnect(client, reader, requestLine)
            requestLine.startsWith("GET")     ||
            requestLine.startsWith("POST")    ||
            requestLine.startsWith("PUT")     ||
            requestLine.startsWith("DELETE")  ||
            requestLine.startsWith("HEAD")    -> handleHttp(client, reader, requestLine)
            else -> Log.w(TAG, "Unknown request method: $requestLine")
        }
    }

    // -------------------------------------------------------------------------
    // HTTP (plain text) handling
    // -------------------------------------------------------------------------

    private fun handleHttp(client: Socket, reader: BufferedReader, requestLine: String) {
        val out = client.getOutputStream()
        try {
            // Parse request
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val colon = line!!.indexOf(':')
                if (colon > 0) {
                    headers[line!!.substring(0, colon).trim()] =
                        line!!.substring(colon + 1).trim()
                }
            }

            val method = requestLine.substringBefore(' ')
            val url    = requestLine.substringAfter(' ').substringBefore(' ')
            val host   = headers["Host"] ?: URL(url).host

            trafficLogger.logRequest(method, url, headers)

            // ABSORB: check cache first (avoid redundant downloads), then fetch
            // SERVE:  cache only
            val cacheKey = "$method:$url"
            val cached   = cacheEngine.get(cacheKey)

            if (cached != null && (mode == Mode.SERVE || !cached.isStale())) {
                trafficLogger.logCacheHit(url)
                writeCachedResponse(out, cached)
                return
            }

            if (mode == Mode.SERVE) {
                writeOfflineError(out, url)
                return
            }

            // Forward to real server
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout    = READ_TIMEOUT_MS
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.connect()

            val statusCode    = connection.responseCode
            val statusMessage = connection.responseMessage
            val responseHeaders = connection.headerFields
            val body = connection.inputStream.readBytes()

            trafficLogger.logResponse(url, statusCode, body.size)

            // Write response back to client
            val sb = StringBuilder("HTTP/1.1 $statusCode $statusMessage\r\n")
            responseHeaders.forEach { (k, values) ->
                if (k != null) sb.append("$k: ${values.joinToString(", ")}\r\n")
            }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray())
            out.write(body)
            out.flush()

            // Cache it
            cacheEngine.put(
                key = cacheKey,
                response = CachedResponse(
                    statusCode    = statusCode,
                    headers       = responseHeaders.entries
                                        .filter { it.key != null }
                                        .joinToString("\n") { "${it.key}::${it.value.joinToString(", ")}" },
                    body          = body,
                    url           = url,
                    method        = method,
                    contentType   = connection.contentType ?: "application/octet-stream",
                    timestampMs   = System.currentTimeMillis(),
                    ttlMs         = parseCacheControlMaxAge(connection.getHeaderField("Cache-Control"))
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "HTTP error", e)
            writeError(out, 502, "Bad Gateway: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // HTTPS CONNECT (MITM)
    // -------------------------------------------------------------------------

    /**
     * HTTPS interception sequence:
     * 1. Parse CONNECT hostname:443 from client
     * 2. Send 200 Connection Established to client
     * 3. Connect to real server with SSL
     * 4. Generate dynamic cert for hostname (signed by our CA)
     * 5. Wrap client socket with SSLSocket using dynamic cert
     * 6. Now both sides are decrypted – relay HTTP as normal
     */
    private fun handleHttpsConnect(
        client: Socket,
        reader: BufferedReader,
        requestLine: String
    ) {
        val out = client.getOutputStream()

        try {
            // Parse: CONNECT hostname:port HTTP/1.1
            val target   = requestLine.split(" ")[1]
            val hostname = target.substringBefore(':')
            val port     = target.substringAfter(':').toIntOrNull() ?: 443

            // Drain remaining CONNECT headers
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) { }

            // Tell client tunnel is open
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            out.flush()

            if (mode == Mode.SERVE) {
                // In serve mode, check cache for this host before attempting SSL
                handleHttpsServeMode(client, hostname, out)
                return
            }

            // Connect to real upstream server
            val upstreamSocket = java.net.Socket(hostname, port)
            val sslContext     = SSLContext.getDefault()
            val sslSocket      = sslContext.socketFactory.createSocket(
                upstreamSocket, hostname, port, true
            ) as SSLSocket
            sslSocket.startHandshake()

            // Build dynamic cert + SSLContext for the client side
            val dynamicSslContext = certificateManager.buildSslContextForHost(hostname)

            val clientSslSocket = dynamicSslContext.socketFactory.createSocket(
                client, client.inetAddress.hostAddress, client.port, true
            ) as SSLSocket

            clientSslSocket.useClientMode = false
            clientSslSocket.startHandshake()

            // Now relay decrypted HTTP between clientSslSocket and sslSocket
            val clientReader = BufferedReader(InputStreamReader(clientSslSocket.inputStream))
            val innerRequest = clientReader.readLine() ?: return
            handleDecryptedHttps(
                clientSslSocket, sslSocket, clientReader,
                innerRequest, hostname
            )

        } catch (e: Exception) {
            Log.e(TAG, "HTTPS MITM error for request: $requestLine", e)
            writeError(out, 502, "MITM error: ${e.message}")
        }
    }

    private fun handleDecryptedHttps(
        clientSocket: SSLSocket,
        upstreamSocket: SSLSocket,
        reader: BufferedReader,
        requestLine: String,
        hostname: String
    ) {
        val upstreamOut = upstreamSocket.outputStream
        val clientOut   = clientSocket.outputStream

        try {
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val colon = line!!.indexOf(':')
                if (colon > 0) {
                    headers[line!!.substring(0, colon).trim()] =
                        line!!.substring(colon + 1).trim()
                }
            }

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

            // Forward to upstream
            val sb = StringBuilder("$requestLine\r\n")
            headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
            sb.append("\r\n")
            upstreamOut.write(sb.toString().toByteArray())
            upstreamOut.flush()

            // Relay response
            val upstreamReader = BufferedReader(InputStreamReader(upstreamSocket.inputStream))
            val statusLine = upstreamReader.readLine() ?: return
            val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 200

            val respHeaders = mutableMapOf<String, String>()
            while (upstreamReader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val colon = line!!.indexOf(':')
                if (colon > 0) {
                    respHeaders[line!!.substring(0, colon).trim()] =
                        line!!.substring(colon + 1).trim()
                }
            }

            val body = upstreamSocket.inputStream.readBytes()

            trafficLogger.logResponse(url, statusCode, body.size)

            val response = StringBuilder("$statusLine\r\n")
            respHeaders.forEach { (k, v) -> response.append("$k: $v\r\n") }
            response.append("\r\n")
            clientOut.write(response.toString().toByteArray())
            clientOut.write(body)
            clientOut.flush()

            val headersStr = respHeaders.entries.joinToString("\n") { "${it.key}::${it.value}" }
            cacheEngine.put(
                key = cacheKey,
                response = CachedResponse(
                    statusCode  = statusCode,
                    headers     = headersStr,
                    body        = body,
                    url         = url,
                    method      = method,
                    contentType = respHeaders["Content-Type"] ?: "application/octet-stream",
                    timestampMs = System.currentTimeMillis(),
                    ttlMs       = parseCacheControlMaxAge(respHeaders["Cache-Control"])
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Decrypted HTTPS relay error", e)
        } finally {
            upstreamSocket.runCatching { close() }
        }
    }

    private fun handleHttpsServeMode(client: Socket, hostname: String, out: OutputStream) {
        // In offline mode we serve from cache for any cached path under this host
        val dynamicSslContext = certificateManager.buildSslContextForHost(hostname)
        try {
            val clientSsl = dynamicSslContext.socketFactory.createSocket(
                client, client.inetAddress.hostAddress, client.port, true
            ) as SSLSocket
            clientSsl.useClientMode = false
            clientSsl.startHandshake()

            val reader      = BufferedReader(InputStreamReader(clientSsl.inputStream))
            val requestLine = reader.readLine() ?: return
            val path        = requestLine.substringAfter(' ').substringBefore(' ')
            val url         = "https://$hostname$path"
            val method      = requestLine.substringBefore(' ')
            val cached      = cacheEngine.get("$method:$url")

            if (cached != null) {
                writeCachedResponse(clientSsl.outputStream, cached)
            } else {
                writeOfflineError(clientSsl.outputStream, url)
            }

        } catch (e: Exception) {
            Log.d(TAG, "Serve mode HTTPS error: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun writeCachedResponse(out: OutputStream, cached: CachedResponse) {
        val sb = StringBuilder("HTTP/1.1 ${cached.statusCode} OK\r\n")
        if (cached.headers.isNotEmpty()) {
            cached.headers.split("\n").forEach { line ->
                val idx = line.indexOf("::")
                if (idx > 0) sb.append("${line.substring(0, idx)}: ${line.substring(idx + 2)}\r\n")
            }
        }
        sb.append("X-Cache: HIT\r\n")
        sb.append("Content-Length: ${cached.body.size}\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
        out.write(cached.body)
        out.flush()
    }

    private fun writeOfflineError(out: OutputStream, url: String) {
        val body = """{"error":"offline","url":"$url","message":"No cached content available"}"""
            .toByteArray()
        val response = "HTTP/1.1 503 Service Unavailable\r\n" +
                       "Content-Type: application/json\r\n" +
                       "Content-Length: ${body.size}\r\n\r\n"
        out.write(response.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun writeError(out: OutputStream, code: Int, message: String) {
        val body     = message.toByteArray()
        val response = "HTTP/1.1 $code Error\r\nContent-Length: ${body.size}\r\n\r\n"
        out.runCatching {
            write(response.toByteArray())
            write(body)
            flush()
        }
    }

    /** Parses Cache-Control: max-age=N header, returns TTL in ms. Default 1 hour. */
    private fun parseCacheControlMaxAge(header: String?): Long {
        if (header == null) return 3_600_000L
        val maxAge = Regex("max-age=(\\d+)").find(header)?.groupValues?.get(1)?.toLongOrNull()
        return (maxAge ?: 3600L) * 1000L
    }
}
