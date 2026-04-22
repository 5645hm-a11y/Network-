package com.networkabsorb.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.networkabsorb.ai.PredictionEngine
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.cache.CachedResponse
import com.networkabsorb.logger.TrafficEvent
import com.networkabsorb.logger.TrafficLogger
import com.networkabsorb.security.CertificateManager
import com.networkabsorb.vpn.InternetExtractorVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UiState(
    val vpnRunning: Boolean        = false,
    val mode: InternetExtractorVpnService.Mode = InternetExtractorVpnService.Mode.ABSORB,
    val cacheEntryCount: Int       = 0,
    val cacheSizeMb: Float         = 0f,
    val caInstalled: Boolean       = false,
    val recentTraffic: List<TrafficEvent> = emptyList(),
    val aiInsights: Map<String, Any> = emptyMap(),
    val exportedCaFile: File?      = null,
    val errorMessage: String?      = null,
    // Live session counters (reset when VPN starts)
    val sessionRequests: Int       = 0,
    val sessionBytesKb: Float      = 0f
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val cacheEngine: CacheEngine,
    private val trafficLogger: TrafficLogger,
    private val certificateManager: CertificateManager,
    private val predictionEngine: PredictionEngine
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Live cache list for the cache browser screen
    val cachedEntries = cacheEngine.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live traffic feed from logger
    val trafficFeed = trafficLogger.events

    init {
        viewModelScope.launch { refreshCacheStats() }
        viewModelScope.launch { collectTrafficEvents() }
    }

    // -------------------------------------------------------------------------
    // VPN control
    // -------------------------------------------------------------------------

    /** Returns VpnService.prepare() intent if VPN permission needed, else null. */
    fun prepareVpn(): Intent? = VpnService.prepare(getApplication())

    fun startAbsorbing() {
        val app = getApplication<Application>()
        val intent = Intent(app, InternetExtractorVpnService::class.java).apply {
            action = InternetExtractorVpnService.ACTION_START_ABSORB
        }
        app.startForegroundService(intent)
        _uiState.update { it.copy(
            vpnRunning = true,
            mode = InternetExtractorVpnService.Mode.ABSORB,
            sessionRequests = 0,
            sessionBytesKb = 0f
        ) }
    }

    fun startServing() {
        val app = getApplication<Application>()
        val intent = Intent(app, InternetExtractorVpnService::class.java).apply {
            action = InternetExtractorVpnService.ACTION_START_SERVE
        }
        app.startForegroundService(intent)
        _uiState.update { it.copy(
            vpnRunning = true,
            mode = InternetExtractorVpnService.Mode.SERVE,
            sessionRequests = 0,
            sessionBytesKb = 0f
        ) }
    }

    fun stopVpn() {
        val app = getApplication<Application>()
        val intent = Intent(app, InternetExtractorVpnService::class.java).apply {
            action = InternetExtractorVpnService.ACTION_STOP
        }
        app.startService(intent)
        _uiState.update { it.copy(vpnRunning = false) }
    }

    // -------------------------------------------------------------------------
    // Certificate management
    // -------------------------------------------------------------------------

    fun exportCaCert() {
        viewModelScope.launch {
            val file = certificateManager.exportCaCertFile()
            _uiState.update { it.copy(exportedCaFile = file) }
        }
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    fun clearCache() {
        viewModelScope.launch {
            cacheEngine.clearAll()
            refreshCacheStats()
        }
    }

    fun purgeExpired() {
        viewModelScope.launch {
            cacheEngine.purgeExpired()
            refreshCacheStats()
        }
    }

    fun refreshAiInsights() {
        _uiState.update { it.copy(aiInsights = predictionEngine.getInsightsReport()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun refreshCacheStats() {
        val count = cacheEngine.count()
        val bytes = cacheEngine.totalSizeBytes()
        _uiState.update {
            it.copy(
                cacheEntryCount = count,
                cacheSizeMb     = bytes / (1024f * 1024f)
            )
        }
    }

    private suspend fun collectTrafficEvents() {
        trafficLogger.events.collect { event ->
            _uiState.update { state ->
                val updated = (listOf(event) + state.recentTraffic).take(100)
                val withSession = if (event is TrafficEvent.Response)
                    state.copy(
                        recentTraffic  = updated,
                        sessionRequests = state.sessionRequests + 1,
                        sessionBytesKb  = state.sessionBytesKb + event.sizeBytes / 1024f
                    )
                else
                    state.copy(recentTraffic = updated)
                withSession
            }
            if (event is TrafficEvent.Response) refreshCacheStats()
        }
    }
}
