package com.brutiful.netprobe.model

data class WiFiHealthSnapshot(
    val ssid: String? = null,
    val bssid: String? = null,
    val localIp: String? = null,
    val prefixLength: Int? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val band: String? = null,
    val channel: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val securityCapabilities: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val unavailableReasons: List<String> = emptyList()
)

enum class RssiCategory(val label: String, val rangeDescription: String) {
    EXCELLENT("Excellent", ">= -55 dBm: Ideal signal quality"),
    GOOD("Good", "-56 to -65 dBm: Reliable connection"),
    FAIR("Fair", "-66 to -72 dBm: Acceptable signal"),
    WEAK("Weak", "-73 to -80 dBm: Poor signal, prone to latency spikes"),
    VERY_WEAK("Very Weak", "< -80 dBm: Critical signal strength")
}

data class RssiStats(
    val avgRssi: Double = -100.0,
    val minRssi: Int = -100,
    val maxRssi: Int = -100,
    val stdDevRssi: Double = 0.0,
    val category: RssiCategory = RssiCategory.VERY_WEAK
)

data class LinkRateStats(
    val avgTxMbps: Double = 0.0,
    val minTxMbps: Int = 0,
    val maxTxMbps: Int = 0,
    val avgRxMbps: Double = 0.0,
    val minRxMbps: Int = 0,
    val maxRxMbps: Int = 0,
    val label: String = "Negotiated Wi-Fi link rate",
    val disclaimer: String = "Negotiated PHY link rate between device and AP. Actual application throughput may be lower due to protocol overhead, wireless contention, and ISP limits."
)

data class DnsQueryMetric(
    val domain: String,
    val resolverUsed: String,
    val isSuccess: Boolean,
    val rcode: String,
    val responseTimeMs: Long,
    val resolvedAddresses: List<String> = emptyList(),
    val isCacheResistant: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class DnsTestSummary(
    val totalQueries: Int = 0,
    val successfulQueries: Int = 0,
    val failureRatePercent: Double = 0.0,
    val avgResponseTimeMs: Double = 0.0,
    val maxResponseTimeMs: Long = 0L,
    val rcodeCounts: Map<String, Int> = emptyMap(),
    val systemVsPublicComparison: String? = null
)

data class HttpsProbeResult(
    val url: String,
    val statusCode: Int = -1,
    val isSuccess: Boolean = false,
    val isCaptivePortalDetected: Boolean = false,
    val dnsTimeMs: Long = -1L,
    val tcpConnectTimeMs: Long = -1L,
    val tlsHandshakeTimeMs: Long = -1L,
    val ttfbMs: Long = -1L,
    val totalTimeMs: Long = -1L,
    val redirectUrl: String? = null,
    val responseContentType: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class IcmpProbeResult(
    val targetName: String,
    val targetAddress: String,
    val totalSent: Int = 0,
    val successfulReceived: Int = 0,
    val minLatencyMs: Double = 0.0,
    val maxLatencyMs: Double = 0.0,
    val medianLatencyMs: Double = 0.0,
    val packetLossPercent: Double = 0.0,
    val jitterMs: Double = 0.0,
    val isSuspectedRateLimited: Boolean = false
)

data class CorrelationMetrics(
    val rssiVsGatewayLatencyCorrelation: Double = 0.0,
    val rssiVsGatewayLossCorrelation: Double = 0.0,
    val isGatewayDegradationCorrelatedWithRssiDrop: Boolean = false
)

enum class WiFiHealthClassification(val displayName: String) {
    NO_WIFI_CONNECTION("No Wi-Fi Connection"),
    CAPTIVE_PORTAL_OR_RESTRICTED("Possible Captive Portal or Restricted Access"),
    VERY_WEAK_OR_UNSTABLE_WIFI("Very Weak or Unstable Wi-Fi Link"),
    LOCAL_NETWORK_INSTABILITY("Likely Local Network Instability"),
    DNS_ISSUE("Likely DNS Issue"),
    UPSTREAM_CONNECTIVITY_ISSUE("Likely Upstream Connectivity Issue"),
    HEALTHY("Healthy Connection"),
    INCONCLUSIVE("Inconclusive Diagnostics")
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

data class WiFiStabilityTestResult(
    val samples: List<WiFiStabilitySample> = emptyList(),
    val rssiStats: RssiStats? = null,
    val linkRateStats: LinkRateStats? = null,
    val icmpProbes: List<IcmpProbeResult> = emptyList(),
    val dnsSummary: DnsTestSummary = DnsTestSummary(),
    val httpsProbes: List<HttpsProbeResult> = emptyList(),
    val correlation: CorrelationMetrics = CorrelationMetrics(),
    val primaryClassification: WiFiHealthClassification = WiFiHealthClassification.INCONCLUSIVE,
    val confidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val primaryExplanation: String = "",
    val secondaryObservations: List<String> = emptyList(),
    val possibleCauses: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val speedTestResult: SpeedTestResult? = null,
    val transport: String? = null,
    val isValidated: Boolean = true,
    val isMetered: Boolean = false,
    val channelWidthLabel: String? = null,
    val interfaceName: String? = null,
    val isVpnConnected: Boolean = false,
    val vpnInterface: String? = null
) {
    val probes: List<IcmpProbeResult> get() = icmpProbes
    val classification: WiFiHealthClassification get() = primaryClassification
}

data class WiFiStabilitySample(
    val timestamp: Long,
    val rssi: Int,
    val txLinkSpeedMbps: Int,
    val rxLinkSpeedMbps: Int
)

data class WiFiScanResult(
    val ssid: String,
    val bssid: String,
    val frequencyMhz: Int,
    val rssi: Int,
    val channel: Int,
    val band: String,
    val capabilities: String,
    val channelWidth: String?,
    val isCurrent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class WiFiThroughputResult(
    val throughputMbps: Double = 0.0,
    val lossPercent: Double = 0.0,
    val jitterMs: Double = 0.0,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val errorMessage: String? = null
)
