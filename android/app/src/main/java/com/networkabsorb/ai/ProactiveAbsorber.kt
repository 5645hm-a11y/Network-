package com.networkabsorb.ai

import android.util.Log
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.cache.CachedResponse
import com.networkabsorb.logger.TrafficLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proactive absorber — fetches internet content DIRECTLY (no proxy).
 *
 * Our app is excluded from the VPN via addDisallowedApplication(), so a plain
 * OkHttpClient connects straight to the internet without routing through TUN.
 * Responses are stored in CacheEngine manually and logged to TrafficLogger so
 * the UI shows live absorption stats.
 *
 * The previous proxy-based approach silently failed: OkHttp inside our app
 * rejected the MITM certificate presented by our own proxy → SSLException → nothing cached.
 */
@Singleton
class ProactiveAbsorber @Inject constructor(
    private val cacheEngine: CacheEngine,
    private val predictionEngine: PredictionEngine,
    private val trafficLogger: TrafficLogger
) {
    companion object {
        private const val TAG = "ProactiveAbsorber"

        private val SEED_URLS = listOf(
            // Reference / knowledge
            "https://en.wikipedia.org/wiki/Main_Page",
            "https://he.wikipedia.org/wiki/%D7%A2%D7%9E%D7%95%D7%93_%D7%A8%D7%90%D7%A9%D7%99",
            "https://www.wikipedia.org/",
            // News
            "https://www.bbc.com/news",
            "https://news.google.com/",
            "https://www.ynet.co.il/",
            // Maps / navigation (open-source, no cert-pinning)
            "https://www.openstreetmap.org/",
            "https://nominatim.openstreetmap.org/search?q=israel&format=json",
            // Utility APIs
            "https://api.ipify.org/?format=json",
            "https://worldtimeapi.org/api/ip",
            "https://wttr.in/?format=j1",
            // CDN resources used by most sites
            "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js",
            "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css",
            "https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap",
            // Search
            "https://www.google.com/",
            "https://duckduckgo.com/"
        )

        private const val MAX_CRAWL_URLS   = 300
        private const val DELAY_BETWEEN_MS = 250L
        private const val CONNECT_TIMEOUT  = 12L
        private const val READ_TIMEOUT     = 25L
        private const val TTL_MS           = 7 * 24 * 3600_000L  // 7 days
    }

    private var job: Job? = null
    @Volatile var quotaBytes = 500L * 1024 * 1024

    /** Direct HTTP client — our app bypasses VPN so this hits the real internet. */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun start(scope: CoroutineScope, quotaMb: Int) {
        quotaBytes = quotaMb.toLong() * 1024 * 1024
        job = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Absorber started (quota=${quotaMb}MB)")
            delay(1500)  // let VPN interface fully establish first
            try { absorb(this) }
            catch (e: Exception) { Log.e(TAG, "Absorber error", e) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.i(TAG, "Absorber stopped")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Absorption pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun absorb(scope: CoroutineScope) {
        val visited = mutableSetOf<String>()

        // Predicted URLs from previous sessions
        predictionEngine.predictByTimeOfDay().forEach { url ->
            if (!scope.isActive || shouldStop()) return
            fetchAndStore(url, visited)
            delay(DELAY_BETWEEN_MS)
        }

        // Seed URLs + crawl (depth 2)
        for (url in SEED_URLS) {
            if (!scope.isActive || shouldStop()) return
            val html = fetchAndStore(url, visited)
            delay(DELAY_BETWEEN_MS)

            if (html != null) {
                val links = extractLinks(html, url).take(20)
                for (link in links) {
                    if (!scope.isActive || shouldStop()) return
                    val subHtml = fetchAndStore(link, visited)
                    delay(DELAY_BETWEEN_MS)

                    subHtml?.let { sh ->
                        extractLinks(sh, link).take(6).forEach { sub ->
                            if (!scope.isActive || shouldStop()) return
                            fetchAndStore(sub, visited)
                            delay(DELAY_BETWEEN_MS)
                        }
                    }

                    if (visited.size >= MAX_CRAWL_URLS) return
                }
            }
        }

        Log.i(TAG, "Absorption complete — ${visited.size} URLs absorbed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Direct fetch + store in CacheEngine
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchAndStore(url: String, visited: MutableSet<String>): String? {
        if (url in visited) return null
        visited.add(url)

        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120")
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Accept-Language", "en,he;q=0.8")
                .build()

            val resp = httpClient.newCall(req).execute()
            val ct   = resp.header("Content-Type") ?: "application/octet-stream"
            val body = resp.body?.bytes() ?: return null
            resp.close()

            if (body.isEmpty()) return null

            // Store directly in cache — bypasses proxy MITM issues entirely
            cacheEngine.put("GET:$url", CachedResponse(
                statusCode  = resp.code,
                headers     = resp.headers.joinToString("\n") { "${it.first}::${it.second}" },
                body        = body,
                url         = url,
                method      = "GET",
                contentType = ct,
                timestampMs = System.currentTimeMillis(),
                ttlMs       = TTL_MS
            ))

            // Emit to TrafficLogger so UI stats update
            trafficLogger.logResponse(url, resp.code, body.size)
            predictionEngine.recordAccess(url)

            Log.d(TAG, "[${resp.code}] ${body.size}B $url")

            if (ct.contains("html", ignoreCase = true)) String(body, Charsets.UTF_8) else null

        } catch (e: Exception) {
            Log.d(TAG, "Skip $url: ${e.message}")
            null
        }
    }

    private fun extractLinks(html: String, baseUrl: String): List<String> {
        val base = try { java.net.URL(baseUrl) } catch (_: Exception) { return emptyList() }
        return Regex("""href=["']([^"'#?]+)["']""")
            .findAll(html)
            .mapNotNull { m ->
                try {
                    val abs = java.net.URL(base, m.groupValues[1]).toString()
                    if (abs.startsWith("http")) abs else null
                } catch (_: Exception) { null }
            }
            .filter { it != baseUrl }
            .distinct()
            .toList()
    }

    private suspend fun shouldStop(): Boolean = cacheEngine.totalSizeBytes() >= quotaBytes
}
