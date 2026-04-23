package com.networkabsorb.vpn

import android.net.VpnService
import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Reads raw IPv4 packets from the VPN TUN fd.
 *
 * DNS (UDP port 53):
 *   ABSORB — forwarded to real DNS server; A-record answer stored in [dnsCache].
 *   SERVE  — answered directly from [dnsCache]; no internet required.
 *
 * TCP (all ports):
 *   ABSORB — relayed to the real destination via a protected Socket.
 *            Gives internet access to ALL apps, even those that bypass the system proxy
 *            (e.g. apps with their own HTTP stack, games, peer-to-peer).
 *   SERVE  — dropped (no internet; proxy on loopback handles cached HTTP/HTTPS).
 *
 * HTTP/HTTPS via system proxy bypass TUN entirely (setHttpProxy → loopback).
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
        private const val PROTO_TCP      = 6
        private const val DNS_PORT       = 53
        private const val DNS_TIMEOUT_MS = 3_000

        // TCP flags
        private const val TCP_FIN = 0x01
        private const val TCP_SYN = 0x02
        private const val TCP_RST = 0x04
        private const val TCP_ACK = 0x10
    }

    @Volatile private var running = false
    private val input  = FileInputStream(vpnFd)
    private val output = FileOutputStream(vpnFd)
    private val iseq   = AtomicInteger(Random.nextInt())  // our ISN counter

    // Active TCP sessions
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    fun run() {
        running = true
        val buf = ByteArray(65535)
        Log.i(TAG, "TUN forwarder started (mode=$mode, dns_cached=${dnsCache.count()})")
        while (running) {
            try {
                val len = input.read(buf)
                if (len >= 20) processPacket(buf, len)
            } catch (_: Exception) { break }
        }
        // Clean up all sessions
        tcpSessions.values.forEach { it.close() }
        tcpSessions.clear()
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
        if ((buf[0].toInt() and 0xFF) ushr 4 != 4) return   // IPv4 only

        val ihl   = (buf[0].toInt() and 0x0F) * 4
        val proto = buf[9].toInt() and 0xFF

        when (proto) {
            PROTO_UDP -> {
                if (len < ihl + 8) return
                val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
                if (dstPort == DNS_PORT) handleDns(buf, ihl, len)
            }
            PROTO_TCP -> {
                if (len < ihl + 20) return
                handleTcp(buf, ihl, len)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DNS handling
    // ─────────────────────────────────────────────────────────────

    private fun handleDns(buf: ByteArray, ihl: Int, totalLen: Int) {
        val srcIp   = buf.copyOfRange(12, 16)
        val dstIp   = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        val off     = ihl + 8
        val payLen  = totalLen - off
        if (payLen < 13) return
        val query = buf.copyOfRange(off, off + payLen)

        if (mode == InternetExtractorVpnService.Mode.SERVE) {
            serveFromDnsCache(query, srcIp, srcPort, dstIp, dstPort)
        } else {
            forwardDnsAndLearn(query, srcIp, srcPort, dstIp, dstPort)
        }
    }

    private fun forwardDnsAndLearn(
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
            val resp = respBuf.copyOf(respPkt.length)
            learnDnsResponse(resp)
            writeUdpToTun(dstIp, dstPort, srcIp, srcPort, resp)
        } catch (e: Exception) {
            Log.d(TAG, "DNS forward failed: ${e.message}")
        }
    }

    private fun serveFromDnsCache(
        query: ByteArray,
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int
    ) {
        val domain = parseDnsQueryName(query) ?: return
        val ip     = dnsCache.get(domain) ?: run {
            Log.d(TAG, "DNS cache miss (offline): $domain")
            return
        }
        Log.d(TAG, "DNS cache hit: $domain → ${ip.joinToString(".")}")
        writeUdpToTun(dstIp, dstPort, srcIp, srcPort, buildDnsAResponse(query, ip))
    }

    // ─────────────────────────────────────────────────────────────
    // TCP relay — ABSORB mode only
    // ─────────────────────────────────────────────────────────────

    private fun handleTcp(buf: ByteArray, ihl: Int, totalLen: Int) {
        // In SERVE mode, TCP from TUN isn't useful (no internet; proxy handles cached content)
        if (mode == InternetExtractorVpnService.Mode.SERVE) return

        val tOff    = ihl
        val srcIp   = buf.copyOfRange(12, 16)
        val dstIp   = buf.copyOfRange(16, 20)
        val srcPort = u16(buf, tOff)
        val dstPort = u16(buf, tOff + 2)
        val seqNum  = u32(buf, tOff + 4)
        val flags   = buf[tOff + 13].toInt() and 0xFF
        val dataOff = (buf[tOff + 12].toInt() ushr 4) * 4
        val dataSt  = tOff + dataOff
        val dataLen = totalLen - dataSt

        val key = sessionKey(srcIp, srcPort, dstIp, dstPort)

        when {
            flags and TCP_RST != 0 -> {
                tcpSessions.remove(key)?.close()
            }
            flags and TCP_FIN != 0 -> {
                val sess = tcpSessions.remove(key)
                sess?.sendFin()
                sess?.close()
            }
            flags and TCP_SYN != 0 && flags and TCP_ACK == 0 -> {
                // New connection
                openSession(key, srcIp, srcPort, dstIp, dstPort, seqNum)
            }
            else -> {
                // Data/ACK
                val sess = tcpSessions[key]
                if (sess != null && dataLen > 0) {
                    val data = buf.copyOfRange(dataSt, dataSt + dataLen)
                    sess.forwardToServer(data, seqNum + dataLen)
                }
            }
        }
    }

    private fun openSession(
        key: String,
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        clientIsn: Int
    ) {
        try {
            val socket = Socket()
            vpnService.protect(socket)
            socket.connect(InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort), 10_000)
            socket.soTimeout = 30_000

            val myIsn  = iseq.getAndAdd(1)
            val mySeq  = myIsn + 1        // consumed by SYN
            val ackNum = clientIsn + 1    // ACK client's SYN

            // Send SYN-ACK
            writeTcpToTun(dstIp, dstPort, srcIp, srcPort, myIsn, ackNum, TCP_SYN or TCP_ACK)

            val session = TcpSession(
                srcIp = srcIp, srcPort = srcPort,
                dstIp = dstIp, dstPort = dstPort,
                socket = socket,
                mySeq  = mySeq,
                clientAck = ackNum,
                onData = { data, seq ->
                    // Server → TUN
                    writeTcpToTun(dstIp, dstPort, srcIp, srcPort, seq, 0, TCP_ACK or 0x08 /*PSH*/, data)
                },
                onClose = {
                    tcpSessions.remove(key)
                }
            )
            tcpSessions[key] = session
            session.startServerRead()
        } catch (e: Exception) {
            Log.d(TAG, "TCP open failed ${InetAddress.getByAddress(dstIp).hostAddress}:$dstPort: ${e.message}")
            writeTcpToTun(dstIp, dstPort, srcIp, srcPort, 0, clientIsn + 1, TCP_RST or TCP_ACK)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TcpSession inner class
    // ─────────────────────────────────────────────────────────────

    private inner class TcpSession(
        val srcIp: ByteArray, val srcPort: Int,
        val dstIp: ByteArray, val dstPort: Int,
        val socket: Socket,
        @Volatile var mySeq: Int,
        @Volatile var clientAck: Int,
        val onData: (ByteArray, Int) -> Unit,
        val onClose: () -> Unit
    ) {
        @Volatile private var closed = false

        fun forwardToServer(data: ByteArray, newClientSeq: Int) {
            if (closed) return
            try {
                socket.getOutputStream().apply { write(data); flush() }
                clientAck = newClientSeq
            } catch (_: Exception) { close() }
        }

        fun startServerRead() {
            Thread {
                val buf = ByteArray(4096)
                try {
                    val stream = socket.getInputStream()
                    while (!closed) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        if (n > 0) {
                            val chunk = buf.copyOf(n)
                            onData(chunk, mySeq)
                            mySeq += n
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    sendFin(); close()
                }
            }.apply { isDaemon = true; start() }
        }

        fun sendFin() {
            if (!closed) {
                runCatching {
                    writeTcpToTun(dstIp, dstPort, srcIp, srcPort, mySeq, clientAck, TCP_FIN or TCP_ACK)
                }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            socket.runCatching { close() }
            onClose()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DNS parsing helpers
    // ─────────────────────────────────────────────────────────────

    private fun parseDnsQueryName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val sb = StringBuilder()
        var i = 12
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

    private fun learnDnsResponse(dns: ByteArray) {
        if (dns.size < 12) return
        val anCount = u16(dns, 6)
        if (anCount == 0) return
        val domain = parseDnsQueryName(dns) ?: return

        var i = 12
        val qdCount = u16(dns, 4)
        repeat(qdCount) {
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) { i++; break }
                if (len and 0xC0 == 0xC0) { i += 2; break }
                i += 1 + len
            }
            i += 4
        }

        repeat(anCount) {
            if (i >= dns.size) return
            if ((dns[i].toInt() and 0xFF) and 0xC0 == 0xC0) i += 2
            else {
                while (i < dns.size) {
                    val len = dns[i].toInt() and 0xFF
                    if (len == 0) { i++; break }
                    i += 1 + len
                }
            }
            if (i + 10 > dns.size) return
            val rrType   = u16(dns, i)
            val rrClass  = u16(dns, i + 2)
            val rdLength = u16(dns, i + 8)
            i += 10
            if (rrType == 1 && rrClass == 1 && rdLength == 4 && i + 4 <= dns.size) {
                dnsCache.put(domain, dns.copyOfRange(i, i + 4))
            }
            i += rdLength
        }
    }

    private fun buildDnsAResponse(query: ByteArray, ip: ByteArray): ByteArray {
        var qEnd = 12
        while (qEnd < query.size) {
            val len = query[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd++; break }
            qEnd += 1 + len
        }
        qEnd += 4
        val question = query.copyOfRange(12, qEnd)
        val resp = ByteArray(12 + question.size + 16)
        resp[0] = query[0]; resp[1] = query[1]
        resp[2] = 0x81.toByte(); resp[3] = 0x80.toByte()
        resp[4] = 0; resp[5] = 1; resp[6] = 0; resp[7] = 1
        question.copyInto(resp, 12)
        val a = 12 + question.size
        resp[a]     = 0xC0.toByte(); resp[a + 1] = 0x0C
        resp[a + 2] = 0;             resp[a + 3] = 1     // A record
        resp[a + 4] = 0;             resp[a + 5] = 1     // IN class
        resp[a + 6] = 0;             resp[a + 7] = 0
        resp[a + 8] = 0x0E.toByte(); resp[a + 9] = 0x10  // TTL 3600
        resp[a + 10] = 0;            resp[a + 11] = 4     // RDLENGTH
        ip.copyInto(resp, a + 12)
        return resp
    }

    // ─────────────────────────────────────────────────────────────
    // UDP packet construction
    // ─────────────────────────────────────────────────────────────

    private fun writeUdpToTun(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ) {
        val udpLen   = 8 + payload.size
        val totalLen = 20 + udpLen
        val pkt      = ByteArray(totalLen)
        buildIpHeader(pkt, totalLen, PROTO_UDP, srcIp, dstIp)
        pkt[20] = (srcPort ushr 8).toByte(); pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort ushr 8).toByte(); pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = (udpLen  ushr 8).toByte(); pkt[25] = (udpLen  and 0xFF).toByte()
        payload.copyInto(pkt, 28)
        synchronized(output) { output.write(pkt, 0, totalLen); output.flush() }
    }

    // ─────────────────────────────────────────────────────────────
    // TCP packet construction
    // ─────────────────────────────────────────────────────────────

    private fun writeTcpToTun(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        seqNum: Int, ackNum: Int, flags: Int,
        payload: ByteArray = ByteArray(0)
    ) {
        val tcpLen   = 20 + payload.size
        val totalLen = 20 + tcpLen
        val pkt      = ByteArray(totalLen)

        buildIpHeader(pkt, totalLen, PROTO_TCP, srcIp, dstIp)

        pkt[20] = (srcPort ushr 8).toByte(); pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort ushr 8).toByte(); pkt[23] = (dstPort and 0xFF).toByte()
        put32(pkt, 24, seqNum)
        put32(pkt, 28, ackNum)
        pkt[32] = 0x50.toByte()              // data offset = 5 (20 bytes)
        pkt[33] = flags.toByte()
        pkt[34] = 0xFF.toByte(); pkt[35] = 0xFF.toByte()  // window 65535
        payload.copyInto(pkt, 40)

        val cksum = tcpChecksum(pkt, 20, tcpLen, srcIp, dstIp)
        pkt[36] = (cksum ushr 8).toByte(); pkt[37] = (cksum and 0xFF).toByte()

        synchronized(output) { output.write(pkt, 0, totalLen); output.flush() }
    }

    private fun buildIpHeader(pkt: ByteArray, totalLen: Int, proto: Int, src: ByteArray, dst: ByteArray) {
        pkt[0]  = 0x45.toByte()
        pkt[2]  = (totalLen ushr 8).toByte(); pkt[3] = (totalLen and 0xFF).toByte()
        pkt[8]  = 64
        pkt[9]  = proto.toByte()
        src.copyInto(pkt, 12); dst.copyInto(pkt, 16)
        val ck = ipChecksum(pkt, 0, 20)
        pkt[10] = (ck ushr 8).toByte(); pkt[11] = (ck and 0xFF).toByte()
    }

    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i   = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((len and 1) == 1) sum += (buf[off + len - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    private fun tcpChecksum(pkt: ByteArray, tcpOff: Int, tcpLen: Int, src: ByteArray, dst: ByteArray): Int {
        var sum = 0L
        // Pseudo-header
        for (i in 0..3 step 2) {
            sum += ((src[i].toInt() and 0xFF) shl 8) or (src[i + 1].toInt() and 0xFF)
            sum += ((dst[i].toInt() and 0xFF) shl 8) or (dst[i + 1].toInt() and 0xFF)
        }
        sum += PROTO_TCP
        sum += tcpLen
        // TCP segment
        var i = tcpOff
        while (i < tcpOff + tcpLen - 1) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((tcpLen and 1) == 1) sum += (pkt[tcpOff + tcpLen - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    // ─────────────────────────────────────────────────────────────
    // Byte-level helpers
    // ─────────────────────────────────────────────────────────────

    private fun u16(buf: ByteArray, off: Int) =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun u32(buf: ByteArray, off: Int) =
        ((buf[off].toInt() and 0xFF) shl 24) or
        ((buf[off + 1].toInt() and 0xFF) shl 16) or
        ((buf[off + 2].toInt() and 0xFF) shl 8) or
        (buf[off + 3].toInt() and 0xFF)

    private fun put32(buf: ByteArray, off: Int, v: Int) {
        buf[off]     = (v ushr 24).toByte()
        buf[off + 1] = (v ushr 16).toByte()
        buf[off + 2] = (v ushr  8).toByte()
        buf[off + 3] = (v         ).toByte()
    }

    private fun sessionKey(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int): String {
        val s = InetAddress.getByAddress(srcIp).hostAddress
        val d = InetAddress.getByAddress(dstIp).hostAddress
        return "$s:$srcPort-$d:$dstPort"
    }
}
