package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.util.Log
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoveredService
import com.brutiful.netprobe.model.DiscoverySource
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

data class WsDiscoveryResponse(
    val ipAddress: InetAddress,
    val types: String?,
    val scopes: String?,
    val xAddrs: String?,
    val rawXml: String
)

object WsDiscoveryScanner {
    private const val TAG = "WsDiscoveryScanner"
    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val WS_DISCOVERY_PORT = 3702

    fun discover(context: Context, timeoutMs: Int = 2500, passes: Int = 2): List<WsDiscoveryResponse> {
        val responses = mutableMapOf<String, WsDiscoveryResponse>()

        repeat(passes) { pass ->
            val socket = try {
                DatagramSocket().apply {
                    soTimeout = timeoutMs
                    NetworkInterfaceBinder.bindSocketToWifi(context, this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create DatagramSocket for WS-Discovery pass $pass: ${e.message}")
                return@repeat
            }

            try {
                val uuid = UUID.randomUUID().toString()
                val probeXml = """
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
                val packet = DatagramPacket(probeXml.toByteArray(), probeXml.length, group, WS_DISCOVERY_PORT)
                socket.send(packet)

                val buffer = ByteArray(8192)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(responsePacket)
                        val content = String(responsePacket.data, 0, responsePacket.length)
                        val addr = responsePacket.address ?: continue

                        val types = Regex("<(?:.*:)?Types>(.*?)</(?:.*:)?Types>").find(content)?.groupValues?.get(1)
                        val scopes = Regex("<(?:.*:)?Scopes>(.*?)</(?:.*:)?Scopes>").find(content)?.groupValues?.get(1)
                        val xAddrs = Regex("<(?:.*:)?XAddrs>(.*?)</(?:.*:)?XAddrs>").find(content)?.groupValues?.get(1)

                        val key = addr.hostAddress ?: continue
                        responses[key] = WsDiscoveryResponse(addr, types, scopes, xAddrs, content)
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in WS-Discovery pass $pass: ${e.message}")
            } finally {
                socket.close()
            }
        }

        return responses.values.toList()
    }

    fun toDiscoveredDevice(response: WsDiscoveryResponse): DiscoveredDevice {
        val scopesList = response.scopes?.split(" ") ?: emptyList()
        val onvifName = scopesList.find { it.contains("name/") }?.substringAfter("name/")?.replace("_", " ")
        val onvifHardware = scopesList.find { it.contains("hardware/") }?.substringAfter("hardware/")
        val isCamera = response.types?.contains("NetworkVideoTransmitter", ignoreCase = true) == true ||
                response.scopes?.contains("onvif", ignoreCase = true) == true

        val notes = mutableListOf<String>()
        notes.add("Discovered via WS-Discovery")
        onvifName?.let { notes.add("Name: $it") }
        onvifHardware?.let { notes.add("Hardware: $it") }

        val category = if (isCamera) DeviceCategory.CAMERA else DeviceCategory.UNKNOWN_REACHABLE

        val service = DiscoveredService(
            serviceType = response.types ?: "dn:NetworkVideoTransmitter",
            name = onvifName ?: "WS-Discovery Target",
            port = 3702,
            txtRecords = mapOf(
                "xAddrs" to (response.xAddrs ?: ""),
                "scopes" to (response.scopes ?: ""),
                "types" to (response.types ?: "")
            ),
            source = DiscoverySource.WS_DISCOVERY
        )

        return DiscoveredDevice(
            ipAddress = response.ipAddress,
            displayName = onvifName,
            modelName = onvifHardware,
            services = listOf(service),
            sources = setOf(DiscoverySource.WS_DISCOVERY),
            category = category,
            confidence = Confidence.HIGH,
            notes = notes
        )
    }
}
