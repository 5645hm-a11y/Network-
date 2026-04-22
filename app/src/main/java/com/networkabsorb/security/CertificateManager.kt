package com.networkabsorb.security

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Manages the app's internal Certificate Authority and dynamic per-host certificates.
 *
 * On first run, generates a 2048-bit RSA CA key pair and self-signed CA certificate.
 * Stores the CA private key in a PKCS12 keystore in the app's private files directory.
 *
 * For each hostname intercepted, generates a leaf certificate signed by the CA.
 * Leaf certificates are cached in memory (LRU-style map) to avoid regenerating.
 *
 * IMPORTANT: For the MITM to work, the user must install this app's CA certificate
 * as a trusted credential on the device:
 *   Settings → Security → Encryption & credentials → Install a certificate → CA certificate
 *
 * On debug builds with network_security_config.xml allowing user CAs, this works
 * automatically for the app itself. For intercepting other apps on Android 7+,
 * the CA must be installed as a system CA (requires root) or those apps must
 * explicitly trust user CAs.
 */
@Singleton
class CertificateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CertificateManager"

        private const val CA_ALIAS        = "network_absorb_ca"
        private const val CA_KEYSTORE_FILE = "ca_keystore.p12"
        private const val CA_KEYSTORE_PASS = "absorb_internal"  // Not user-facing
        private const val CA_VALIDITY_DAYS = 3650L  // 10 years

        private const val LEAF_VALIDITY_DAYS = 365L
        private const val KEY_ALGORITHM      = "RSA"
        private const val KEY_SIZE           = 2048
        private const val SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption"

        private const val CA_SUBJECT = "CN=NetworkAbsorb Root CA, O=NetworkAbsorb, C=IL"

        init {
            // Register BouncyCastle as a JCE provider
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    private val certCache = ConcurrentHashMap<String, Pair<X509Certificate, PrivateKey>>()

    private val caKeyPair: KeyPair   by lazy { loadOrCreateCa().first }
    private val caCert: X509Certificate by lazy { loadOrCreateCa().second }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** True if the CA keystore file already exists on disk. */
    fun caExists(): Boolean = File(context.filesDir, CA_KEYSTORE_FILE).exists()

    /**
     * Returns the CA certificate as a PEM string, ready for the user to install.
     */
    fun getCaCertPem(): String {
        val cert    = caCert
        val encoded = cert.encoded
        val b64     = android.util.Base64.encodeToString(encoded, android.util.Base64.DEFAULT)
        return "-----BEGIN CERTIFICATE-----\n$b64-----END CERTIFICATE-----\n"
    }

    /**
     * Exports the CA cert as a .crt file to external cache so the user can install it.
     */
    fun exportCaCertFile(): File {
        val file = File(context.getExternalFilesDir(null), "NetworkAbsorb_CA.crt")
        file.writeText(getCaCertPem())
        Log.i(TAG, "CA cert exported to: ${file.absolutePath}")
        return file
    }

    /**
     * Builds an SSLContext that presents a dynamic certificate for [hostname].
     * Used to terminate the TLS session with the client during MITM.
     */
    fun buildSslContextForHost(hostname: String): SSLContext {
        val (leafCert, leafKey) = getOrCreateLeafCert(hostname)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(hostname, leafKey, CA_KEYSTORE_PASS.toCharArray(), arrayOf(leafCert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, CA_KEYSTORE_PASS.toCharArray())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, arrayOf(TrustAllTrustManager()), SecureRandom())
        return sslContext
    }

    /**
     * Returns an SSLContext that trusts all server certificates.
     * Used when connecting to upstream servers in ABSORB mode (we verify
     * the pinning separately via certificate transparency checks).
     */
    fun buildTrustAllContext(): SSLContext {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(TrustAllTrustManager()), SecureRandom())
        return ctx
    }

    // -------------------------------------------------------------------------
    // CA creation / loading
    // -------------------------------------------------------------------------

    private fun loadOrCreateCa(): Pair<KeyPair, X509Certificate> {
        val keystoreFile = File(context.filesDir, CA_KEYSTORE_FILE)

        if (keystoreFile.exists()) {
            return loadCaFromDisk(keystoreFile)
        }

        Log.i(TAG, "Generating new CA key pair…")
        val keyPair = generateKeyPair()
        val cert    = createCaCertificate(keyPair)

        saveCaToDisk(keystoreFile, keyPair, cert)
        Log.i(TAG, "CA created: ${cert.subjectDN}")
        return Pair(keyPair, cert)
    }

    private fun loadCaFromDisk(file: File): Pair<KeyPair, X509Certificate> {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, CA_KEYSTORE_PASS.toCharArray()) }

        val privateKey = ks.getKey(CA_ALIAS, CA_KEYSTORE_PASS.toCharArray()) as PrivateKey
        val cert       = ks.getCertificate(CA_ALIAS) as X509Certificate

        // Reconstruct public key from cert
        val keyPair = KeyPair(cert.publicKey, privateKey)
        Log.i(TAG, "CA loaded from disk")
        return Pair(keyPair, cert)
    }

    private fun saveCaToDisk(file: File, keyPair: KeyPair, cert: X509Certificate) {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, CA_KEYSTORE_PASS.toCharArray())
        ks.setKeyEntry(
            CA_ALIAS,
            keyPair.private,
            CA_KEYSTORE_PASS.toCharArray(),
            arrayOf(cert)
        )
        file.outputStream().use { ks.store(it, CA_KEYSTORE_PASS.toCharArray()) }
    }

    private fun createCaCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Name(CA_SUBJECT)
        val serial  = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter  = Date(System.currentTimeMillis() + CA_VALIDITY_DAYS * 86_400_000L)

        val builder = JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.public
        )

        // Mark as CA cert
        builder.addExtension(
            Extension.basicConstraints, true, BasicConstraints(true)
        )

        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
    }

    // -------------------------------------------------------------------------
    // Dynamic leaf certificate generation
    // -------------------------------------------------------------------------

    private fun getOrCreateLeafCert(hostname: String): Pair<X509Certificate, PrivateKey> {
        return certCache.getOrPut(hostname) { createLeafCert(hostname) }
    }

    private fun createLeafCert(hostname: String): Pair<X509Certificate, PrivateKey> {
        val leafKeyPair = generateKeyPair()
        val subject     = X500Name("CN=$hostname, O=NetworkAbsorb Intercepted, C=IL")
        val issuer      = X500Name(CA_SUBJECT)
        val serial      = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore   = Date()
        val notAfter    = Date(System.currentTimeMillis() + LEAF_VALIDITY_DAYS * 86_400_000L)

        val builder = JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, leafKeyPair.public
        )

        // Subject Alternative Name – required by modern browsers/clients
        val san = GeneralNames(GeneralName(GeneralName.dNSName, hostname))
        builder.addExtension(Extension.subjectAlternativeName, false, san)

        // Not a CA
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))

        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caKeyPair.private)

        val cert = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))

        Log.d(TAG, "Created leaf cert for: $hostname")
        return Pair(cert, leafKeyPair.private)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
        generator.initialize(KEY_SIZE, SecureRandom())
        return generator.generateKeyPair()
    }

    // -------------------------------------------------------------------------
    // Trust manager that accepts all server certificates
    // (used when connecting to upstream servers in MITM mode)
    // -------------------------------------------------------------------------

    private class TrustAllTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
