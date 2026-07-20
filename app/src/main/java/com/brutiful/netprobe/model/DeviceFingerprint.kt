package com.brutiful.netprobe.model

data class DeviceFingerprint(
    val source: FingerprintSource,
    val vendor: String? = null,
    val model: String? = null,
    val deviceType: String? = null,
    val friendlyName: String? = null,
    val hostName: String? = null,
    val confidence: Int = 0,
    val rawMetadata: Map<String, String> = emptyMap()
)

enum class FingerprintSource {
    MAC_OUI,
    SSDP,
    ONVIF,
    MDNS,
    NETBIOS,
    HOSTNAME,
    OPEN_PORTS,
    HTTP,
    HTTP_BANNER,
    SNMP,
    RTSP,
    HEURISTIC
}
