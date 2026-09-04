package com.brutiful.netprobe.network

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

class CertificateManager(private val context: Context) {
    private val rootCaFile = File(context.filesDir, "root_ca.crt")
    private val rootKeyFile = File(context.filesDir, "root_ca.key")
    
    private var rootCa: X509Certificate? = null
    private var rootKey: PrivateKey? = null

    init {
        loadOrCreateRootCa()
    }

    private fun loadOrCreateRootCa() {
        if (rootCaFile.exists() && rootKeyFile.exists()) {
            try {
                val cf = CertificateFactory.getInstance("X.509")
                rootCa = cf.generateCertificate(FileInputStream(rootCaFile)) as X509Certificate
                
                val keyBytes = rootKeyFile.readBytes()
                val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
                val kf = KeyFactory.getInstance("RSA")
                rootKey = kf.generatePrivate(keySpec)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        generateRootCa()
    }

    private fun generateRootCa() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()
        rootKey = keyPair.private
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60 * 60 * 24 * 30) // 30 days ago
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365 * 10) // 10 years
        
        val name = X500Name("CN=NetProbe Root CA, O=Brutiful, C=US")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        
        val builder = JcaX509v3CertificateBuilder(
            name, serial, notBefore, notAfter, name, keyPair.public
        )
        
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(rootKey)
        rootCa = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
        
        // Save to files
        rootCaFile.writeBytes(rootCa!!.encoded)
        rootKeyFile.writeBytes(rootKey!!.encoded)
    }

    fun generateLeafCertificate(hostname: String): Pair<X509Certificate, PrivateKey> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()
        
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60 * 60)
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365)
        
        val subject = X500Name("CN=$hostname")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        
        val builder = JcaX509v3CertificateBuilder(
            rootCa!!, serial, notBefore, notAfter, subject, keyPair.public
        )
        
        val altNames = arrayOf(GeneralName(GeneralName.dNSName, hostname))
        builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(altNames))
        
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(rootKey)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
        
        return Pair(cert, keyPair.private)
    }

    fun getRootCa(): X509Certificate? = rootCa
    
    fun getRootCaPem(): String {
        val encoder = Base64.getEncoder()
        val certBase64 = encoder.encodeToString(rootCa?.encoded ?: return "")
        return "-----BEGIN CERTIFICATE-----\n" +
                certBase64.chunked(64).joinToString("\n") +
                "\n-----END CERTIFICATE-----"
    }
}
