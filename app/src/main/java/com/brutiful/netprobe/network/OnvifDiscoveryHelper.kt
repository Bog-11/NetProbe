package com.brutiful.netprobe.network

import android.util.Log
import com.brutiful.netprobe.model.DeviceFingerprint
import com.brutiful.netprobe.model.FingerprintSource
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

object OnvifDiscoveryHelper {
    private const val TAG = "OnvifDiscovery"
    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val WS_DISCOVERY_PORT = 3702
    private const val DEFAULT_TIMEOUT = 3000

    fun discover(passes: Int = 3, timeoutPerPass: Int = DEFAULT_TIMEOUT): List<DeviceFingerprint> {
        val fingerprints = mutableMapOf<String, DeviceFingerprint>()
        
        repeat(passes) { pass ->
            Log.d(TAG, "Starting ONVIF pass ${pass + 1}/$passes")
            val socket = DatagramSocket()
            socket.soTimeout = timeoutPerPass

            try {
                val uuid = UUID.randomUUID().toString()
                val probe = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                                xmlns:d="http://schemas.xmlsoap.org/ws/2004/08/discovery"
                                xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                        <e:Header>
                            <w:MessageID>uuid:$uuid</w:MessageID>
                            <w:To>urn:schemas-xmlsoap-org:ws:2004:08:discovery</w:To>
                            <w:Action>http://schemas.xmlsoap.org/ws/2004/08/discovery/Probe</w:Action>
                        </e:Header>
                        <e:Body>
                            <d:Probe>
                                <d:Types>dn:NetworkVideoTransmitter</d:Types>
                            </d:Probe>
                        </e:Body>
                    </e:Envelope>
                """.trimIndent()

                val group = InetAddress.getByName(MULTICAST_ADDRESS)
                val packet = DatagramPacket(probe.toByteArray(), probe.length, group, WS_DISCOVERY_PORT)
                socket.send(packet)

                val buffer = ByteArray(8192)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutPerPass) {
                    val response = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(response)
                        val content = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress

                        if (!fingerprints.containsKey(ip)) {
                            parseOnvifFingerprint(ip, content)?.let {
                                fingerprints[ip] = it
                            }
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ONVIF pass ${pass + 1}", e)
            } finally {
                socket.close()
            }
            
            if (pass < passes - 1) {
                Thread.sleep(300)
            }
        }

        return fingerprints.values.toList()
    }

    private fun parseOnvifFingerprint(ip: String, content: String): DeviceFingerprint? {
        val types = Regex("<(?:.*:)?Types>(.*?)</(?:.*:)?Types>").find(content)?.groupValues?.get(1)
        val scopesRaw = Regex("<(?:.*:)?Scopes>(.*?)</(?:.*:)?Scopes>").find(content)?.groupValues?.get(1)
        val xAddrs = Regex("<(?:.*:)?XAddrs>(.*?)</(?:.*:)?XAddrs>").find(content)?.groupValues?.get(1)

        val scopes = scopesRaw?.split(" ") ?: emptyList()
        val isCamera = types?.contains("NetworkVideoTransmitter") == true
        
        val onvifName = scopes.find { it.contains("name/") }?.substringAfter("name/")?.replace("_", " ")
        val onvifModel = scopes.find { it.contains("hardware/") }?.substringAfter("hardware/")
        
        var manufacturer = when {
            onvifName?.contains("HIKVISION", ignoreCase = true) == true -> "Hikvision"
            onvifName?.contains("DAHUA", ignoreCase = true) == true -> "Dahua"
            else -> null
        }

        return DeviceFingerprint(
            source = FingerprintSource.ONVIF,
            vendor = manufacturer,
            model = onvifModel,
            deviceType = if (isCamera) "IP Camera" else "ONVIF Device",
            friendlyName = onvifName,
            confidence = 65,
            rawMetadata = mapOf(
                "ip" to ip,
                "types" to (types ?: ""),
                "xAddrs" to (xAddrs ?: ""),
                "scopes" to (scopesRaw ?: "")
            )
        )
    }
}
