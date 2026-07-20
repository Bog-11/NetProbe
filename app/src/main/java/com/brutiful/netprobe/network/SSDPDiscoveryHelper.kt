package com.brutiful.netprobe.network

import android.util.Log
import android.util.Xml
import com.brutiful.netprobe.model.DeviceFingerprint
import com.brutiful.netprobe.model.FingerprintSource
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL

object SSDPDiscoveryHelper {
    private const val TAG = "SSDPDiscovery"
    private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val DEFAULT_TIMEOUT = 3000

    fun discover(passes: Int = 3, timeoutPerPass: Int = DEFAULT_TIMEOUT): List<DeviceFingerprint> {
        val fingerprints = mutableMapOf<String, DeviceFingerprint>()
        
        repeat(passes) { pass ->
            Log.d(TAG, "Starting SSDP pass ${pass + 1}/$passes")
            val socket = DatagramSocket()
            socket.soTimeout = timeoutPerPass

            try {
                val message = """
                    M-SEARCH * HTTP/1.1
                    HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
                    MAN: "ssdp:discover"
                    MX: ${timeoutPerPass / 1000}
                    ST: ssdp:all
                    
                """.trimIndent().replace("\n", "\r\n")

                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val packet = DatagramPacket(message.toByteArray(), message.length, group, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutPerPass) {
                    val response = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(response)
                        val content = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress
                        
                        parseLocation(content)?.let { location ->
                            if (!fingerprints.containsKey(ip)) {
                                fetchSsdpFingerprint(ip, location)?.let { 
                                    fingerprints[ip] = it
                                }
                            }
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SSDP pass ${pass + 1}", e)
            } finally {
                socket.close()
            }
            
            if (pass < passes - 1) {
                Thread.sleep(200)
            }
        }

        return fingerprints.values.toList()
    }

    private fun parseLocation(content: String): String? {
        val lines = content.split("\r\n")
        for (line in lines) {
            if (line.uppercase().startsWith("LOCATION:")) {
                return line.substring(9).trim()
            }
        }
        return null
    }

    private fun fetchSsdpFingerprint(ip: String, location: String): DeviceFingerprint? {
        return try {
            val url = URL(location)
            val connection = url.openConnection()
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            
            val inputStream = connection.getInputStream()
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, null)

            var friendlyName: String? = null
            var manufacturer: String? = null
            var modelName: String? = null
            var deviceType: String? = null
            val metadata = mutableMapOf<String, String>()
            metadata["location"] = location

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    when (tagName) {
                        "friendlyName" -> friendlyName = parser.nextText()
                        "manufacturer" -> manufacturer = parser.nextText()
                        "modelName" -> modelName = parser.nextText()
                        "deviceType" -> if (deviceType == null) deviceType = parser.nextText()
                        else -> {
                            // Collect other tags as metadata
                            try {
                                val text = parser.nextText()
                                if (text.isNotBlank()) metadata[tagName] = text
                            } catch (e: Exception) { /* skip */ }
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()

            DeviceFingerprint(
                source = FingerprintSource.SSDP,
                vendor = manufacturer,
                model = modelName,
                deviceType = deviceType,
                friendlyName = friendlyName,
                confidence = if (friendlyName != null) 50 else 30,
                rawMetadata = metadata + mapOf("ip" to ip)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SSDP details from $location", e)
            DeviceFingerprint(
                source = FingerprintSource.SSDP,
                friendlyName = "UPnP Device",
                confidence = 10,
                rawMetadata = mapOf("location" to location, "ip" to ip, "error" to (e.message ?: "unknown"))
            )
        }
    }
}
