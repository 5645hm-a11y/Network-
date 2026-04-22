package com.networkabsorb.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.networkabsorb.R
import com.networkabsorb.ai.ProactiveAbsorber
import com.networkabsorb.cache.CacheEngine
import com.networkabsorb.logger.TrafficLogger
import com.networkabsorb.proxy.LocalHttpProxyServer
import com.networkabsorb.security.CertificateManager
import com.networkabsorb.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VPN service that routes HTTP/HTTPS traffic through our local proxy.
 *
 * Interception strategy:
 *   - VPN established so we can set a system-wide HTTP proxy (API 29+)
 *   - setHttpProxy() tells all apps to route HTTP/HTTPS through localhost:PROXY_PORT
 *   - LocalHttpProxyServer receives standard CONNECT/GET requests from apps
 *   - Our app is excluded from the VPN via addDisallowedApplication(), so the
 *     proxy's own upstream connections bypass the VPN (no routing loop)
 *   - TunDrainer keeps the TUN fd drained to avoid kernel buffer overflow
 */
@AndroidEntryPoint
class InternetExtractorVpnService : VpnService() {

    companion object {
        private const val TAG = "VpnService"

        const val ACTION_START_ABSORB = "com.networkabsorb.action.START_ABSORB"
        const val ACTION_START_SERVE  = "com.networkabsorb.action.START_SERVE"
        const val ACTION_STOP         = "com.networkabsorb.action.STOP"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID      = "network_absorb_channel"
        const val EXTRA_QUOTA_MB  = "quota_mb"

        const val PROXY_PORT  = 8118
        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_DNS     = "8.8.8.8"
    }

    @Inject lateinit var cacheEngine: CacheEngine
    @Inject lateinit var certificateManager: CertificateManager
    @Inject lateinit var trafficLogger: TrafficLogger
    @Inject lateinit var proactiveAbsorber: ProactiveAbsorber
    @Inject lateinit var dnsCache: DnsCache

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyServer: LocalHttpProxyServer?  = null
    private var tunForwarder: TunForwarder?         = null

    enum class Mode { ABSORB, SERVE }
    private var currentMode = Mode.ABSORB
    private var quotaMb     = 500

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START_SERVE -> currentMode = Mode.SERVE
            else               -> {
                currentMode = Mode.ABSORB
                quotaMb = intent?.getIntExtra(EXTRA_QUOTA_MB, 500) ?: 500
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by system/user")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // VPN setup
    // -------------------------------------------------------------------------

    private fun startVpn() {
        try {
            vpnInterface = buildVpnInterface() ?: run {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            Log.i(TAG, "VPN interface established in $currentMode mode")

            // Start the local HTTP/HTTPS proxy server
            proxyServer = LocalHttpProxyServer(
                port               = PROXY_PORT,
                cacheEngine        = cacheEngine,
                certificateManager = certificateManager,
                trafficLogger      = trafficLogger,
                mode               = currentMode
            )
            serviceScope.launch { proxyServer?.start() }

            // Forward DNS packets so name resolution keeps working.
            // HTTP/HTTPS is handled via setHttpProxy() — never reaches TUN.
            tunForwarder = TunForwarder(
                vpnFd      = vpnInterface!!.fileDescriptor,
                vpnService = this@InternetExtractorVpnService,
                mode       = currentMode,
                dnsCache   = dnsCache
            )
            serviceScope.launch(Dispatchers.IO) { tunForwarder?.run() }

            // In ABSORB mode: proactively download predicted content via the proxy
            if (currentMode == Mode.ABSORB) {
                proactiveAbsorber.start(serviceScope, quotaMb)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")
        proactiveAbsorber.stop()
        tunForwarder?.stop()
        proxyServer?.stop()
        vpnInterface?.close()
        vpnInterface  = null
        tunForwarder  = null
        proxyServer   = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildVpnInterface(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .addAddress(VPN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)          // capture all IPv4 traffic
            .addDnsServer(VPN_DNS)
            .addDnsServer("8.8.4.4")
            .setMtu(1500)
            .setBlocking(true)
            // Our own app's sockets bypass the VPN — no routing loop for proxy upstream
            .addDisallowedApplication(packageName)

        // Tell every app on this device to use our local HTTP proxy (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy("127.0.0.1", PROXY_PORT)
            )
        }

        return builder.establish()
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        ensureNotificationChannel()

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, InternetExtractorVpnService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (currentMode == Mode.ABSORB)
            getString(R.string.notification_absorbing)
        else
            getString(R.string.notification_serving)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows status of Network Absorb VPN"
            manager.createNotificationChannel(channel)
        }
    }
}
