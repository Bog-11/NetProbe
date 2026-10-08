package com.brutiful.netprobe.model

data class NetworkOverview(
    val isConnected: Boolean = false,
    val transport: String = "Offline",
    val isValidated: Boolean = false,
    val isMetered: Boolean = false,
    val dnsServers: List<String> = emptyList(),
    val localAddresses: List<LocalAddress> = emptyList(),
    val primaryIp: String? = null,
    val interfaceName: String? = null,
    val wifiName: String? = null,
    val wifiPermissionGranted: Boolean = false,
    val speedTestResult: SpeedTestResult? = null,
    val isSpeedTesting: Boolean = false,
    val isSpeedTestExpanded: Boolean = false,
    val isVpnConnected: Boolean = false,
    val vpnInterface: String? = null,
    val radioFrequencyMhz: Int? = null,
    val radioBandLabel: String? = null,
    val channelWidthLabel: String? = null,
    val isBrightnessBoostEnabled: Boolean = true,
    val isDarkMode: Boolean = true
)

data class SpeedTestResult(
    val downloadSpeedMbps: Double,
    val uploadSpeedMbps: Double,
    val latencyMs: Long,
    val jitterMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalAddress(
    val address: String,
    val prefixLength: Int,
    val isVpn: Boolean = false
) {
    fun getNetworkRange(): String {
        val parts = address.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return address
        
        val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = if (prefixLength == 0) 0 else (0xFFFFFFFF shl (32 - prefixLength)).toInt()
        val network = ipInt and mask
        
        val netStr = "${(network ushr 24) and 0xFF}.${(network ushr 16) and 0xFF}.${(network ushr 8) and 0xFF}.${network and 0xFF}"
        return "$netStr/$prefixLength"
    }
}
