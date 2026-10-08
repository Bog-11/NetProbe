package com.brutiful.netprobe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.WiFiHealthSnapshot
import com.brutiful.netprobe.model.WiFiScanResult
import com.brutiful.netprobe.model.WiFiStabilityTestResult
import com.brutiful.netprobe.model.WiFiThroughputResult
import com.brutiful.netprobe.network.WiFiHealthRepository
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class WiFiHealthUiState(
    val snapshot: WiFiHealthSnapshot = WiFiHealthSnapshot(),
    val stabilityResult: WiFiStabilityTestResult = WiFiStabilityTestResult(),
    val nearbyScanResults: List<WiFiScanResult> = emptyList(),
    val throughputResult: WiFiThroughputResult = WiFiThroughputResult(),
    val isStabilityTesting: Boolean = false,
    val isScanning: Boolean = false,
    val isThroughputTesting: Boolean = false,
    val isPreparingAiPrompt: Boolean = false,
    val generatedAiPrompt: String? = null,
    val promptCopiedEvent: Boolean = false
)

class WiFiHealthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WiFiHealthRepository(application)
    
    private val _uiState = MutableStateFlow(WiFiHealthUiState())
    val uiState: StateFlow<WiFiHealthUiState> = _uiState.asStateFlow()

    private var stabilityTestJob: Job? = null
    private var aiPromptPrepJob: Job? = null

    init {
        refreshSnapshot()
        startObserving()
    }

    fun prepareAiPrompt() {
        if (_uiState.value.isPreparingAiPrompt) return

        aiPromptPrepJob?.cancel()
        aiPromptPrepJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreparingAiPrompt = true) }

            val needStabilityTest = _uiState.value.stabilityResult.probes.isEmpty() && !_uiState.value.isStabilityTesting
            val needWifiScan = _uiState.value.nearbyScanResults.isEmpty() && !_uiState.value.isScanning

            if (needStabilityTest) {
                runStabilityTest()
            }
            if (needWifiScan) {
                startWiFiScan()
            }

            // Wait until any active scans complete
            while (_uiState.value.isStabilityTesting || _uiState.value.isScanning) {
                delay(500)
            }

            val currentState = _uiState.value
            val prompt = buildAiPromptText(
                snapshot = currentState.snapshot,
                stabilityResult = currentState.stabilityResult,
                nearbyScanResults = currentState.nearbyScanResults,
                throughputResult = currentState.throughputResult
            )

            _uiState.update {
                it.copy(
                    isPreparingAiPrompt = false,
                    generatedAiPrompt = prompt,
                    promptCopiedEvent = true
                )
            }
        }
    }

    fun consumePromptCopiedEvent() {
        _uiState.update { it.copy(promptCopiedEvent = false) }
    }

    private fun buildAiPromptText(
        snapshot: WiFiHealthSnapshot,
        stabilityResult: WiFiStabilityTestResult,
        nearbyScanResults: List<WiFiScanResult>,
        throughputResult: WiFiThroughputResult
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("You are an expert network engineer and Wi-Fi network diagnostic assistant.")
        sb.appendLine("Below are the comprehensive diagnostic scan results captured from the NetProbe Android app on my local Wi-Fi connection.")
        sb.appendLine()
        sb.appendLine("### DIAGNOSTIC QUESTION & INSTRUCTIONS")
        sb.appendLine("Based on the network diagnostic scan results below:")
        sb.appendLine("1. What are the potential connectivity problems or bottlenecks affecting my network, if any?")
        sb.appendLine("2. Why are these specific issues occurring (explain in detail based on the RSSI, latency, jitter, packet loss, channel overlap, link speeds, or DNS metrics)?")
        sb.appendLine("3. Provide a comprehensive, moderately easy-to-understand analysis that does not compromise on technical details useful for troubleshooting.")
        sb.appendLine("4. Provide step-by-step actionable recommendations to troubleshoot and resolve any detected problems.")
        sb.appendLine()
        sb.appendLine("==================================================")
        sb.appendLine("                  SCAN RESULTS                    ")
        sb.appendLine("==================================================")
        sb.appendLine()

        // 1. Connection Snapshot
        sb.appendLine("--- 1. CURRENT WI-FI CONNECTION SNAPSHOT ---")
        sb.appendLine("• SSID: ${snapshot.ssid ?: "Unknown / Hidden"}")
        sb.appendLine("• BSSID: ${snapshot.bssid ?: "Unknown"}")
        sb.appendLine("• Signal Strength (RSSI): ${snapshot.rssi?.let { "$it dBm" } ?: "N/A"}")
        sb.appendLine("• Frequency / Band: ${snapshot.frequencyMhz?.let { "$it MHz" } ?: "N/A"} (${snapshot.band ?: "N/A"})")
        sb.appendLine("• Channel: ${snapshot.channel ?: "N/A"}")
        sb.appendLine("• Link Speeds: Tx ${snapshot.txLinkSpeedMbps ?: "N/A"} Mbps / Rx ${snapshot.rxLinkSpeedMbps ?: "N/A"} Mbps")
        sb.appendLine("• Local IP: ${snapshot.localIp ?: "N/A"}${snapshot.prefixLength?.let { "/$it" } ?: ""}")
        sb.appendLine("• Gateway IP: ${snapshot.gateway ?: "N/A"}")
        sb.appendLine("• DNS Servers: ${snapshot.dnsServers.joinToString(", ").ifEmpty { "N/A" }}")
        if (snapshot.unavailableReasons.isNotEmpty()) {
            sb.appendLine("• Data Limitations / Notes: ${snapshot.unavailableReasons.joinToString("; ")}")
        }
        sb.appendLine()

        // 2. 60-Second Stability Test
        sb.appendLine("--- 2. 60-SECOND STABILITY TEST RESULTS ---")
        if (stabilityResult.samples.isEmpty() && stabilityResult.probes.isEmpty()) {
            sb.appendLine("Status: Test not executed or no samples captured.")
        } else {
            val classificationText = "${stabilityResult.primaryClassification.displayName} (Confidence: ${stabilityResult.confidence})"
            sb.appendLine("• Health Classification: $classificationText")
            if (stabilityResult.primaryExplanation.isNotEmpty()) {
                sb.appendLine("• Explanation: ${stabilityResult.primaryExplanation}")
            }
            if (stabilityResult.secondaryObservations.isNotEmpty()) {
                sb.appendLine("• Secondary Observations: ${stabilityResult.secondaryObservations.joinToString("; ")}")
            }
            
            stabilityResult.speedTestResult?.let { speed ->
                sb.appendLine("• Internet Speed & Latency (Overview Test): Download ${String.format(Locale.US, "%.2f", speed.downloadSpeedMbps)} Mbps / Upload ${String.format(Locale.US, "%.2f", speed.uploadSpeedMbps)} Mbps | Latency: ${speed.latencyMs} ms | Jitter: ${speed.jitterMs} ms")
            }

            sb.appendLine("• Overview Network Details: Transport: ${stabilityResult.transport ?: "Wi-Fi"} | Metered: ${if (stabilityResult.isMetered) "Yes" else "No"}${stabilityResult.interfaceName?.let { " | Interface: $it" } ?: ""}${stabilityResult.channelWidthLabel?.let { " | Channel Width: $it" } ?: ""}${if (stabilityResult.isVpnConnected) " | VPN: Active (${stabilityResult.vpnInterface ?: "VPN"})" else ""}")

            stabilityResult.rssiStats?.let { rssi ->
                sb.appendLine("• RSSI Stats: Category: ${rssi.category.label} (${rssi.category.rangeDescription}) | Avg: ${String.format(Locale.US, "%.1f", rssi.avgRssi)} dBm (Min: ${rssi.minRssi}, Max: ${rssi.maxRssi}, StdDev: ${String.format(Locale.US, "%.2f", rssi.stdDevRssi)})")
            }

            stabilityResult.linkRateStats?.let { rate ->
                sb.appendLine("• Negotiated Wi-Fi Link Rate: Tx Avg ${String.format(Locale.US, "%.1f", rate.avgTxMbps)} Mbps (Min: ${rate.minTxMbps}, Max: ${rate.maxTxMbps}) | Rx Avg ${String.format(Locale.US, "%.1f", rate.avgRxMbps)} Mbps (Min: ${rate.minRxMbps}, Max: ${rate.maxRxMbps})")
                sb.appendLine("  (Note: ${rate.disclaimer})")
            }

            if (stabilityResult.dnsSummary.totalQueries > 0) {
                val dns = stabilityResult.dnsSummary
                sb.appendLine("• Real DNS Query Summary: ${dns.successfulQueries}/${dns.totalQueries} successful (${String.format(Locale.US, "%.1f", dns.failureRatePercent)}% failure rate, Avg Lookup Time: ${dns.avgResponseTimeMs.toInt()} ms, RCODEs: ${dns.rcodeCounts})")
            }

            if (stabilityResult.httpsProbes.isNotEmpty()) {
                val totalH = stabilityResult.httpsProbes.size
                val succH = stabilityResult.httpsProbes.count { it.isSuccess }
                val captiveH = stabilityResult.httpsProbes.count { it.isCaptivePortalDetected }
                sb.appendLine("• HTTPS Probe Summary: $succH/$totalH successful | Captive Portals Detected: $captiveH")
            }

            if (stabilityResult.icmpProbes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("ICMP Reachability Breakdown:")
                stabilityResult.icmpProbes.forEach { probe ->
                    val rateLimitNote = if (probe.isSuspectedRateLimited) " [Note: Suspected target ICMP rate limiting]" else ""
                    sb.appendLine("  [${probe.targetName} -> ${probe.targetAddress}]$rateLimitNote")
                    sb.appendLine("    - Packet Loss: ${String.format(Locale.US, "%.1f", probe.packetLossPercent)}%")
                    sb.appendLine("    - Latency (avg/med): ${probe.medianLatencyMs.toInt()} ms (Min: ${probe.minLatencyMs.toInt()} ms, Max: ${probe.maxLatencyMs.toInt()} ms)")
                    sb.appendLine("    - Jitter: ${probe.jitterMs.toInt()} ms")
                }
            }

            if (stabilityResult.possibleCauses.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("Possible Causes:")
                stabilityResult.possibleCauses.forEach { cause ->
                    sb.appendLine("  • $cause")
                }
            }

            if (stabilityResult.recommendedActions.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("Recommended Actions:")
                stabilityResult.recommendedActions.forEach { action ->
                    sb.appendLine("  • $action")
                }
            }
        }
        sb.appendLine()

        // 3. Nearby Wi-Fi Networks & Channel Congestion
        sb.appendLine("--- 3. NEARBY WI-FI NETWORKS SCAN ---")
        if (nearbyScanResults.isEmpty()) {
            sb.appendLine("No nearby Wi-Fi networks found or scan unavailable.")
        } else {
            sb.appendLine("Total Nearby Access Points Detected: ${nearbyScanResults.size}")
            val grouped = nearbyScanResults.groupBy { it.band }
            grouped.keys.sorted().forEach { band ->
                val aps = grouped[band] ?: emptyList()
                sb.appendLine()
                sb.appendLine("  Band $band (${aps.size} networks):")
                aps.sortedByDescending { it.rssi }.forEach { ap ->
                    val connectedTag = if (ap.isCurrent) " [CURRENTLY CONNECTED]" else ""
                    val ssidName = ap.ssid.ifBlank { "(Hidden SSID)" }
                    val widthInfo = ap.channelWidth?.let { " ($it)" } ?: ""
                    sb.appendLine("    • $ssidName$connectedTag | BSSID: ${ap.bssid} | Signal: ${ap.rssi} dBm | CH ${ap.channel} (${ap.frequencyMhz} MHz)$widthInfo | Security: ${ap.capabilities}")
                }
                if (band == "2.4 GHz") {
                    val overlapping = aps.filter { it.rssi > -70 && it.channel !in listOf(1, 6, 11) }
                    if (overlapping.size > 2) {
                        sb.appendLine("    ⚠️ CONGESTION WARNING: ${overlapping.size} networks detected on non-standard overlapping 2.4 GHz channels (not 1, 6, 11).")
                    }
                }
            }
        }
        sb.appendLine()

        // 4. Local Throughput Test (if available)
        if (throughputResult.throughputMbps > 0.0 || throughputResult.errorMessage != null) {
            sb.appendLine("--- 4. LOCAL THROUGHPUT TEST ---")
            if (throughputResult.errorMessage != null) {
                sb.appendLine("• Error: ${throughputResult.errorMessage}")
            } else {
                sb.appendLine("• Measured Throughput: ${String.format(Locale.US, "%.2f", throughputResult.throughputMbps)} Mbps")
            }
            sb.appendLine()
        }

        sb.appendLine("==================================================")
        sb.appendLine("Please analyze the above data and answer the diagnostic questions.")

        return sb.toString()
    }

    fun runStabilityTest() {
        if (_uiState.value.isStabilityTesting) return
        
        stabilityTestJob?.cancel()
        stabilityTestJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(
                isStabilityTesting = true,
                stabilityResult = WiFiStabilityTestResult(isRunning = true)
            ) }
            
            try {
                val result = repository.runStabilityTest(
                    onProgress = { progress ->
                        _uiState.update { it.copy(stabilityResult = it.stabilityResult.copy(progress = progress)) }
                    },
                    onSample = { sample ->
                        _uiState.update { it.copy(stabilityResult = it.stabilityResult.copy(samples = it.stabilityResult.samples + sample)) }
                    }
                )
                _uiState.update { it.copy(stabilityResult = result) }
            } catch (e: Exception) {
                // Handle cancellation or error
            } finally {
                _uiState.update { it.copy(isStabilityTesting = false) }
            }
        }
    }

    fun cancelStabilityTest() {
        stabilityTestJob?.cancel()
        aiPromptPrepJob?.cancel()
        _uiState.update { it.copy(isStabilityTesting = false, isPreparingAiPrompt = false) }
    }

    fun startWiFiScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                // Use a timeout to ensure we don't get stuck in "Scanning..." state forever.
                // We use first() to complete the operation as soon as we get the first set of results.
                val results = withTimeout(10000) {
                    repository.scanNearbyWiFi().first()
                }
                _uiState.update { it.copy(nearbyScanResults = results, isScanning = false) }
            } catch (e: Exception) {
                // On timeout or error, ensure we stop the loading state
                _uiState.update { it.copy(isScanning = false) }
                // Fallback to whatever current results are available
                val fallback = repository.getCurrentWiFiScanResults()
                if (fallback.isNotEmpty()) {
                    _uiState.update { it.copy(nearbyScanResults = fallback) }
                }
            }
        }
    }

    private var throughputJob: Job? = null

    fun runThroughputTest(host: String, port: Int, isUdp: Boolean, duration: Int) {
        if (_uiState.value.isThroughputTesting) return
        
        if (!NetworkUtils.isValidIp(host)) {
            _uiState.update { it.copy(
                throughputResult = WiFiThroughputResult(
                    errorMessage = "Invalid Server IP address. Please enter a valid IPv4 or IPv6 address.",
                    isRunning = false
                )
            ) }
            return
        }

        throughputJob?.cancel()
        throughputJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(
                isThroughputTesting = true,
                throughputResult = WiFiThroughputResult(isRunning = true)
            ) }
            
            val result = repository.runThroughputTest(
                host = host,
                port = port,
                isUdp = isUdp,
                durationSeconds = duration,
                onProgress = { progress ->
                    _uiState.update { it.copy(throughputResult = it.throughputResult.copy(progress = progress)) }
                }
            )
            _uiState.update { it.copy(throughputResult = result, isThroughputTesting = false) }
        }
    }

    fun cancelThroughputTest() {
        throughputJob?.cancel()
        _uiState.update { it.copy(isThroughputTesting = false) }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            val snapshot = repository.getWiFiHealthSnapshot()
            _uiState.update { it.copy(snapshot = snapshot) }
        }
    }

    private fun startObserving() {
        viewModelScope.launch {
            repository.observeWiFiHealth().collect { snapshot ->
                _uiState.update { it.copy(snapshot = snapshot) }
            }
        }
    }
}
