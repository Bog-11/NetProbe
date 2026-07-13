package com.example.netprobe.model

data class DiscoveredDevice(
    val ipAddress: String,
    val isReachable: Boolean = false,
    val hostName: String? = null,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val modelName: String? = null
)

enum class DeviceType {
    IPHONE, ANDROID, WINDOWS, MAC, LINUX, PRINTER, IOT, UNKNOWN
}
