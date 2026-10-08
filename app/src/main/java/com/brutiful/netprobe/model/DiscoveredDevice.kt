package com.brutiful.netprobe.model

import java.net.InetAddress

enum class PresenceState {
    CONFIRMED_ACTIVE,
    DISCOVERED_BY_MULTICAST,
    ROUTER_REPORTED,
    PREVIOUSLY_SEEN,
    UNRESPONSIVE_THIS_SCAN,
    NOT_REACHABLE_FROM_PHONE
}

enum class DiscoverySource {
    MDNS, SSDP, WS_DISCOVERY, TCP_CONNECT, ICMP, MANUAL
}

enum class DeviceCategory {
    CAMERA,
    PRINTER,
    MEDIA_STREAMER,
    SMART_HOME,
    NETWORK_EQUIPMENT,
    COMPUTER_MOBILE,
    UNKNOWN_REACHABLE
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

data class DiscoveredService(
    val serviceType: String,
    val name: String,
    val port: Int,
    val txtRecords: Map<String, String> = emptyMap(),
    val hostTarget: String? = null,
    val source: DiscoverySource = DiscoverySource.MDNS
)

data class DiscoveredDevice(
    val ipAddress: InetAddress?,
    val ipv6Addresses: Set<InetAddress> = emptySet(),
    val hostname: String? = null,
    val displayName: String? = null,
    val macAddress: String? = null, // nullable; Android often cannot provide this
    val services: List<DiscoveredService> = emptyList(),
    val openPorts: Set<Int> = emptySet(),
    val sources: Set<DiscoverySource> = emptySet(),
    val category: DeviceCategory = DeviceCategory.UNKNOWN_REACHABLE,
    val confidence: Confidence = Confidence.LOW,
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val notes: List<String> = emptyList(),
    // Metadata fields for UI/Enrichment compatibility
    val vendorName: String? = null,
    val modelName: String? = null,
    val manufacturer: String? = null,
    val presenceState: PresenceState = PresenceState.CONFIRMED_ACTIVE
) {
    val ipString: String
        get() = ipAddress?.hostAddress ?: "Unknown IP"

    fun computedDisplayName(): String {
        val candidateName = displayName
            ?: hostname
            ?: services.firstOrNull { it.name.isNotBlank() }?.name
            ?: modelName
            ?: vendorName
            
        return candidateName?.takeIf { it.isNotBlank() } ?: ipString
    }

    fun evidenceSummary(): String {
        val confidenceText = when (confidence) {
            Confidence.HIGH -> "High confidence"
            Confidence.MEDIUM -> "Medium confidence"
            Confidence.LOW -> "Low confidence"
        }
        val categoryText = when (category) {
            DeviceCategory.CAMERA -> "Possible IP camera"
            DeviceCategory.PRINTER -> "Possible printer"
            DeviceCategory.MEDIA_STREAMER -> "Media streamer"
            DeviceCategory.SMART_HOME -> "Smart home device"
            DeviceCategory.NETWORK_EQUIPMENT -> "Network equipment"
            DeviceCategory.COMPUTER_MOBILE -> "Computer or mobile device"
            DeviceCategory.UNKNOWN_REACHABLE -> "Unknown reachable device"
        }
        val sourceDetails = mutableListOf<String>()
        if (sources.contains(DiscoverySource.WS_DISCOVERY)) sourceDetails.add("WS-Discovery response")
        if (sources.contains(DiscoverySource.MDNS)) sourceDetails.add("mDNS response")
        if (sources.contains(DiscoverySource.SSDP)) sourceDetails.add("SSDP/UPnP description")
        if (openPorts.contains(554)) sourceDetails.add("RTSP port 554 reachable")
        if (openPorts.contains(631)) sourceDetails.add("IPP port 631 reachable")
        if (openPorts.contains(9100)) sourceDetails.add("JetDirect port 9100 reachable")
        if (openPorts.contains(80) || openPorts.contains(443)) sourceDetails.add("HTTP(S) port reachable")
        if (openPorts.contains(445)) sourceDetails.add("SMB port 445 reachable")
        if (sources.contains(DiscoverySource.ICMP)) sourceDetails.add("ICMP Echo reply")

        val detailStr = if (sourceDetails.isNotEmpty()) sourceDetails.joinToString(" + ") else "Reachable via scan"
        return "$categoryText — $confidenceText:\n$detailStr."
    }
}
