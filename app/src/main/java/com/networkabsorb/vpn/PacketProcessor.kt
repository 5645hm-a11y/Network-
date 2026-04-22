package com.networkabsorb.vpn

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Reads raw IP packets from the VPN tunnel file descriptor.
 *
 * For each TCP packet destined for port 80 or 443, it transparently redirects
 * the connection to our LocalHttpProxyServer running on localhost:proxyPort.
 *
 * Architecture decision: We use a transparent proxy redirect approach rather
 * than full userspace TCP stack (tun2socks style) to keep complexity manageable
 * for the PoC. A full tun2socks implementation would handle UDP/ICMP too.
 *
 * Packet flow:
 *   VPN fd → read raw IP packet → parse TCP header → extract dest IP:port
 *         → open connection to proxy → tell proxy the original destination
 *         → relay bytes bidirectionally
 */
class PacketProcessor(
    private val vpnFd: FileDescriptor,
    private val proxyPort: Int,
    private val vpnService: VpnService,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PacketProcessor"
        private const val BUFFER_SIZE = 65536

        // IP protocol numbers
        private const val PROTO_TCP = 6
        private const val PROTO_UDP = 17

        // HTTP/S ports we intercept
        private val INTERCEPT_PORTS = setOf(80, 443)
    }

    @Volatile private var running = false
    private val inputStream  = FileInputStream(vpnFd)
    private val outputStream = FileOutputStream(vpnFd)

    fun run() {
        running = true
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        Log.i(TAG, "PacketProcessor started")

        while (running) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) continue

                buffer.limit(length)
                processPacket(buffer)

            } catch (e: Exception) {
                if (running) Log.e(TAG, "Packet read error", e)
            }
        }

        Log.i(TAG, "PacketProcessor stopped")
    }

    fun stop() {
        running = false
        try { inputStream.close() } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // IP packet parsing
    // -------------------------------------------------------------------------

    private fun processPacket(buffer: ByteBuffer) {
        if (buffer.limit() < 20) return // Too small for IP header

        val firstByte = buffer.get(0).toInt() and 0xFF
        val version   = firstByte shr 4

        when (version) {
            4 -> processIPv4Packet(buffer)
            6 -> { /* IPv6 - future work */ }
        }
    }

    private fun processIPv4Packet(buffer: ByteBuffer) {
        val ihl      = (buffer.get(0).toInt() and 0x0F) * 4  // IP header length
        val protocol = buffer.get(9).toInt() and 0xFF

        if (protocol != PROTO_TCP) return  // Only handle TCP for now

        if (buffer.limit() < ihl + 20) return // Too small for TCP header

        // Destination IP (bytes 16-19)
        val destIp = buildString {
            for (i in 16..19) {
                if (i > 16) append('.')
                append(buffer.get(i).toInt() and 0xFF)
            }
        }

        // TCP destination port (bytes ihl+2, ihl+3)
        val destPort = ((buffer.get(ihl + 2).toInt() and 0xFF) shl 8) or
                       (buffer.get(ihl + 3).toInt() and 0xFF)

        if (destPort !in INTERCEPT_PORTS) return

        // Redirect this TCP flow to our local proxy
        scope.launch(Dispatchers.IO) {
            redirectToProxy(buffer.array(), buffer.limit(), destIp, destPort)
        }
    }

    /**
     * Opens a connection to the local proxy and passes original destination
     * so the proxy knows where to forward the request.
     *
     * The proxy receives:
     *   Line 1: "DEST <originalIp> <originalPort>\r\n"
     *   Then: raw TCP payload bytes
     */
    private suspend fun redirectToProxy(
        packet: ByteArray,
        length: Int,
        originalDest: String,
        originalPort: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val proxyChannel = SocketChannel.open()

            // Let this socket bypass the VPN tunnel (avoid routing loop)
            vpnService.protect(proxyChannel.socket())

            proxyChannel.connect(InetSocketAddress("127.0.0.1", proxyPort))

            // Send routing hint to proxy
            val header = "DEST $originalDest $originalPort\r\n".toByteArray()
            proxyChannel.write(ByteBuffer.wrap(header))

            // Send the TCP payload (skip IP+TCP headers)
            val ihl      = (packet[0].toInt() and 0x0F) * 4
            val tcpFlags = packet[ihl + 13].toInt() and 0xFF
            val dataOff  = ((packet[ihl + 12].toInt() and 0xFF) shr 4) * 4
            val payloadStart = ihl + dataOff

            if (payloadStart < length) {
                proxyChannel.write(
                    ByteBuffer.wrap(packet, payloadStart, length - payloadStart)
                )
            }

            // Relay response back into VPN tunnel
            val respBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            while (proxyChannel.read(respBuffer) > 0) {
                respBuffer.flip()
                outputStream.write(respBuffer.array(), 0, respBuffer.limit())
                respBuffer.clear()
            }

            proxyChannel.close()

        } catch (e: Exception) {
            Log.d(TAG, "Redirect error for $originalDest:$originalPort - ${e.message}")
        }
    }
}
