package com.networkabsorb.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.core.content.FileProvider
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
import javax.inject.Inject

data class UiState(
    // VPN / mode
    val vpnRunning: Boolean = false,
    val mode: InternetExtractorVpnService.Mode = InternetExtractorVpnService.Mode.ABSORB,

    // Absorption quota (chosen by user before starting)
    val quotaMb: Int = 500,           // target MB to absorb
    val absorptionProgressMb: Float = 0f,

    // Live session counters (reset on each start)
    val sessionRequests: Int = 0,
    val sessionBytesKb: Float = 0f,
    val sessionCacheHits: Int = 0,

    // Total cache stats
    val cacheEntryCount: Int = 0,
    val cacheSizeMb: Float = 0f,

    // CA certificate
    val caCertReady: Boolean = false,

    // Top absorbed domains this session
    val topDomains: List<Pair<String, Int>> = emptyList(),  // domain → request count

    // Recent traffic for Traffic Monitor screen
    val recentTraffic: List<TrafficEvent> = emptyList(),

    val errorMessage: String? = null
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

    val cachedEntries = cacheEngine.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trafficFeed = trafficLogger.events

    init {
        viewModelScope.launch { refreshCacheStats() }
        viewModelScope.launch { collectTrafficEvents() }
        _uiState.update { it.copy(caCertReady = certificateManager.caExists()) }
    }

    // ─── VPN control ─────────────────────────────────────────────────────────

    fun prepareVpn(): Intent? = VpnService.prepare(getApplication())

    fun startAbsorbing() {
        val app = getApplication<Application>()
        app.startForegroundService(
            Intent(app, InternetExtractorVpnService::class.java).apply {
                action = InternetExtractorVpnService.ACTION_START_ABSORB
            }
        )
        _uiState.update { it.copy(
            vpnRunning = true,
            mode = InternetExtractorVpnService.Mode.ABSORB,
            sessionRequests = 0,
            sessionBytesKb = 0f,
            sessionCacheHits = 0,
            absorptionProgressMb = 0f,
            topDomains = emptyList()
        ) }
    }

    fun startServing() {
        val app = getApplication<Application>()
        app.startForegroundService(
            Intent(app, InternetExtractorVpnService::class.java).apply {
                action = InternetExtractorVpnService.ACTION_START_SERVE
            }
        )
        _uiState.update { it.copy(
            vpnRunning = true,
            mode = InternetExtractorVpnService.Mode.SERVE,
            sessionRequests = 0,
            sessionBytesKb = 0f,
            sessionCacheHits = 0
        ) }
    }

    fun stopVpn() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, InternetExtractorVpnService::class.java).apply {
                action = InternetExtractorVpnService.ACTION_STOP
            }
        )
        _uiState.update { it.copy(vpnRunning = false) }
    }

    fun setQuota(mb: Int) {
        _uiState.update { it.copy(quotaMb = mb) }
    }

    // ─── CA certificate ───────────────────────────────────────────────────────

    /** Exports the CA cert and returns an Intent the UI can launch to install it. */
    fun buildCaInstallIntent(): Intent {
        val file = certificateManager.exportCaCertFile()
        val app  = getApplication<Application>()
        val uri  = FileProvider.getUriForFile(app, "${app.packageName}.provider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-x509-ca-cert")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun onCaCertInstalled() {
        _uiState.update { it.copy(caCertReady = true) }
    }

    // ─── Cache management ─────────────────────────────────────────────────────

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

    fun refreshAiInsights() { /* PredictionEngine.getInsightsReport() available if needed */ }

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }

    // ─── Private helpers ──────────────────────────────────────────────────────

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
                val traffic = (listOf(event) + state.recentTraffic).take(100)

                when (event) {
                    is TrafficEvent.Response -> {
                        val addedKb = event.sizeBytes / 1024f
                        val newProgressMb = state.absorptionProgressMb + addedKb / 1024f

                        // Update per-domain counter
                        val domains = state.topDomains.toMutableList()
                        val idx = domains.indexOfFirst { it.first == event.host }
                        if (idx >= 0) domains[idx] = event.host to domains[idx].second + 1
                        else domains.add(0, event.host to 1)
                        val sorted = domains.sortedByDescending { it.second }.take(8)

                        // Auto-stop if quota reached
                        if (state.mode == InternetExtractorVpnService.Mode.ABSORB &&
                            newProgressMb >= state.quotaMb) {
                            viewModelScope.launch { stopVpn() }
                        }

                        state.copy(
                            recentTraffic        = traffic,
                            sessionRequests       = state.sessionRequests + 1,
                            sessionBytesKb        = state.sessionBytesKb + addedKb,
                            absorptionProgressMb  = newProgressMb.coerceAtMost(state.quotaMb.toFloat()),
                            topDomains            = sorted
                        )
                    }
                    is TrafficEvent.CacheHit ->
                        state.copy(recentTraffic = traffic, sessionCacheHits = state.sessionCacheHits + 1)
                    else ->
                        state.copy(recentTraffic = traffic)
                }
            }
            if (event is TrafficEvent.Response) refreshCacheStats()
        }
    }
}
