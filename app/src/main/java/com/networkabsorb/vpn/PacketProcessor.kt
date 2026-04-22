package com.networkabsorb.vpn

import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream

/**
 * Drains the VPN TUN fd to prevent kernel buffer overflow.
 * All HTTP/HTTPS interception is handled via the system proxy setting
 * (VpnService.Builder.setHttpProxy), so we don't need to parse raw packets.
 */
class TunDrainer(private val vpnFd: FileDescriptor) {
    companion object {
        private const val TAG = "TunDrainer"
    }

    @Volatile private var running = false
    private val inputStream = FileInputStream(vpnFd)

    fun run() {
        running = true
        val buffer = ByteArray(32768)
        Log.i(TAG, "TUN drainer started")
        while (running) {
            try {
                val n = inputStream.read(buffer)
                if (n <= 0) Thread.sleep(10)
                // Packets are discarded — HTTP/HTTPS is handled by system proxy
            } catch (_: Exception) {
                break
            }
        }
        Log.i(TAG, "TUN drainer stopped")
    }

    fun stop() {
        running = false
        try { inputStream.close() } catch (_: Exception) {}
    }
}
