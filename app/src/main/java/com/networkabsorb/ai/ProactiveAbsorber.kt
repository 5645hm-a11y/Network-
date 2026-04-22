package com.networkabsorb.ai

import android.util.Log
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.logger.TrafficLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-driven proactive absorber.
 *
 * While the VPN is in ABSORB mode this engine runs in the background and
 * systematically downloads high-value internet content through the local
 * proxy so it gets stored in the cache — even if the user never visits
 * those URLs manually.
 *
 * Strategy (four layers):
 *  1. PASSIVE  — content the user actually visits is cached automatically by proxy
 *  2. SEED     — curated list of universally-useful URLs (news, CDN, APIs)
 *  3. CRAWL    — follows links found in already-absorbed HTML pages
 *  4. PREDICT  — uses PredictionEngine history to pre-fetch likely-needed URLs
 */
@Singleton
class ProactiveAbsorber @Inject constructor(
    private val cacheEngine: CacheEngine,
    private val predictionEngine: PredictionEngine,
    private val trafficLogger: TrafficLogger
) {
    companion object {
        private const val TAG = "ProactiveAbsorber"
        private const val PROXY_PORT = 8118

        // Curated seed URLs — high value for most users
        private val SEED_URLS = listOf(
            // Search
            "https://www.google.com/",
            "https://www.bing.com/",

            // News (light pages)
            "https://news.google.com/",
            "https://www.bbc.com/news",
            "https://edition.cnn.com/",

            // Knowledge
            "https://en.wikipedia.org/wiki/Main_Page",
            "https://he.wikipedia.org/wiki/%D7%A2%D7%9E%D7%95%D7%93_%D7%A8%D7%90%D7%A9%D7%99",

            // Common CDN resources cached globally
            "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js",
            "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css",
            "https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap",

            // Weather
            "https://wttr.in/?format=j1",

            // Common APIs
            "https://api.ipify.org/?format=json",
            "https://worldtimeapi.org/api/ip"
        )

        private const val MAX_CRAWL_DEPTH  = 2
        private const val MAX_CRAWL_URLS   = 200
        private const val DELAY_BETWEEN_MS = 300L   // be polite to servers
        private const val CONNECT_TIMEOUT  = 10L
        private const val READ_TIMEOUT     = 20L
    }

    private var job: Job? = null
    private var quotaBytes = 500L * 1024 * 1024  // default 500 MB

    // HTTP client routed through our local proxy — responses get cached automatically
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", PROXY_PORT)))
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun start(scope: CoroutineScope, quotaMb: Int) {
        quotaBytes = quotaMb.toLong() * 1024 * 1024
        job = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Proactive absorber started (quota=${quotaMb}MB)")
            try {
                absorb(this)
            } catch (e: Exception) {
                Log.e(TAG, "Absorber error", e)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.i(TAG, "Proactive absorber stopped")
    }

    // ─── Absorption pipeline ──────────────────────────────────────────────────

    private suspend fun absorb(scope: CoroutineScope) {
        val visited = mutableSetOf<String>()

        // Layer 4: predictions from previous sessions
        val predicted = predictionEngine.predictByTimeOfDay()
        for (url in predicted) {
            if (!scope.isActive) return
            if (shouldStop()) return
            fetchAndCache(url, visited)
            delay(DELAY_BETWEEN_MS)
        }

        // Layer 2: seed URLs
        for (url in SEED_URLS) {
            if (!scope.isActive) return
            if (shouldStop()) return
            val html = fetchAndCache(url, visited)
            delay(DELAY_BETWEEN_MS)

            // Layer 3: crawl links found in seed pages (depth 1)
            if (html != null && MAX_CRAWL_DEPTH > 0) {
                val links = extractLinks(html, url).take(15)
                for (link in links) {
                    if (!scope.isActive) return
                    if (shouldStop()) return
                    val subHtml = fetchAndCache(link, visited)
                    delay(DELAY_BETWEEN_MS)

                    // depth 2
                    if (subHtml != null && MAX_CRAWL_DEPTH > 1) {
                        val subLinks = extractLinks(subHtml, link).take(5)
                        for (sub2 in subLinks) {
                            if (!scope.isActive || shouldStop()) return
                            fetchAndCache(sub2, visited)
                            delay(DELAY_BETWEEN_MS)
                        }
                    }

                    if (visited.size >= MAX_CRAWL_URLS) return
                }
            }
        }

        Log.i(TAG, "Proactive absorption complete — ${visited.size} URLs fetched")
    }

    // ─── Fetch helpers ────────────────────────────────────────────────────────

    /**
     * GETs [url] through the local proxy (which caches it automatically).
     * Returns the response body as String if HTML, null otherwise.
     */
    private fun fetchAndCache(url: String, visited: MutableSet<String>): String? {
        if (url in visited) return null
        visited.add(url)

        return try {
            val req  = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = httpClient.newCall(req).execute()
            val ct   = resp.header("Content-Type") ?: ""
            val body = resp.body?.string()
            resp.close()

            Log.d(TAG, "Absorbed [${resp.code}] $url")
            predictionEngine.recordAccess(url)

            if (ct.contains("html", ignoreCase = true)) body else null
        } catch (e: Exception) {
            Log.d(TAG, "Skip $url: ${e.message}")
            null
        }
    }

    /** Extracts absolute http/https links from HTML text. */
    private fun extractLinks(html: String, baseUrl: String): List<String> {
        val base = try { java.net.URL(baseUrl) } catch (_: Exception) { return emptyList() }
        return Regex("""href=["']([^"'#?]+)["']""")
            .findAll(html)
            .mapNotNull { m ->
                try {
                    val raw = m.groupValues[1]
                    val abs = java.net.URL(base, raw).toString()
                    if (abs.startsWith("http")) abs else null
                } catch (_: Exception) { null }
            }
            .filter { it != baseUrl }
            .distinct()
            .toList()
    }

    private suspend fun shouldStop(): Boolean {
        val used = cacheEngine.totalSizeBytes()
        return used >= quotaBytes
    }
}
