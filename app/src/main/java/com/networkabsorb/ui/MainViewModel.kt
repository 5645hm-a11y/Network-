package com.networkabsorb.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.networkabsorb.ai.AgentStatus
import com.networkabsorb.ai.AiAbsorptionAgent
import com.networkabsorb.ai.PredictionEngine
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.logger.TrafficEvent
import com.networkabsorb.logger.TrafficLogger
import com.networkabsorb.security.CertificateManager
import com.networkabsorb.vpn.InternetExtractorVpnService
import com.networkabsorb.vpn.InternetExtractorVpnService.Companion.EXTRA_QUOTA_MB
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
    val vpnRunning: Boolean = false,
    val mode: InternetExtractorVpnService.Mode = InternetExtractorVpnService.Mode.ABSORB,
    val quotaMb: Int = 500,
    val absorptionProgressMb: Float = 0f,
    val sessionRequests: Int = 0,
    val sessionBytesKb: Float = 0f,
    val sessionCacheHits: Int = 0,
    val cacheEntryCount: Int = 0,
    val cacheSizeMb: Float = 0f,
    val caCertReady: Boolean = false,
    val topDomains: List<Pair<String, Int>> = emptyList(),
    val recentTraffic: List<TrafficEvent> = emptyList(),
    val agentStatus: AgentStatus = AgentStatus(),
    val errorMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val cacheEngine: CacheEngine,
    private val trafficLogger: TrafficLogger,
    private val certificateManager: CertificateManager,
    private val predictionEngine: PredictionEngine,
    private val aiAgent: AiAbsorptionAgent
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val cachedEntries = cacheEngine.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trafficFeed = trafficLogger.events

    init {
        viewModelScope.launch { refreshCacheStats() }
        viewModelScope.launch { collectTrafficEvents() }
        viewModelScope.launch { collectAgentStatus() }
        _uiState.update { it.copy(caCertReady = certificateManager.caExists()) }
    }

    fun prepareVpn(): Intent? = VpnService.prepare(getApplication())

    fun startAbsorbing() {
        val app = getApplication<Application>()
        app.startForegroundService(
            Intent(app, InternetExtractorVpnService::class.java).apply {
                action = InternetExtractorVpnService.ACTION_START_ABSORB
                putExtra(EXTRA_QUOTA_MB, _uiState.value.quotaMb)
            }
        )
        _uiState.update {
            it.copy(vpnRunning = true, mode = InternetExtractorVpnService.Mode.ABSORB,
                    sessionRequests = 0, sessionBytesKb = 0f,
                    sessionCacheHits = 0, absorptionProgressMb = 0f, topDomains = emptyList())
        }
        viewModelScope.launch { aiAgent.planAbsorption(_uiState.value.quotaMb) }
    }

    fun startServing() {
        val app = getApplication<Application>()
        app.startForegroundService(
            Intent(app, InternetExtractorVpnService::class.java).apply {
                action = InternetExtractorVpnService.ACTION_START_SERVE
            }
        )
        _uiState.update {
            it.copy(vpnRunning = true, mode = InternetExtractorVpnService.Mode.SERVE,
                    sessionRequests = 0, sessionBytesKb = 0f, sessionCacheHits = 0)
        }
        viewModelScope.launch {
            val s = _uiState.value
            aiAgent.describeInventory(s.cacheSizeMb, s.cacheEntryCount, s.topDomains.map { it.first })
        }
    }

    fun stopVpn() {
        val app = getApplication<Application>()
        app.startService(Intent(app, InternetExtractorVpnService::class.java).apply {
            action = InternetExtractorVpnService.ACTION_STOP
        })
        _uiState.update { it.copy(vpnRunning = false) }
        aiAgent.setIdleMessage("הופסק. ${"%.0f".format(_uiState.value.cacheSizeMb)}MB שמורים.")
    }

    fun setQuota(mb: Int) { _uiState.update { it.copy(quotaMb = mb) } }

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

    fun onCaCertInstalled() { _uiState.update { it.copy(caCertReady = true) } }
    fun clearCache() { viewModelScope.launch { cacheEngine.clearAll(); refreshCacheStats() } }
    fun purgeExpired() { viewModelScope.launch { cacheEngine.purgeExpired(); refreshCacheStats() } }
    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }
    fun setAiApiKey(key: String) { aiAgent.apiKey = key }
    fun getAiApiKey(): String = aiAgent.apiKey ?: ""

    private suspend fun refreshCacheStats() {
        val count = cacheEngine.count()
        val bytes = cacheEngine.totalSizeBytes()
        _uiState.update { it.copy(cacheEntryCount = count, cacheSizeMb = bytes / (1024f * 1024f)) }
    }

    private suspend fun collectAgentStatus() {
        aiAgent.status.collect { s -> _uiState.update { it.copy(agentStatus = s) } }
    }

    private suspend fun collectTrafficEvents() {
        trafficLogger.events.collect { event ->
            _uiState.update { state ->
                val traffic = (listOf(event) + state.recentTraffic).take(100)
                when (event) {
                    is TrafficEvent.Response -> {
                        val addedKb   = event.sizeBytes / 1024f
                        val newProgMb = state.absorptionProgressMb + addedKb / 1024f
                        val domains   = state.topDomains.toMutableList()
                        val idx = domains.indexOfFirst { it.first == event.host }
                        if (idx >= 0) domains[idx] = event.host to domains[idx].second + 1
                        else domains.add(0, event.host to 1)
                        val sorted = domains.sortedByDescending { it.second }.take(8)

                        if (state.mode == InternetExtractorVpnService.Mode.ABSORB &&
                            newProgMb >= state.quotaMb) {
                            viewModelScope.launch { stopVpn() }
                        }
                        if (state.mode == InternetExtractorVpnService.Mode.ABSORB &&
                            state.sessionRequests > 0 && state.sessionRequests % 40 == 0) {
                            viewModelScope.launch {
                                aiAgent.reportProgress(newProgMb, state.quotaMb, sorted.map { it.first })
                            }
                        }
                        state.copy(recentTraffic = traffic,
                                   sessionRequests = state.sessionRequests + 1,
                                   sessionBytesKb  = state.sessionBytesKb + addedKb,
                                   absorptionProgressMb = newProgMb.coerceAtMost(state.quotaMb.toFloat()),
                                   topDomains = sorted)
                    }
                    is TrafficEvent.CacheHit ->
                        state.copy(recentTraffic = traffic, sessionCacheHits = state.sessionCacheHits + 1)
                    else -> state.copy(recentTraffic = traffic)
                }
            }
            if (event is TrafficEvent.Response) refreshCacheStats()
        }
    }
}
