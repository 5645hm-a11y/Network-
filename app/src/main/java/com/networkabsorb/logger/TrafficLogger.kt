package com.networkabsorb.logger

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight traffic event logger.
 *
 * Emits [TrafficEvent] to a SharedFlow that the UI observes in real time.
 * Also writes to Logcat (debug builds only).
 *
 * Keeps a bounded in-memory ring buffer of the last [BUFFER_SIZE] events
 * so the UI can display recent traffic without a database query.
 */
@Singleton
class TrafficLogger @Inject constructor() {

    companion object {
        private const val TAG         = "TrafficLogger"
        private const val BUFFER_SIZE = 500
    }

    private val _events = MutableSharedFlow<TrafficEvent>(replay = BUFFER_SIZE)
    val events: SharedFlow<TrafficEvent> = _events.asSharedFlow()

    fun logRequest(method: String, url: String, headers: Map<String, String> = emptyMap()) {
        val event = TrafficEvent.Request(
            method    = method,
            url       = url,
            host      = extractHost(url),
            headers   = headers,
            timestamp = System.currentTimeMillis()
        )
        _events.tryEmit(event)
        Log.d(TAG, "→ $method $url")
    }

    fun logResponse(url: String, statusCode: Int, bodySize: Int) {
        val event = TrafficEvent.Response(
            url        = url,
            host       = extractHost(url),
            statusCode = statusCode,
            sizeBytes  = bodySize,
            timestamp  = System.currentTimeMillis()
        )
        _events.tryEmit(event)
        Log.d(TAG, "← $statusCode $url (${bodySize}B)")
    }

    fun logCacheHit(url: String) {
        val event = TrafficEvent.CacheHit(
            url       = url,
            host      = extractHost(url),
            timestamp = System.currentTimeMillis()
        )
        _events.tryEmit(event)
        Log.d(TAG, "⚡ CACHE HIT: $url")
    }

    fun logError(url: String, error: String) {
        val event = TrafficEvent.Error(
            url       = url,
            host      = extractHost(url),
            message   = error,
            timestamp = System.currentTimeMillis()
        )
        _events.tryEmit(event)
        Log.w(TAG, "✗ ERROR $url: $error")
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (_: Exception) {
            url.substringAfter("://").substringBefore("/").substringBefore(":")
        }
    }
}

sealed class TrafficEvent {
    abstract val url: String
    abstract val host: String
    abstract val timestamp: Long

    data class Request(
        override val url: String,
        override val host: String,
        override val timestamp: Long,
        val method: String,
        val headers: Map<String, String>
    ) : TrafficEvent()

    data class Response(
        override val url: String,
        override val host: String,
        override val timestamp: Long,
        val statusCode: Int,
        val sizeBytes: Int
    ) : TrafficEvent()

    data class CacheHit(
        override val url: String,
        override val host: String,
        override val timestamp: Long
    ) : TrafficEvent()

    data class Error(
        override val url: String,
        override val host: String,
        override val timestamp: Long,
        val message: String
    ) : TrafficEvent()
}
