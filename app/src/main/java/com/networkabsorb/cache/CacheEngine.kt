package com.networkabsorb.cache

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level cache API used by the proxy and UI.
 *
 * Wraps Room DAO with:
 *  - In-memory hot cache (LRU, max 200 entries) for sub-millisecond reads
 *  - Content-aware compression (text/JSON/HTML compressed, binary passthrough)
 *  - Automatic eviction when disk usage exceeds [MAX_CACHE_BYTES]
 *  - Background expiry purge on startup
 */
@Singleton
class CacheEngine @Inject constructor(
    private val dao: CacheDao
) {
    companion object {
        private const val TAG = "CacheEngine"
        private const val MAX_CACHE_BYTES = 500L * 1024 * 1024  // 500 MB
        private const val HOT_CACHE_SIZE  = 200
        private const val EVICTION_BATCH  = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe in-memory LRU hot cache
    private val hotCache = object : LinkedHashMap<String, CachedResponse>(
        HOT_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedResponse>): Boolean =
            size > HOT_CACHE_SIZE
    }
    private val hotCacheLock = Any()

    init {
        scope.launch { purgeExpired() }
    }

    // -------------------------------------------------------------------------
    // Core API
    // -------------------------------------------------------------------------

    /** Synchronous get – called from proxy threads. Returns null on cache miss. */
    fun get(key: String): CachedResponse? {
        // 1. Check hot cache first
        synchronized(hotCacheLock) { hotCache[key] }?.let { cached ->
            if (!cached.isStale()) {
                scope.launch { dao.incrementAccess(key) }
                return cached
            }
        }

        // 2. Fall back to Room (blocking – proxy runs on IO thread)
        val cached = runBlocking(Dispatchers.IO) { dao.findByKey(key) } ?: return null

        if (cached.isStale()) {
            scope.launch { dao.delete(key) }
            return null
        }

        val decompressed = if (cached.compressed) decompress(cached.body) else cached.body
        val result = cached.copy(body = decompressed, compressed = false)

        synchronized(hotCacheLock) { hotCache[key] = result }
        scope.launch { dao.incrementAccess(key) }
        return result
    }

    /** Stores a response. Compresses text content automatically. */
    fun put(key: String, response: CachedResponse) {
        scope.launch {
            val shouldCompress = isCompressible(response.contentType)
            val body = if (shouldCompress) compress(response.body) else response.body

            val toStore = response.copy(
                key        = key,
                body       = body,
                compressed = shouldCompress,
                sizeBytes  = body.size
            )

            dao.insert(toStore)
            synchronized(hotCacheLock) {
                hotCache[key] = response.copy(key = key)  // Hot cache keeps raw
            }

            enforceStorageLimit()
        }
    }

    fun observeAll(): Flow<List<CachedResponse>> = dao.observeAll()

    suspend fun getAll(): List<CachedResponse> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun totalSizeBytes(): Long = dao.totalSizeBytes() ?: 0L

    suspend fun clearAll() {
        dao.clearAll()
        synchronized(hotCacheLock) { hotCache.clear() }
        Log.i(TAG, "Cache cleared")
    }

    suspend fun purgeExpired() {
        val purged = dao.purgeExpired(System.currentTimeMillis())
        Log.i(TAG, "Purged expired entries")
    }

    // -------------------------------------------------------------------------
    // Storage limit enforcement
    // -------------------------------------------------------------------------

    private suspend fun enforceStorageLimit() {
        val used = dao.totalSizeBytes() ?: 0L
        if (used > MAX_CACHE_BYTES) {
            Log.i(TAG, "Cache full (${used / 1024 / 1024} MB), evicting…")
            dao.evictLeastValuable(EVICTION_BATCH)
        }
    }

    // -------------------------------------------------------------------------
    // Compression (zlib/Deflate)
    // -------------------------------------------------------------------------

    private fun isCompressible(contentType: String): Boolean {
        return contentType.contains("text") ||
               contentType.contains("json") ||
               contentType.contains("xml") ||
               contentType.contains("javascript") ||
               contentType.contains("css")
    }

    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArray(data.size)
        val compressedSize = deflater.deflate(output)
        deflater.end()
        return output.copyOf(compressedSize)
    }

    private fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArray(data.size * 10)  // Assume max 10x expansion
        val decompressedSize = inflater.inflate(output)
        inflater.end()
        return output.copyOf(decompressedSize)
    }
}
