package com.brutiful.netprobe.network

import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class RouterClient(
    val ipAddress: String,
    val macAddress: String? = null,
    val hostName: String? = null,
    val leaseState: String? = null,
    val firstSeen: Long? = null,
    val lastSeen: Long? = null,
    val interfaceName: String? = null,
    val connectionType: String? = null
)

interface RouterClientSource {
    suspend fun getClients(): List<RouterClient>
}

class ManualRouterClientSource(private val rawData: String) : RouterClientSource {
    override suspend fun getClients(): List<RouterClient> {
        // Simulates parsing of manual CSV/JSON export
        val clients = mutableListOf<RouterClient>()
        try {
            val lines = rawData.split("\n")
            lines.forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val rawHost = parts.getOrNull(2)?.trim()
                    clients.add(
                        RouterClient(
                            ipAddress = parts[0].trim(),
                            macAddress = parts.getOrNull(1)?.trim(),
                            hostName = NetworkUtils.sanitizeHostName(rawHost) ?: rawHost ?: "Router Client"
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return clients
    }
}

class CompanionRouterClientSource(private val companionIp: String) : RouterClientSource {
    override suspend fun getClients(): List<RouterClient> = withContext(Dispatchers.IO) {
        val targetIp = "192.168.1.150"
        try {
            val localPrefix = companionIp.substringBeforeLast(".")
            val targetPrefix = targetIp.substringBeforeLast(".")
            if (localPrefix.isNotEmpty() && localPrefix == targetPrefix) {
                val address = InetAddress.getByName(targetIp)
                if (address.isReachable(500)) {
                    return@withContext listOf(
                        RouterClient(
                            ipAddress = targetIp,
                            macAddress = "b8:27:eb:11:22:33",
                            hostName = "RaspberryPi-Companion",
                            leaseState = "active",
                            connectionType = "Ethernet"
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }
}
