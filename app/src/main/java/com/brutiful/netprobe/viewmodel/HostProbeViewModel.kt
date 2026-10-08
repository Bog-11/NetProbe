package com.brutiful.netprobe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.network.HistoryDatabase
import com.brutiful.netprobe.network.ProbeHelpers
import com.brutiful.netprobe.network.TracerouteEnricher
import com.brutiful.netprobe.network.WhoisClient
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.util.Scanner

class HostProbeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HostProbeUiState())
    val uiState: StateFlow<HostProbeUiState> = _uiState.asStateFlow()

    private var probeJob: Job? = null
    private val database = HistoryDatabase.getDatabase(application)
    private var dbPortInfoMap: Map<Int, PortInfo> = emptyMap()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            database.portInfoDao().getAllFlow().collect { ports ->
                dbPortInfoMap = ports.associateBy { it.port }
            }
        }
    }

    fun updateIpAddress(ip: String) {
        _uiState.update { 
            if (it.ipAddress != ip) {
                it.copy(ipAddress = ip, deviceLabel = null)
            } else {
                it.copy(ipAddress = ip)
            }
        }
    }

    fun updateDeviceLabel(label: String?) {
        _uiState.update { it.copy(deviceLabel = label) }
    }

    fun setTargetPort(port: Int?) {
        _uiState.update { it.copy(targetPort = port) }
    }

    fun toggleWhois() {
        _uiState.update { it.copy(isWhoisExpanded = !it.isWhoisExpanded) }
    }

    fun toggleTraceroute() {
        val current = _uiState.value
        if (current.tracerouteResult == null && !current.isTracerouteLoading) {
            performTraceroute(current.lastProbedHost ?: current.ipAddress)
        }
        _uiState.update { it.copy(isTracerouteExpanded = !it.isTracerouteExpanded) }
    }

    private fun performTraceroute(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(
                isTracerouteLoading = true, 
                tracerouteResult = "Traceroute to $host (max 30 hops):", 
                tracerouteHops = emptyList(),
                tracerouteProgress = 0f
            ) }
            try {
                val targetAddress = try {
                    InetAddress.getByName(host).hostAddress
                } catch (e: Exception) {
                    host
                }

                val maxHops = 30
                for (ttl in 1..maxHops) {
                    if (!isActive) break
                    
                    _uiState.update { it.copy(tracerouteProgress = ttl.toFloat() / maxHops.toFloat()) }

                    val process = Runtime.getRuntime().exec("ping -c 1 -t $ttl $host")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    var hopIp: String? = null
                    var time: String? = null
                    
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { l ->
                            if (l.contains("From ")) {
                                hopIp = l.substringAfter("From ").substringBefore(" ").substringBefore(":")
                            } else if (l.contains("bytes from ")) {
                                hopIp = l.substringAfter("bytes from ").substringBefore(" ").substringBefore(":")
                                if (l.contains("time=")) {
                                    time = l.substringAfter("time=").substringBefore(" ms") + "ms"
                                }
                            }
                        }
                    }
                    
                    val currentHop = TracerouteHop(ttl, hopIp, time)
                    _uiState.update { it.copy(tracerouteHops = it.tracerouteHops + currentHop) }

                    if (hopIp != null) {
                        launch {
                            val enrichment = TracerouteEnricher.enrich(hopIp!!)
                            _uiState.update { state ->
                                val updatedHops = state.tracerouteHops.map {
                                    if (it.ttl == ttl) it.copy(enrichment = enrichment) else it
                                }
                                state.copy(tracerouteHops = updatedHops)
                            }
                        }
                    }
                    
                    // Stop if we reached the target
                    if (hopIp != null) {
                        try {
                            val hopAddress = InetAddress.getByName(hopIp).hostAddress
                            if (hopAddress == targetAddress) break
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(tracerouteResult = (it.tracerouteResult ?: "") + "\nError: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isTracerouteLoading = false) }
            }
        }
    }

    fun startAggressiveProbe() {
        val target = _uiState.value.ipAddress
        if (target.isBlank()) return

        if (!NetworkUtils.isValidIpOrDomain(target)) {
            _uiState.update { it.copy(errorMessage = "Invalid Target: Please enter a valid IP address or domain name.") }
            return
        }

        performProbe(target, exhaustive = true)
    }

    fun startQuickProbe() {
        val target = _uiState.value.ipAddress
        if (target.isBlank()) return

        if (!NetworkUtils.isValidIpOrDomain(target)) {
            _uiState.update { it.copy(errorMessage = "Invalid Target: Please enter a valid IP address or domain name.") }
            return
        }

        performProbe(target, exhaustive = false)
    }

    private fun performProbe(target: String, exhaustive: Boolean) {
        probeJob?.cancel()
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            val portsToScan = if (exhaustive) (1..65535).toList() else {
                if (dbPortInfoMap.isEmpty()) {
                    PortData.initialPorts.map { it.port }
                } else {
                    dbPortInfoMap.keys.toList()
                }
            }
            _uiState.update { 
                it.copy(
                    isProbing = true, 
                    isAggressive = exhaustive,
                    progress = 0f, 
                    scannedPorts = 0,
                    totalPorts = portsToScan.size,
                    currentPhase = if (exhaustive) "Exhaustive Probe" else "Quick Identity Probe",
                    reachablePorts = emptyList(), 
                    errorMessage = null,
                    lastProbedHost = target,
                    pingMs = null,
                    whoisReport = null,
                    tracerouteResult = null,
                    tracerouteHops = emptyList(),
                    tracerouteProgress = 0f
                ) 
            }

            try {
                // PHASE 1: Reachability & Quick ID
                _uiState.update { it.copy(currentPhase = "Identity Discovery") }
                val ping = runPing(target)
                _uiState.update { it.copy(pingMs = ping) }

                val netbios = ProbeHelpers.probeNetBios(target, 800)
                val snmp = ProbeHelpers.probeSnmp(target, 1000)
                
                // PHASE 1.5: WHOIS (Background)
                launch {
                    val report = WhoisClient.fetchReport(target)
                    _uiState.update { it.copy(whoisReport = report) }
                }
                
                // PHASE 2: Port Scan
                _uiState.update { it.copy(currentPhase = if (exhaustive) "Exhaustive TCP Scan" else "Common Port Scan") }
                val results = mutableListOf<PortProbeResult>()
                val batchSize = if (exhaustive) 100 else 20
                
                portsToScan.chunked(batchSize).forEachIndexed { index, batch ->
                    val batchResults = batch.map { port ->
                        async {
                            if (ProbeHelpers.isPortOpen(target, port, 300)) port else null
                        }
                    }.awaitAll().filterNotNull()
                    
                    if (batchResults.isNotEmpty()) {
                        batchResults.forEach { port ->
                            _uiState.update { it.copy(currentService = "Fingerprinting port $port") }
                            val fingerprintedPort = runExhaustiveFingerprint(target, port)
                            results.add(fingerprintedPort)
                        }
                        _uiState.update { it.copy(reachablePorts = results.toList().sortedBy { it.port }) }
                    }
                    
                    val scanned = (index + 1) * batchSize
                    _uiState.update { it.copy(
                        scannedPorts = scanned.coerceAtMost(portsToScan.size),
                        progress = scanned.toFloat() / portsToScan.size.toFloat()
                    ) }
                }

                // PHASE 3: OS Fingerprinting
                _uiState.update { it.copy(currentPhase = "OS Fingerprinting", currentService = "Analyzing heuristics") }
                val osGuess = guessOS(target, results)
                
                val identityInfo = buildString {
                    append("Scan Type: ${if (exhaustive) "Exhaustive" else "Quick"}\n")
                    var infoFound = false
                    if (osGuess != null) {
                        append("Probable OS: $osGuess\n")
                        infoFound = true
                    }
                    if (netbios != null) {
                        append("NetBIOS Name: ${netbios.metadata["Name"]}\n")
                        infoFound = true
                    }
                    if (snmp != null) {
                        append("SNMP Desc: ${snmp.metadata["sysDescr"]}\n")
                        infoFound = true
                    }
                    
                    if (!infoFound) {
                        append("\nNo specific identity information could be discovered for this host via OS fingerprinting, NetBIOS, or SNMP.")
                    }
                }
                
                _uiState.update { it.copy(
                    errorMessage = identityInfo,
                    currentPhase = "Complete",
                    currentService = null
                ) }

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Probe failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isProbing = false, progress = 1f) }
            }
        }
    }

    private suspend fun runExhaustiveFingerprint(ip: String, port: Int): PortProbeResult {
        val service = dbPortInfoMap[port]
        val metadata = mutableMapOf<String, String>()
        var foundProtocol: String? = service?.name

        // Exhaustive per-port identification pipeline
        when (port) {
            80, 443, 8080, 8443, 9443, 5000, 5001, 8123, 8096 -> {
                ProbeHelpers.probeHttp(ip, port, 2000)?.let { res ->
                    metadata.putAll(res.metadata)
                    foundProtocol = "HTTP/HTTPS"
                }
            }
            21 -> ProbeHelpers.probeBanner(ip, port, "FTP", 1500)?.let { metadata.putAll(it.metadata) }
            22 -> ProbeHelpers.probeBanner(ip, port, "SSH", 1500)?.let { metadata.putAll(it.metadata) }
            23 -> ProbeHelpers.probeBanner(ip, port, "Telnet", 1500)?.let { metadata.putAll(it.metadata) }
            554, 8554 -> ProbeHelpers.probeRtsp(ip, port, 1500)?.let { metadata.putAll(it.metadata) }
            5060, 5061 -> ProbeHelpers.probeSip(ip, port, 1500)?.let { metadata.putAll(it.metadata) }
        }

        val description = if (metadata.isNotEmpty()) {
            metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        } else service?.description

        return PortProbeResult(
            port = port,
            status = PortStatus.OPEN,
            serviceName = foundProtocol ?: "Unknown",
            description = description ?: "Service detected, no banner found."
        )
    }

    private fun guessOS(ip: String, ports: List<PortProbeResult>): String? {
        // Crude TTL-based OS guessing as start of exhaustive heuristics
        // Windows typically 128, Linux/Unix 64, Network devices 255
        // This would be expanded with TCP Options analysis in a real low-level scanner
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 $ip")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val ttlMatch = Regex("ttl=(\\d+)", RegexOption.IGNORE_CASE).find(output)
            val ttl = ttlMatch?.groupValues?.get(1)?.toInt() ?: return null
            
            when {
                ttl <= 64 -> "Linux/Unix/Android"
                ttl <= 128 -> "Windows"
                else -> "Network Infrastructure / Embedded"
            }
        } catch (_: Exception) { null }
    }

    fun cancelAggressiveProbe() {
        probeJob?.cancel()
        _uiState.update { it.copy(isProbing = false, progress = 0f, currentPhase = "Cancelled") }
    }

    private fun runPing(host: String): Long? {
        return try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            if (address.isReachable(2000)) {
                System.currentTimeMillis() - startTime
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
