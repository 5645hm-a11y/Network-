package com.networkabsorb.ai

import android.util.Log
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.cache.CachedResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-powered prediction engine for proactive content caching.
 *
 * Strategy (no external ML model required for PoC):
 *
 * 1. FREQUENCY ANALYSIS – tracks URL access patterns using an exponential
 *    moving average (EMA) score. URLs with high EMA get prefetched on WiFi.
 *
 * 2. SEQUENCE PREDICTION – records URL visit sequences. If URL A is always
 *    followed by URL B, seeing A triggers prefetch of B.
 *
 * 3. TIME-PATTERN LEARNING – records the time-of-day when each URL is accessed.
 *    Before the expected access time, it prefetches proactively.
 *
 * 4. PRIORITY QUEUE – when bandwidth is limited, prioritizes what to keep
 *    vs. evict based on predicted future demand.
 *
 * Phase 2 (post-PoC): Replace heuristic scoring with TFLite LSTM model trained
 * on the user's own access log. Model runs fully on-device.
 */
@Singleton
class PredictionEngine @Inject constructor(
    private val cacheEngine: CacheEngine
) {
    companion object {
        private const val TAG = "PredictionEngine"
        private const val EMA_ALPHA          = 0.3f   // Smoothing factor for frequency EMA
        private const val MIN_PREFETCH_SCORE = 0.4f   // Minimum score to trigger prefetch
        private const val MAX_SEQUENCE_LEN   = 5      // Sequence memory depth
        private const val MAX_TRACKED_URLS   = 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // EMA frequency scores per URL
    private val frequencyScores = LinkedHashMap<String, Float>(MAX_TRACKED_URLS, 0.75f, true)

    // Sequence map: URL → list of (nextUrl, count) pairs
    private val sequenceMap = HashMap<String, MutableMap<String, Int>>()

    // Recent visit sequence for sequence learning
    private val recentSequence = ArrayDeque<String>(MAX_SEQUENCE_LEN)

    // Time-of-day buckets (hourly) per host
    private val hourlyAccess = HashMap<String, IntArray>()  // host → [24 ints]

    // -------------------------------------------------------------------------
    // Learning (called on each real network request)
    // -------------------------------------------------------------------------

    fun recordAccess(url: String) {
        val normalizedUrl = normalizeUrl(url)
        val host          = extractHost(url)
        val currentHour   = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        // Update frequency EMA
        val prev = frequencyScores[normalizedUrl] ?: 0f
        frequencyScores[normalizedUrl] = (EMA_ALPHA * 1f) + ((1 - EMA_ALPHA) * prev)

        // Evict least-frequent if map too large
        if (frequencyScores.size > MAX_TRACKED_URLS) {
            frequencyScores.entries.minByOrNull { it.value }?.let {
                frequencyScores.remove(it.key)
            }
        }

        // Update sequence learning
        if (recentSequence.isNotEmpty()) {
            val prevUrl = recentSequence.last()
            val transitions = sequenceMap.getOrPut(prevUrl) { mutableMapOf() }
            transitions[normalizedUrl] = (transitions[normalizedUrl] ?: 0) + 1
        }
        recentSequence.addLast(normalizedUrl)
        if (recentSequence.size > MAX_SEQUENCE_LEN) recentSequence.removeFirst()

        // Update hourly access pattern
        val buckets = hourlyAccess.getOrPut(host) { IntArray(24) }
        buckets[currentHour]++

        Log.v(TAG, "Recorded access: $normalizedUrl (score=${frequencyScores[normalizedUrl]?.let { "%.2f".format(it) }})")
    }

    // -------------------------------------------------------------------------
    // Prediction (called to decide what to prefetch)
    // -------------------------------------------------------------------------

    /** Returns a ranked list of URLs to prefetch next, based on the current URL. */
    fun predictNext(currentUrl: String): List<String> {
        val normalized = normalizeUrl(currentUrl)
        val transitions = sequenceMap[normalized] ?: return emptyList()

        return transitions.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .also { Log.d(TAG, "Predicted next after $normalized: $it") }
    }

    /** Returns URLs that are likely needed soon based on time-of-day patterns. */
    fun predictByTimeOfDay(): List<String> {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val nextHour    = (currentHour + 1) % 24

        return hourlyAccess.entries
            .filter { (_, buckets) -> buckets[nextHour] > 2 }  // Accessed this hour >2 times
            .sortedByDescending { it.value[nextHour] }
            .take(10)
            .map { it.key }  // Returns hosts, not full URLs
    }

    /** Scores a URL for cache retention (0.0 = evict, 1.0 = keep). */
    fun retentionScore(url: String): Float {
        val freq = frequencyScores[normalizeUrl(url)] ?: 0f
        val host = extractHost(url)
        val hourBuckets = hourlyAccess[host]
        val timePeak = hourBuckets?.max()?.toFloat() ?: 0f
        val timeScore = minOf(timePeak / 10f, 1f)
        return (freq * 0.7f) + (timeScore * 0.3f)
    }

    // -------------------------------------------------------------------------
    // Proactive prefetch trigger
    // -------------------------------------------------------------------------

    /**
     * Called after each real request. Triggers background prefetch of
     * predicted next URLs that aren't already cached.
     */
    fun triggerPrefetch(currentUrl: String, fetcher: suspend (String) -> Unit) {
        val predicted = predictNext(currentUrl)
        scope.launch {
            for (url in predicted) {
                val key = "GET:$url"
                if (cacheEngine.get(key) == null) {
                    Log.i(TAG, "Prefetching predicted URL: $url")
                    try { fetcher(url) } catch (e: Exception) {
                        Log.w(TAG, "Prefetch failed for $url: ${e.message}")
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun normalizeUrl(url: String): String =
        url.trimEnd('/').lowercase().substringBefore('?')

    private fun extractHost(url: String): String = try {
        java.net.URL(url).host
    } catch (_: Exception) {
        url.substringAfter("://").substringBefore("/")
    }

    /** Returns a summary report of learned patterns for the debug UI. */
    fun getInsightsReport(): Map<String, Any> = mapOf(
        "trackedUrls"   to frequencyScores.size,
        "sequenceRules" to sequenceMap.size,
        "trackedHosts"  to hourlyAccess.size,
        "topUrls"       to frequencyScores.entries
                               .sortedByDescending { it.value }
                               .take(10)
                               .map { it.key to "%.2f".format(it.value) }
    )
}
