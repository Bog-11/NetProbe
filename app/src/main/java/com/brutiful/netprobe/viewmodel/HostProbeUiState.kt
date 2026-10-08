package com.brutiful.netprobe.viewmodel

import com.brutiful.netprobe.model.PortProbeResult
import com.brutiful.netprobe.model.TracerouteHop
import com.brutiful.netprobe.model.WhoisReport

data class HostProbeUiState(
    val ipAddress: String = "",
    val deviceLabel: String? = null,
    val targetPort: Int? = null,
    val isProbing: Boolean = false,
    val progress: Float = 0f,
    val scannedPorts: Int = 0,
    val totalPorts: Int = 0,
    val currentPhase: String? = null,
    val currentService: String? = null,
    val isAggressive: Boolean = false,
    val scanRange: String = "1-65535 (Exhaustive)",
    val reachablePorts: List<PortProbeResult> = emptyList(),
    val errorMessage: String? = null,
    val lastProbedHost: String? = null,
    val pingMs: Long? = null,
    val whoisReport: WhoisReport? = null,
    val isWhoisExpanded: Boolean = false,
    val tracerouteResult: String? = null,
    val tracerouteHops: List<TracerouteHop> = emptyList(),
    val tracerouteProgress: Float = 0f,
    val isTracerouteLoading: Boolean = false,
    val isTracerouteExpanded: Boolean = false
)
