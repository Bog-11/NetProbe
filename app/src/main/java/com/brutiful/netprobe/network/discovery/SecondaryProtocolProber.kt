package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.util.Log
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoveredService
import com.brutiful.netprobe.model.DiscoverySource
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SecondaryProtocolProber {
    private const val TAG = "SecondaryProtocolProber"

    fun probeSecondaryEvidence(context: Context, device: DiscoveredDevice): DiscoveredDevice {
        var updated = device

        // 1. HTTP/HTTPS metadata fetch ONLY for HTTP/HTTPS responders (port 80 or 443)
        if (updated.openPorts.contains(80) || updated.openPorts.contains(443)) {
            val httpMeta = fetchHttpMetadata(context, updated.ipString, if (updated.openPorts.contains(443)) 443 else 80)
            if (httpMeta != null) {
                val newNotes = updated.notes + "HTTP Server: ${httpMeta.first}" + (httpMeta.second?.let { "Title: $it" } ?: "")
                updated = updated.copy(
                    displayName = updated.displayName ?: httpMeta.second,
                    notes = newNotes
                )
            }
        }

        // 2. RTSP probe ONLY for camera candidates or port 554 open
        if (updated.category == DeviceCategory.CAMERA || updated.openPorts.contains(554)) {
            val rtspServer = probeRtspServer(context, updated.ipString, 554)
            if (rtspServer != null) {
                updated = updated.copy(
                    category = DeviceCategory.CAMERA,
                    confidence = Confidence.HIGH,
                    notes = updated.notes + "RTSP Server: $rtspServer"
                )
            }
        }

        // 3. IPP / JetDirect probe ONLY for printer candidates (port 631 or 9100 or ipp service)
        if (updated.category == DeviceCategory.PRINTER || updated.openPorts.contains(631) || updated.openPorts.contains(9100)) {
            val isPrinterConfirmed = probePrinterPort(context, updated.ipString, if (updated.openPorts.contains(631)) 631 else 9100)
            if (isPrinterConfirmed) {
                updated = updated.copy(
                    category = DeviceCategory.PRINTER,
                    confidence = Confidence.HIGH,
                    notes = updated.notes + "Printer service verified"
                )
            }
        }

        // 4. UPnP Description fetch ONLY for SSDP responders with a location URL
        val ssdpLocation = updated.services.find { it.source == DiscoverySource.SSDP }?.txtRecords?.get("location")
        if (!ssdpLocation.isNullOrBlank()) {
            val upnpDetails = fetchUpnpDescription(ssdpLocation)
            if (upnpDetails != null) {
                updated = updated.copy(
                    displayName = updated.displayName ?: upnpDetails.friendlyName,
                    manufacturer = updated.manufacturer ?: upnpDetails.manufacturer,
                    modelName = updated.modelName ?: upnpDetails.modelName,
                    notes = updated.notes + "UPnP Name: ${upnpDetails.friendlyName}"
                )
            }
        }

        return updated
    }

    private fun fetchHttpMetadata(context: Context, ip: String, port: Int): Pair<String?, String?>? {
        return try {
            val proto = if (port == 443) "https" else "http"
            val url = URL("$proto://$ip:$port/")
            val conn = url.openConnection()
            conn.connectTimeout = 1000
            conn.readTimeout = 1000

            if (conn is HttpsURLConnection) {
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
                conn.sslSocketFactory = sc.socketFactory
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }

            conn.connect()
            val server = conn.getHeaderField("Server")
            val content = conn.getInputStream()?.bufferedReader()?.use { it.readText() } ?: ""
            val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)?.trim()

            Pair(server, title)
        } catch (_: Exception) {
            null
        }
    }

    private fun probeRtspServer(context: Context, ip: String, port: Int): String? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            NetworkInterfaceBinder.bindSocketToWifi(context, socket)
            socket.connect(InetSocketAddress(ip, port), 1000)
            socket.soTimeout = 1000

            val out = socket.getOutputStream()
            val ins = socket.getInputStream()
            val req = "OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: NetProbe\r\n\r\n"
            out.write(req.toByteArray())

            val buf = ByteArray(1024)
            val read = ins.read(buf)
            if (read > 0) {
                val resp = String(buf, 0, read)
                Regex("Server: (.*)", RegexOption.IGNORE_CASE).find(resp)?.groupValues?.get(1)?.trim()
            } else null
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun probePrinterPort(context: Context, ip: String, port: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            NetworkInterfaceBinder.bindSocketToWifi(context, socket)
            socket.connect(InetSocketAddress(ip, port), 800)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private data class UpnpMeta(val friendlyName: String?, val manufacturer: String?, val modelName: String?)

    private fun fetchUpnpDescription(locationUrl: String): UpnpMeta? {
        return try {
            val url = URL(locationUrl)
            val conn = url.openConnection()
            conn.connectTimeout = 1200
            conn.readTimeout = 1200
            val content = conn.getInputStream()?.bufferedReader()?.use { it.readText() } ?: ""

            val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)
            val mfr = Regex("<manufacturer>(.*?)</manufacturer>", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)
            val model = Regex("<modelName>(.*?)</modelName>", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)

            if (name != null || mfr != null || model != null) {
                UpnpMeta(name, mfr, model)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
