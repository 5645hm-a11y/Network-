package com.networkabsorb.vpn

import android.net.VpnService
import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Reads raw IPv4 packets from the VPN TUN fd.
 *
 * DNS (UDP port 53): forwarded to the real DNS server via a protected DatagramSocket
 * so name resolution keeps working while the VPN is active.
 * The DNS response is packed back into a proper IP/UDP packet and written to TUN.
 *
 * All other packets: discarded.
 * HTTP/HTTPS is handled at the application layer via setHttpProxy() — those
 * connections go through loopback to our LocalHttpProxyServer and never appear here.
 */
class TunForwarder(
    private val vpnFd: FileDescriptor,
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG            = "TunForwarder"
        private const val PROTO_UDP      = 17
        private const val DNS_PORT       = 53
        private const val DNS_TIMEOUT_MS = 3_000
    }

    @Volatile private var running = false
    private val input  = FileInputStream(vpnFd)
    private val output = FileOutputStream(vpnFd)

    fun run() {
        running = true
        val buf = ByteArray(65535)
        Log.i(TAG, "TUN forwarder started")
        while (running) {
            try {
                val len = input.read(buf)
                if (len >= 20) processPacket(buf, len)
            } catch (_: Exception) {
                break
            }
        }
        Log.i(TAG, "TUN forwarder stopped")
    }

    fun stop() {
        running = false
        try { input.close() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // Packet dispatch
    // ─────────────────────────────────────────────────────────────

    private fun processPacket(buf: ByteArray, len: Int) {
        val version = (buf[0].toInt() and 0xFF) ushr 4
        if (version != 4) return                      // ignore IPv6

        val ihl   = (buf[0].toInt() and 0x0F) * 4
        val proto = buf[9].toInt() and 0xFF
        if (proto != PROTO_UDP) return                // only handle UDP

        if (len < ihl + 8) return
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or
                      (buf[ihl + 3].toInt() and 0xFF)
        if (dstPort == DNS_PORT) forwardDns(buf, ihl, len)
        // all other UDP discarded — QUIC, etc. make browsers fall back to TCP/proxy
    }

    // ─────────────────────────────────────────────────────────────
    // DNS forwarding
    // ─────────────────────────────────────────────────────────────

    private fun forwardDns(buf: ByteArray, ihl: Int, totalLen: Int) {
        val srcIp   = buf.copyOfRange(12, 16)
        val dstIp   = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl + 0].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)

        val payloadStart = ihl + 8
        val payloadLen   = totalLen - payloadStart
        if (payloadLen <= 0) return

        val query = buf.copyOfRange(payloadStart, payloadStart + payloadLen)

        try {
            val sock = DatagramSocket()
            vpnService.protect(sock)               // bypass VPN loop
            sock.soTimeout = DNS_TIMEOUT_MS

            sock.send(DatagramPacket(query, query.size, InetAddress.getByAddress(dstIp), dstPort))

            val respBuf = ByteArray(512)
            val respPkt = DatagramPacket(respBuf, respBuf.size)
            sock.receive(respPkt)
            sock.close()

            // Write the DNS reply back into the TUN as a proper IP/UDP packet
            writeUdpToTun(
                srcIp   = dstIp,  srcPort = dstPort,   // "from" DNS server
                dstIp   = srcIp,  dstPort = srcPort,   // "to" original sender
                payload = respBuf.copyOf(respPkt.length)
            )
        } catch (e: Exception) {
            Log.d(TAG, "DNS forward failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // IP/UDP packet construction
    // ─────────────────────────────────────────────────────────────

    private fun writeUdpToTun(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ) {
        val udpLen   = 8 + payload.size
        val totalLen = 20 + udpLen
        val pkt      = ByteArray(totalLen)

        // IPv4 header
        pkt[0]  = 0x45.toByte()                     // ver=4, ihl=5 words
        pkt[1]  = 0                                 // DSCP/ECN
        pkt[2]  = (totalLen ushr 8).toByte()
        pkt[3]  = (totalLen and 0xFF).toByte()
        pkt[4]  = 0; pkt[5] = 0                     // Identification
        pkt[6]  = 0; pkt[7] = 0                     // Flags / Fragment offset
        pkt[8]  = 64                                // TTL
        pkt[9]  = PROTO_UDP.toByte()                // Protocol
        pkt[10] = 0; pkt[11] = 0                    // Checksum (computed below)
        srcIp.copyInto(pkt, 12)
        dstIp.copyInto(pkt, 16)
        val cksum = ipChecksum(pkt, 0, 20)
        pkt[10] = (cksum ushr 8).toByte()
        pkt[11] = (cksum and 0xFF).toByte()

        // UDP header
        pkt[20] = (srcPort ushr 8).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort ushr 8).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = (udpLen ushr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0; pkt[27] = 0                    // UDP checksum (RFC 768: optional)

        payload.copyInto(pkt, 28)
        output.write(pkt, 0, totalLen)
        output.flush()
    }

    /** One's complement sum over a 20-byte IPv4 header. */
    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((len and 1) == 1) sum += (buf[off + len - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}
