package com.networkabsorb.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import com.networkabsorb.ai.AiAbsorptionAgent
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.logger.TrafficEvent
import com.networkabsorb.logger.TrafficLogger
import com.networkabsorb.security.CertificateManager
import com.networkabsorb.vpn.InternetExtractorVpnService
import com.networkabsorb.vpn.InternetExtractorVpnService.Companion.EXTRA_QUOTA_MB
import dagger.hilt.android.AndroidEntryPoint
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FlutterFragmentActivity() {

    @Inject lateinit var cacheEngine: CacheEngine
    @Inject lateinit var trafficLogger: TrafficLogger
    @Inject lateinit var certificateManager: CertificateManager
    @Inject lateinit var aiAgent: AiAbsorptionAgent

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var vpnRunning = false
    private var vpnMode = "absorb"
    private var quotaMb = 500
    private var progressMb = 0f
    private var sessionRequests = 0
    private var sessionKb = 0f
    private var cacheHits = 0
    private var stateSink: EventChannel.EventSink? = null
    private var trafficSink: EventChannel.EventSink? = null

    private var pendingVpnResult: MethodChannel.Result? = null
    private var pendingVpnAction: String? = null
    private var pendingVpnQuota: Int = 500

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (pendingVpnAction) {
                "startAbsorb" -> {
                    launchVpnService(InternetExtractorVpnService.ACTION_START_ABSORB, pendingVpnQuota)
                    vpnRunning = true; vpnMode = "absorb"; quotaMb = pendingVpnQuota
                    progressMb = 0f; sessionRequests = 0; sessionKb = 0f; cacheHits = 0
                    pendingVpnResult?.success(null)
                    pushState()
                }
                "startServe" -> {
                    launchVpnService(InternetExtractorVpnService.ACTION_START_SERVE, null)
                    vpnRunning = true; vpnMode = "serve"
                    pendingVpnResult?.success(null)
                    pushState()
                }
            }
        } else {
            pendingVpnResult?.error("PERMISSION_DENIED", "VPN permission denied", null)
        }
        pendingVpnResult = null
        pendingVpnAction = null
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        setupControlChannel(messenger)
        setupSettingsChannel(messenger)
        setupStateEventChannel(messenger)
        setupTrafficEventChannel(messenger)

        scope.launch {
            trafficLogger.events.collect { event -> handleTrafficEvent(event) }
        }
        scope.launch {
            aiAgent.status.collect { pushState() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Channels ─────────────────────────────────────────────────────────────

    private fun setupControlChannel(messenger: io.flutter.plugin.common.BinaryMessenger) {
        MethodChannel(messenger, "com.networkabsorb/vpn_control")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startAbsorb" -> startVpn("startAbsorb", call.argument<Int>("quotaMb") ?: 500, result)
                    "startServe"  -> startVpn("startServe", 0, result)
                    "stop" -> {
                        stopVpnService()
                        vpnRunning = false
                        progressMb = 0f; sessionRequests = 0; sessionKb = 0f; cacheHits = 0
                        pushState(); result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun setupSettingsChannel(messenger: io.flutter.plugin.common.BinaryMessenger) {
        MethodChannel(messenger, "com.networkabsorb/settings")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setApiKey" -> { aiAgent.apiKey = call.argument<String>("key") ?: ""; result.success(null) }
                    "getApiKey" -> result.success(aiAgent.apiKey ?: "")
                    "getCacheStats" -> scope.launch {
                        result.success(mapOf(
                            "count" to cacheEngine.count(),
                            "sizeMb" to (cacheEngine.totalSizeBytes() / 1_048_576.0)
                        ))
                    }
                    "clearCache"    -> { scope.launch { cacheEngine.clearAll(); pushState() }; result.success(null) }
                    "purgeExpired"  -> { scope.launch { cacheEngine.purgeExpired(); pushState() }; result.success(null) }
                    "installCaCert" -> {
                        try {
                            val file = certificateManager.exportCaCertFile()
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                this, "${packageName}.provider", file)
                            startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/x-x509-ca-cert")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            result.success(null)
                        } catch (e: Exception) {
                            result.error("CERT_ERROR", e.message, null)
                        }
                    }
                    "getCacheEntries" -> scope.launch {
                        val entries = cacheEngine.getAll().map { e ->
                            mapOf("url" to e.url, "contentType" to e.contentType,
                                  "sizeBytes" to e.body.size, "statusCode" to e.statusCode,
                                  "timestampMs" to e.timestampMs)
                        }
                        result.success(entries)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun setupStateEventChannel(messenger: io.flutter.plugin.common.BinaryMessenger) {
        EventChannel(messenger, "com.networkabsorb/vpn_state")
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    stateSink = sink; pushState()
                }
                override fun onCancel(arguments: Any?) { stateSink = null }
            })
    }

    private fun setupTrafficEventChannel(messenger: io.flutter.plugin.common.BinaryMessenger) {
        EventChannel(messenger, "com.networkabsorb/traffic_feed")
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) { trafficSink = sink }
                override fun onCancel(arguments: Any?) { trafficSink = null }
            })
    }

    // ── VPN control ──────────────────────────────────────────────────────────

    private fun startVpn(action: String, quota: Int, result: MethodChannel.Result) {
        val prep = VpnService.prepare(this)
        if (prep == null) {
            when (action) {
                "startAbsorb" -> {
                    launchVpnService(InternetExtractorVpnService.ACTION_START_ABSORB, quota)
                    vpnRunning = true; vpnMode = "absorb"; quotaMb = quota
                    progressMb = 0f; sessionRequests = 0; sessionKb = 0f; cacheHits = 0
                }
                "startServe"  -> {
                    launchVpnService(InternetExtractorVpnService.ACTION_START_SERVE, null)
                    vpnRunning = true; vpnMode = "serve"
                }
            }
            pushState(); result.success(null)
        } else {
            pendingVpnResult = result
            pendingVpnAction = action
            pendingVpnQuota = quota
            vpnPermissionLauncher.launch(prep)
        }
    }

    private fun launchVpnService(action: String, quota: Int?) {
        startForegroundService(Intent(this, InternetExtractorVpnService::class.java).apply {
            this.action = action
            if (quota != null) putExtra(EXTRA_QUOTA_MB, quota)
        })
        scope.launch {
            if (action == InternetExtractorVpnService.ACTION_START_ABSORB) {
                aiAgent.planAbsorption(quota ?: 500)
            } else {
                val count = cacheEngine.count()
                val sizeMb = cacheEngine.totalSizeBytes() / (1024f * 1024f)
                aiAgent.describeInventory(sizeMb, count, emptyList())
            }
        }
    }

    private fun stopVpnService() {
        startService(Intent(this, InternetExtractorVpnService::class.java).apply {
            action = InternetExtractorVpnService.ACTION_STOP
        })
        aiAgent.setIdleMessage("הופסק. ${String.format("%.0f", progressMb)}MB שמורים.")
    }

    // ── State push ────────────────────────────────────────────────────────────

    private fun pushState() {
        val sink = stateSink ?: return
        scope.launch(Dispatchers.Main) {
            val count   = cacheEngine.count()
            val sizeMb  = cacheEngine.totalSizeBytes() / 1_048_576.0
            val agent   = aiAgent.status.value
            sink.success(mapOf(
                "running"        to vpnRunning,
                "mode"           to vpnMode,
                "quotaMb"        to quotaMb,
                "progressMb"     to progressMb.toDouble(),
                "sessionRequests" to sessionRequests,
                "sessionKb"      to sessionKb.toDouble(),
                "cacheHits"      to cacheHits,
                "cacheEntryCount" to count,
                "cacheSizeMb"    to sizeMb,
                "caCertReady"    to certificateManager.caExists(),
                "topDomains"     to emptyList<Any>(),
                "agentMessage"   to agent.message,
                "agentThinking"  to agent.thinking,
                "agentHasKey"    to agent.hasKey
            ))
        }
    }

    private fun handleTrafficEvent(event: TrafficEvent) {
        when (event) {
            is TrafficEvent.Response -> {
                sessionRequests++
                val kb = event.sizeBytes / 1024f
                sessionKb += kb
                if (vpnMode == "absorb") progressMb += kb / 1024f
                if (vpnRunning && vpnMode == "absorb" && progressMb >= quotaMb) {
                    stopVpnService(); vpnRunning = false; pushState(); return
                }
                trafficSink?.success(mapOf(
                    "type" to "response", "url" to event.url,
                    "statusCode" to event.statusCode, "sizeBytes" to event.sizeBytes))
                if (sessionRequests % 40 == 0)
                    scope.launch { aiAgent.reportProgress(progressMb, quotaMb, emptyList()) }
                pushState()
            }
            is TrafficEvent.CacheHit -> {
                cacheHits++
                trafficSink?.success(mapOf("type" to "hit", "url" to event.url))
                pushState()
            }
            is TrafficEvent.Error -> {
                trafficSink?.success(mapOf("type" to "miss", "url" to event.url))
            }
            else -> {}
        }
    }
}
