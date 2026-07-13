package com.example.netprobe.probe

enum class PortStatus {
    OPEN, CLOSED, TIMEOUT
}

data class PortProbeResult(
    val port: Int,
    val status: PortStatus,
    val serviceName: String? = null,
    val description: String? = null
)
