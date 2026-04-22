package com.networkabsorb.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(response: CachedResponse)

    @Query("SELECT * FROM cached_responses WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): CachedResponse?

    @Query("UPDATE cached_responses SET accessCount = accessCount + 1 WHERE key = :key")
    suspend fun incrementAccess(key: String)

    @Query("SELECT * FROM cached_responses ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<CachedResponse>>

    @Query("SELECT * FROM cached_responses ORDER BY timestampMs DESC")
    suspend fun getAll(): List<CachedResponse>

    @Query("SELECT COUNT(*) FROM cached_responses")
    suspend fun count(): Int

    @Query("SELECT SUM(sizeBytes) FROM cached_responses")
    suspend fun totalSizeBytes(): Long?

    /** Remove entries with lowest priority to free [targetBytes] of space */
    @Query("""
        DELETE FROM cached_responses WHERE key IN (
            SELECT key FROM cached_responses
            ORDER BY (accessCount * 0.6 + (timestampMs / 1000) * 0.4) ASC
            LIMIT :limit
        )
    """)
    suspend fun evictLeastValuable(limit: Int)

    @Query("DELETE FROM cached_responses WHERE timestampMs + ttlMs < :nowMs AND ttlMs > 0")
    suspend fun purgeExpired(nowMs: Long)

    @Query("DELETE FROM cached_responses WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_responses")
    suspend fun clearAll()

    @Query("SELECT * FROM cached_responses WHERE url LIKE '%' || :hostFragment || '%' ORDER BY accessCount DESC")
    suspend fun findByHost(hostFragment: String): List<CachedResponse>
}
