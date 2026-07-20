package com.brutiful.netprobe.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import java.util.Date

object ProbeHelpers {
    private const val TAG = "ProbeHelpers"

    data class ProbeResult(
        val protocol: String,
        val port: Int,
        val metadata: Map<String, String>,
        val confidenceContribution: Int
    )

    fun isPortOpen(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun probeHttp(ip: String, port: Int, timeout: Int): ProbeResult? {
        val protocol = if (port == 443 || port == 8443 || port == 9443) "https" else "http"
        val urlString = "$protocol://$ip:$port/"
        val metadata = mutableMapOf<String, String>()
        var confidence = 10

        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.instanceFollowRedirects = false // Manual handle for deeper info

            if (connection is HttpsURLConnection) {
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        chain?.firstOrNull()?.let { cert ->
                            metadata["TLS_Subject"] = cert.subjectX500Principal.name
                            metadata["TLS_Issuer"] = cert.issuerX500Principal.name
                            metadata["TLS_NotBefore"] = cert.notBefore.toString()
                            metadata["TLS_NotAfter"] = cert.notAfter.toString()
                            metadata["TLS_Serial"] = cert.serialNumber.toString(16)
                            cert.subjectAlternativeNames?.let { sanList ->
                                metadata["TLS_SAN"] = sanList.joinToString(", ") { it[1].toString() }
                            }
                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
                connection.sslSocketFactory = sc.socketFactory
                connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }

            connection.connect()
            
            val responseCode = connection.responseCode
            metadata["ResponseCode"] = responseCode.toString()
            
            if (responseCode in 300..399) {
                connection.getHeaderField("Location")?.let { metadata["RedirectLocation"] = it }
            }

            val server = connection.getHeaderField("Server")
            if (!server.isNullOrBlank()) {
                metadata["Server"] = server
                confidence += 25
            }

            val contentType = connection.contentType
            if (!contentType.isNullOrBlank()) metadata["ContentType"] = contentType

            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            titleRegex.find(content)?.groupValues?.get(1)?.let { 
                metadata["Title"] = it.trim()
                confidence += 25
            }

            return ProbeResult("HTTP", port, metadata, confidence)
        } catch (e: Exception) {
            return null
        }
    }

    fun probeRtsp(ip: String, port: Int, timeout: Int): ProbeResult? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                val out = socket.getOutputStream()
                val ins = socket.getInputStream()
                
                val request = "OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: NetProbe\r\n\r\n"
                out.write(request.toByteArray())
                
                val buffer = ByteArray(2048)
                val read = ins.read(buffer)
                if (read > 0) {
                    val response = String(buffer, 0, read)
                    val metadata = mutableMapOf<String, String>()
                    var confidence = 40
                    
                    val serverRegex = Regex("Server: (.*)", RegexOption.IGNORE_CASE)
                    serverRegex.find(response)?.groupValues?.get(1)?.let {
                        metadata["Server"] = it.trim()
                        confidence += 30
                    }
                    
                    val publicRegex = Regex("Public: (.*)", RegexOption.IGNORE_CASE)
                    publicRegex.find(response)?.groupValues?.get(1)?.let {
                        metadata["Methods"] = it.trim()
                    }
                    
                    return ProbeResult("RTSP", port, metadata, confidence)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun probeSnmp(ip: String, timeout: Int): ProbeResult? {
        val port = 161
        // Minimal SNMP GetRequest for sysDescr (1.3.6.1.2.1.1.1.0)
        val pdu = byteArrayOf(
            0x30, 0x26, 0x02, 0x01, 0x00, 0x04, 0x06, 0x70, 0x75, 0x62, 0x6c, 0x69, 0x63, 
            0xa0.toByte(), 0x19, 0x02, 0x04, 0x1a, 0x2b, 0x3c, 0x4d, 0x02, 0x01, 0x00, 0x02, 0x01, 0x00, 
            0x30, 0x0b, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x00
        )
        
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeout
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(pdu, pdu.size, address, port)
                socket.send(packet)
                
                val responseBuffer = ByteArray(2048)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                val response = responsePacket.data.take(responsePacket.length).toByteArray()
                if (response.size > 30) {
                    val result = parseSnmpString(response)
                    if (result != null) {
                        return ProbeResult("SNMP", port, mapOf("sysDescr" to result), 50)
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun parseSnmpString(response: ByteArray): String? {
        var i = response.size - 1
        while (i > 0) {
            if (response[i].toInt() == 0x04) {
                val length = response[i + 1].toInt()
                if (i + 2 + length <= response.size) {
                    return String(response, i + 2, length)
                }
            }
            i--
        }
        return null
    }

    fun probeBanner(ip: String, port: Int, protocol: String, timeout: Int): ProbeResult? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                socket.soTimeout = timeout
                val ins = socket.getInputStream()
                
                // For most services (SSH, FTP, Telnet), they send a banner immediately
                val buffer = ByteArray(1024)
                val read = ins.read(buffer)
                if (read > 0) {
                    val banner = String(buffer, 0, read).trim()
                    return ProbeResult(protocol, port, mapOf("Banner" to banner), 40)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun probeNetBios(ip: String, timeout: Int): ProbeResult? {
        val port = 137
        val query = byteArrayOf(
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte(), 0x01.toByte(), 
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 
            0x20.toByte(), 0x43.toByte(), 0x4b.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 
            0x41.toByte(), 0x41.toByte(), 0x00.toByte(), 0x00.toByte(), 0x21.toByte(), 0x00.toByte(), 0x01.toByte()
        )

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeout
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(query, query.size, address, port)
                socket.send(packet)
                
                val buffer = ByteArray(1024)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(responsePacket)
                
                val response = responsePacket.data
                if (response.size > 57) {
                    val nameCount = response[56].toInt()
                    if (nameCount > 0) {
                        val name = String(response, 57, 15).trim()
                        return ProbeResult("NetBIOS", port, mapOf("Name" to name), 40)
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    fun probeSip(ip: String, port: Int, timeout: Int): ProbeResult? {
        val query = "OPTIONS sip:$ip SIP/2.0\r\nVia: SIP/2.0/UDP $ip:$port;branch=z9hG4bK776asdf\r\nFrom: <sip:netprobe@$ip>;tag=12345\r\nTo: <sip:netprobe@$ip>\r\nCall-ID: 54321@$ip\r\nCSeq: 1 OPTIONS\r\nContact: <sip:netprobe@$ip>\r\nMax-Forwards: 70\r\nUser-Agent: NetProbe\r\nContent-Length: 0\r\n\r\n"
        
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeout
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(query.toByteArray(), query.length, address, port)
                socket.send(packet)
                
                val responseBuffer = ByteArray(2048)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                val response = String(responsePacket.data, 0, responsePacket.length)
                if (response.contains("SIP/2.0")) {
                    val metadata = mutableMapOf<String, String>()
                    val serverRegex = Regex("Server: (.*)", RegexOption.IGNORE_CASE)
                    serverRegex.find(response)?.groupValues?.get(1)?.let { metadata["Server"] = it.trim() }
                    return ProbeResult("SIP", port, metadata, 45)
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
