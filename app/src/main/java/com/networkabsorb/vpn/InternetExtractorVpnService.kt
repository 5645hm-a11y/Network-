package com.networkabsorb.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.networkabsorb.R
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
 * Core VPN service that routes all device traffic through our local proxy.
 *
 * Architecture:
 *   Device apps → VPN tun0 interface → PacketProcessor → LocalHttpProxyServer
 *                                                               ↓
 *                                                    CacheEngine (ABSORB mode)
 *                                                    or CacheEngine (SERVE mode)
 *
 * Two operating modes:
 *   ABSORB – online, intercept + forward + cache everything
 *   SERVE  – offline, intercept + serve from cache (no real network needed)
 */
@AndroidEntryPoint
class InternetExtractorVpnService : VpnService() {

    companion object {
        private const val TAG = "VpnService"

        const val ACTION_START_ABSORB = "com.networkabsorb.action.START_ABSORB"
        const val ACTION_START_SERVE  = "com.networkabsorb.action.START_SERVE"
        const val ACTION_STOP         = "com.networkabsorb.action.STOP"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "network_absorb_channel"

        // Local proxy listens on this port inside the VPN namespace
        const val PROXY_PORT = 8118
        // VPN virtual interface address
        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_ROUTE   = "0.0.0.0"
        const val VPN_DNS     = "8.8.8.8"
    }

    @Inject lateinit var cacheEngine: CacheEngine
    @Inject lateinit var certificateManager: CertificateManager
    @Inject lateinit var trafficLogger: TrafficLogger

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyServer: LocalHttpProxyServer? = null
    private var packetProcessor: PacketProcessor? = null

    enum class Mode { ABSORB, SERVE }
    private var currentMode = Mode.ABSORB

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
            else -> currentMode = Mode.ABSORB
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

            // Start local HTTPS proxy first so the packet processor can forward to it
            proxyServer = LocalHttpProxyServer(
                port              = PROXY_PORT,
                cacheEngine       = cacheEngine,
                certificateManager = certificateManager,
                trafficLogger     = trafficLogger,
                mode              = currentMode
            )

            serviceScope.launch {
                proxyServer?.start()
            }

            // Packet processor reads raw IP packets from the VPN fd and
            // redirects TCP connections to our local proxy
            packetProcessor = PacketProcessor(
                vpnFd       = vpnInterface!!.fileDescriptor,
                proxyPort   = PROXY_PORT,
                vpnService  = this,
                scope       = serviceScope
            )

            serviceScope.launch {
                packetProcessor?.run()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")
        packetProcessor?.stop()
        proxyServer?.stop()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Builds the VPN interface using Android's VpnService.Builder.
     * Routes all IPv4 traffic through the tunnel.
     */
    private fun buildVpnInterface(): ParcelFileDescriptor? {
        return Builder()
            .setSession(getString(R.string.vpn_session_name))
            // Virtual interface gets this IP
            .addAddress(VPN_ADDRESS, 32)
            // Capture all traffic
            .addRoute(VPN_ROUTE, 0)
            // Use Google DNS (we will also cache DNS in future iterations)
            .addDnsServer(VPN_DNS)
            .addDnsServer("8.8.4.4")
            // MTU matching typical WiFi
            .setMtu(1500)
            // Blocking mode so we can read packets synchronously
            .setBlocking(true)
            // Allow our own app's traffic to bypass the VPN (avoid loops)
            .addDisallowedApplication(packageName)
            .establish()
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
