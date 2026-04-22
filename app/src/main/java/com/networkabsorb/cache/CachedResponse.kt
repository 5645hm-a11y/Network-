package com.networkabsorb.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "cached_responses")
data class CachedResponse(
    @PrimaryKey val key: String = "",
    val url: String,
    val method: String,
    val statusCode: Int,
    val contentType: String,
    val headers: String,         // stored as "key::value\n..." — converted by CacheConverters
    val body: ByteArray,
    val timestampMs: Long,
    val ttlMs: Long,
    val accessCount: Int = 0,
    val sizeBytes: Int = body.size,
    val compressed: Boolean = false
) {
    fun isStale(): Boolean {
        if (ttlMs == 0L) return false
        return System.currentTimeMillis() > timestampMs + ttlMs
    }

    fun priorityScore(): Float {
        val freshnessRatio = if (ttlMs > 0)
            1f - ((System.currentTimeMillis() - timestampMs).toFloat() / ttlMs)
        else 1f
        val frequencyScore = minOf(accessCount / 10f, 1f)
        return (freshnessRatio * 0.4f) + (frequencyScore * 0.6f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedResponse) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

/** Separate converter class — registered only on CacheDatabase, not on the entity */
class CacheConverters {
    @TypeConverter
    fun fromHeaderMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}::${it.value}" }

    @TypeConverter
    fun toHeaderMap(raw: String): Map<String, String> =
        if (raw.isEmpty()) emptyMap()
        else raw.split("\n").associate {
            val idx = it.indexOf("::")
            if (idx < 0) it to ""
            else it.substring(0, idx) to it.substring(idx + 2)
        }
}
