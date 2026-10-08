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

data class SsdpResponse(
    val ipAddress: InetAddress,
    val location: String?,
    val st: String?,
    val usn: String?,
    val server: String?,
    val cacheControl: String?
)

object SsdpDiscoveryScanner {
    private const val TAG = "SsdpDiscoveryScanner"
    private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900

    fun discover(context: Context, timeoutMs: Int = 2500, passes: Int = 2): List<SsdpResponse> {
        val responses = mutableMapOf<String, SsdpResponse>()

        repeat(passes) { pass ->
            val socket = try {
                DatagramSocket().apply {
                    soTimeout = timeoutMs
                    NetworkInterfaceBinder.bindSocketToWifi(context, this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create DatagramSocket for SSDP pass $pass: ${e.message}")
                return@repeat
            }

            try {
                val message = """
                    M-SEARCH * HTTP/1.1
                    HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
                    MAN: "ssdp:discover"
                    MX: ${timeoutMs / 1000}
                    ST: ssdp:all
                    
                """.trimIndent().replace("\n", "\r\n")

                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val packet = DatagramPacket(message.toByteArray(), message.length, group, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(responsePacket)
                        val content = String(responsePacket.data, 0, responsePacket.length)
                        val addr = responsePacket.address ?: continue

                        val parsed = parseSsdpHeaders(addr, content)
                        val key = addr.hostAddress ?: continue
                        if (!responses.containsKey(key) || parsed.location != null) {
                            responses[key] = parsed
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during SSDP M-SEARCH pass $pass: ${e.message}")
            } finally {
                socket.close()
            }
        }

        return responses.values.toList()
    }

    private fun parseSsdpHeaders(address: InetAddress, content: String): SsdpResponse {
        var location: String? = null
        var st: String? = null
        var usn: String? = null
        var server: String? = null
        var cacheControl: String? = null

        val lines = content.split("\r\n")
        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("location:") -> location = line.substring(9).trim()
                lower.startsWith("st:") -> st = line.substring(3).trim()
                lower.startsWith("usn:") -> usn = line.substring(4).trim()
                lower.startsWith("server:") -> server = line.substring(7).trim()
                lower.startsWith("cache-control:") -> cacheControl = line.substring(14).trim()
            }
        }

        return SsdpResponse(address, location, st, usn, server, cacheControl)
    }

    fun toDiscoveredDevice(response: SsdpResponse): DiscoveredDevice {
        val notes = mutableListOf<String>()
        notes.add("Discovered via SSDP/UPnP M-SEARCH")
        response.server?.let { notes.add("Server: $it") }
        response.st?.let { notes.add("ST: $it") }

        val category = when {
            response.st?.contains("MediaServer", ignoreCase = true) == true ||
                    response.st?.contains("MediaRenderer", ignoreCase = true) == true -> DeviceCategory.MEDIA_STREAMER
            response.st?.contains("Printer", ignoreCase = true) == true -> DeviceCategory.PRINTER
            else -> DeviceCategory.UNKNOWN_REACHABLE
        }

        val service = DiscoveredService(
            serviceType = response.st ?: "ssdp:all",
            name = response.usn ?: "UPnP Device",
            port = 1900,
            txtRecords = mapOf(
                "location" to (response.location ?: ""),
                "server" to (response.server ?: ""),
                "usn" to (response.usn ?: "")
            ),
            source = DiscoverySource.SSDP
        )

        return DiscoveredDevice(
            ipAddress = response.ipAddress,
            services = listOf(service),
            sources = setOf(DiscoverySource.SSDP),
            category = category,
            confidence = Confidence.MEDIUM,
            notes = notes
        )
    }
}
