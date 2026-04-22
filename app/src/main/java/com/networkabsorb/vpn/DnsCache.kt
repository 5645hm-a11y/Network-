package com.networkabsorb.vpn

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent DNS cache: domain → IPv4 address.
 *
 * Populated automatically during ABSORB mode whenever a DNS query succeeds.
 * Read during SERVE mode so DNS keeps working with zero internet connectivity.
 */
@Singleton
class DnsCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG   = "DnsCache"
        private const val PREFS = "dns_cache_v1"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    fun put(domain: String, ip: ByteArray) {
        if (ip.size != 4) return
        val dotted = ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
        prefs.edit().putString(domain.lowercase().trimEnd('.'), dotted).apply()
        Log.d(TAG, "DNS learned: $domain → $dotted")
    }

    fun get(domain: String): ByteArray? {
        val dotted = prefs.getString(domain.lowercase().trimEnd('.'), null) ?: return null
        return try { InetAddress.getByName(dotted).address } catch (_: Exception) { null }
    }

    fun count(): Int = prefs.all.size

    fun clear() = prefs.edit().clear().apply()
}
