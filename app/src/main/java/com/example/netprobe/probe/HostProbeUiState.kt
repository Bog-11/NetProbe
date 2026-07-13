package com.example.netprobe.probe

data class HostProbeUiState(
    val ipAddress: String = "",
    val isProbing: Boolean = false,
    val progress: Float = 0f,
    val scanRange: String = "1-1024",
    val reachablePorts: List<PortProbeResult> = emptyList(),
    val errorMessage: String? = null,
    val lastProbedHost: String? = null,
    val pingMs: Long? = null,
    val whoisData: String? = null,
    val isWhoisExpanded: Boolean = false,
    val tracerouteResult: String? = null,
    val isTracerouteLoading: Boolean = false,
    val isTracerouteExpanded: Boolean = false
)
