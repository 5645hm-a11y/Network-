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
 * DNS (UDP port 53):
 *   ABSORB mode — forwarded to the real DNS server via a protected DatagramSocket.
 *                 The A-record response is parsed and stored in [dnsCache] for later
 *                 offline use. The reply is also written back into TUN.
 *   SERVE mode  — answered directly from [dnsCache] without internet access.
 *                 The phone works normally abroad even with no connectivity.
 *
 * All other packets: discarded.
 * HTTP/HTTPS is handled via setHttpProxy() → LocalHttpProxyServer — never reaches TUN.
 */
class TunForwarder(
    private val vpnFd: FileDescriptor,
    private val vpnService: VpnService,
    private val mode: InternetExtractorVpnService.Mode,
    private val dnsCache: DnsCache
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
        Log.i(TAG, "TUN forwarder started (mode=$mode, cached=${dnsCache.count()} domains)")
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
        if (version != 4) return

        val ihl   = (buf[0].toInt() and 0x0F) * 4
        val proto = buf[9].toInt() and 0xFF
        if (proto != PROTO_UDP) return

        if (len < ihl + 8) return
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        if (dstPort == DNS_PORT) handleDns(buf, ihl, len)
    }

    // ─────────────────────────────────────────────────────────────
    // DNS handling — mode-aware
    // ─────────────────────────────────────────────────────────────

    private fun handleDns(buf: ByteArray, ihl: Int, totalLen: Int) {
        val srcIp   = buf.copyOfRange(12, 16)
        val dstIp   = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)

        val payloadOff = ihl + 8
        val payloadLen = totalLen - payloadOff
        if (payloadLen < 13) return

        val query = buf.copyOfRange(payloadOff, payloadOff + payloadLen)

        if (mode == InternetExtractorVpnService.Mode.SERVE) {
            serveFromCache(query, srcIp, srcPort, dstIp, dstPort)
        } else {
            forwardAndLearn(query, srcIp, srcPort, dstIp, dstPort)
        }
    }

    /** ABSORB mode: forward DNS to real server, learn & cache domain→IP, relay reply to TUN. */
    private fun forwardAndLearn(
        query: ByteArray,
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int
    ) {
        try {
            val sock = DatagramSocket()
            vpnService.protect(sock)
            sock.soTimeout = DNS_TIMEOUT_MS
            sock.send(DatagramPacket(query, query.size, InetAddress.getByAddress(dstIp), dstPort))

            val respBuf = ByteArray(512)
            val respPkt = DatagramPacket(respBuf, respBuf.size)
            sock.receive(respPkt)
            sock.close()

            val response = respBuf.copyOf(respPkt.length)

            // Learn domain→IP from A records in the response
            learnFromResponse(response)

            writeUdpToTun(
                srcIp = dstIp, srcPort = dstPort,
                dstIp = srcIp, dstPort = srcPort,
                payload = response
            )
        } catch (e: Exception) {
            Log.d(TAG, "DNS forward failed: ${e.message}")
        }
    }

    /** SERVE mode: answer DNS query from local cache, no internet needed. */
    private fun serveFromCache(
        query: ByteArray,
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int
    ) {
        val domain = parseDnsQueryName(query) ?: return

        val ip = dnsCache.get(domain)
        if (ip == null) {
            Log.d(TAG, "DNS cache miss (offline): $domain")
            return  // drop — app will show "no connection" for unknown hosts
        }

        Log.d(TAG, "DNS cache hit (offline): $domain → ${ip.joinToString(".")}")
        val response = buildDnsAResponse(query, ip)
        writeUdpToTun(
            srcIp = dstIp, srcPort = dstPort,
            dstIp = srcIp, dstPort = srcPort,
            payload = response
        )
    }

    // ─────────────────────────────────────────────────────────────
    // DNS parsing helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the QNAME from a DNS query payload (first question).
     * Wire format: sequence of (length, chars) pairs ending with 0x00.
     */
    private fun parseDnsQueryName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val sb = StringBuilder()
        var i = 12  // skip DNS header
        try {
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dns, i + 1, len, Charsets.US_ASCII))
                i += 1 + len
            }
        } catch (_: Exception) { return null }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /**
     * Extracts the first A-record IP from a DNS response and stores it in [dnsCache].
     * Handles both label and pointer (0xC0xx) name encoding.
     */
    private fun learnFromResponse(dns: ByteArray) {
        if (dns.size < 12) return
        val anCount = ((dns[6].toInt() and 0xFF) shl 8) or (dns[7].toInt() and 0xFF)
        if (anCount == 0) return

        val domain = parseDnsQueryName(dns) ?: return

        // Skip question section to reach answers
        var i = 12
        val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        repeat(qdCount) {
            // Skip QNAME labels
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) { i++; break }
                if (len and 0xC0 == 0xC0) { i += 2; break }
                i += 1 + len
            }
            i += 4  // QTYPE + QCLASS
        }

        // Parse answer records
        repeat(anCount) {
            if (i >= dns.size) return

            // Skip name (pointer or labels)
            if ((dns[i].toInt() and 0xFF) and 0xC0 == 0xC0) {
                i += 2
            } else {
                while (i < dns.size) {
                    val len = dns[i].toInt() and 0xFF
                    if (len == 0) { i++; break }
                    i += 1 + len
                }
            }

            if (i + 10 > dns.size) return
            val rrType    = ((dns[i].toInt() and 0xFF) shl 8) or (dns[i + 1].toInt() and 0xFF)
            val rrClass   = ((dns[i + 2].toInt() and 0xFF) shl 8) or (dns[i + 3].toInt() and 0xFF)
            val rdLength  = ((dns[i + 8].toInt() and 0xFF) shl 8) or (dns[i + 9].toInt() and 0xFF)
            i += 10

            if (rrType == 1 && rrClass == 1 && rdLength == 4 && i + 4 <= dns.size) {
                val ip = dns.copyOfRange(i, i + 4)
                dnsCache.put(domain, ip)
            }

            i += rdLength
        }
    }

    /**
     * Builds a minimal DNS A-record response for [ip] matching the given [query].
     *
     *   Header: QR=1 AA=0 TC=0 RD=1 RA=1, QDCOUNT=1, ANCOUNT=1
     *   Question section: copied from query
     *   Answer: pointer → QNAME, type A, class IN, TTL 1h, 4-byte IP
     */
    private fun buildDnsAResponse(query: ByteArray, ip: ByteArray): ByteArray {
        // Find where the question section ends (QNAME + QTYPE + QCLASS)
        var qEnd = 12
        while (qEnd < query.size) {
            val len = query[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd++; break }
            qEnd += 1 + len
        }
        qEnd += 4  // QTYPE (2) + QCLASS (2)

        val questionSection = query.copyOfRange(12, qEnd)
        val response = ByteArray(12 + questionSection.size + 16)

        // Header
        response[0] = query[0]; response[1] = query[1]  // transaction ID
        response[2] = 0x81.toByte(); response[3] = 0x80.toByte()  // flags: response + RA
        response[4] = 0; response[5] = 1   // QDCOUNT = 1
        response[6] = 0; response[7] = 1   // ANCOUNT = 1
        response[8] = 0; response[9] = 0   // NSCOUNT = 0
        response[10] = 0; response[11] = 0 // ARCOUNT = 0

        // Question section
        questionSection.copyInto(response, 12)

        // Answer section
        val ansOff = 12 + questionSection.size
        response[ansOff]     = 0xC0.toByte()  // pointer
        response[ansOff + 1] = 0x0C           // to offset 12 (QNAME)
        response[ansOff + 2] = 0; response[ansOff + 3] = 1    // type A
        response[ansOff + 4] = 0; response[ansOff + 5] = 1    // class IN
        response[ansOff + 6] = 0; response[ansOff + 7] = 0    // TTL high
        response[ansOff + 8] = 0x0E.toByte(); response[ansOff + 9] = 0x10.toByte()  // TTL = 3600
        response[ansOff + 10] = 0; response[ansOff + 11] = 4  // RDLENGTH = 4
        ip.copyInto(response, ansOff + 12)

        return response
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
        pkt[0]  = 0x45.toByte()
        pkt[1]  = 0
        pkt[2]  = (totalLen ushr 8).toByte()
        pkt[3]  = (totalLen and 0xFF).toByte()
        pkt[4]  = 0; pkt[5] = 0
        pkt[6]  = 0; pkt[7] = 0
        pkt[8]  = 64
        pkt[9]  = PROTO_UDP.toByte()
        pkt[10] = 0; pkt[11] = 0
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
        pkt[26] = 0; pkt[27] = 0

        payload.copyInto(pkt, 28)
        output.write(pkt, 0, totalLen)
        output.flush()
    }

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
