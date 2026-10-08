package com.brutiful.netprobe.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs
import kotlin.math.sqrt

class WiFiHealthRepository(private val context: Context) {

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun getWiFiHealthSnapshot(): WiFiHealthSnapshot {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WiFiHealthSnapshot(
                unavailableReasons = listOf("Not connected to Wi-Fi")
            )
        }

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasNearbyPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val canAccessSsid = hasLocationPermission || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasNearbyPermission)

        var ssid: String? = null
        var bssid: String? = null
        var rssi: Int? = null
        var freq: Int? = null
        var txSpeed: Int? = null
        var rxSpeed: Int? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                ssid = NetworkUtils.sanitizeSsid(transportInfo.ssid)
                bssid = transportInfo.bssid
                rssi = transportInfo.rssi
                freq = transportInfo.frequency
                txSpeed = transportInfo.txLinkSpeedMbps.takeIf { it != -1 }
                rxSpeed = transportInfo.rxLinkSpeedMbps.takeIf { it != -1 }
            }
        }

        if (ssid == null || bssid == null || rssi == null) {
            try {
                @Suppress("DEPRECATION")
                val connectionInfo = wifiManager.connectionInfo
                if (connectionInfo != null) {
                    if (ssid == null) ssid = NetworkUtils.sanitizeSsid(connectionInfo.ssid)
                    if (bssid == null) bssid = connectionInfo.bssid
                    if (rssi == null) rssi = connectionInfo.rssi
                    if (freq == null) freq = connectionInfo.frequency
                    if (txSpeed == null) {
                        @Suppress("DEPRECATION")
                        val speed = connectionInfo.linkSpeed
                        txSpeed = if (speed != -1) speed else null
                    }
                }
            } catch (_: SecurityException) {}
        }

        val localIp = linkProperties?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val unavailableReasons = mutableListOf<String>()
        if (!canAccessSsid) {
            unavailableReasons.add("Location/Nearby permission missing (SSID/BSSID hidden)")
        }
        if (ssid == null) unavailableReasons.add("SSID unavailable")
        if (bssid == null) unavailableReasons.add("BSSID unavailable")

        return WiFiHealthSnapshot(
            ssid = ssid,
            bssid = bssid,
            localIp = localIp?.address?.hostAddress,
            prefixLength = localIp?.prefixLength,
            gateway = gateway,
            dnsServers = dnsServers,
            rssi = rssi,
            frequencyMhz = freq,
            band = freq?.let { NetworkUtils.getWifiBandLabel(it) },
            channel = freq?.let { NetworkUtils.getWifiChannelFromFrequency(it) },
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            timestamp = System.currentTimeMillis(),
            unavailableReasons = unavailableReasons
        )
    }

    fun observeWiFiHealth(): Flow<WiFiHealthSnapshot> = flow {
        while (true) {
            emit(getWiFiHealthSnapshot())
            delay(2000)
        }
    }

    fun getCurrentWiFiScanResults(): List<WiFiScanResult> {
        return try {
            val results = wifiManager.scanResults
            val currentBssid = getWiFiHealthSnapshot().bssid
            results
                .sortedByDescending { it.level }
                .map { result ->
                    WiFiScanResult(
                        ssid = NetworkUtils.sanitizeSsid(result.SSID) ?: "Hidden Network",
                        bssid = result.BSSID,
                        frequencyMhz = result.frequency,
                        rssi = result.level,
                        channel = NetworkUtils.getWifiChannelFromFrequency(result.frequency) ?: 0,
                        band = NetworkUtils.getWifiBandLabel(result.frequency),
                        capabilities = result.capabilities,
                        channelWidth = NetworkUtils.getWifiChannelWidthLabel(result.channelWidth),
                        isCurrent = result.BSSID.equals(currentBssid, ignoreCase = true)
                    )
                }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun scanNearbyWiFi(): Flow<List<WiFiScanResult>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val results = getCurrentWiFiScanResults()
                trySend(results)
            }
        }
        
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        var success = false
        try {
            @Suppress("DEPRECATION")
            success = wifiManager.startScan()
        } catch (_: SecurityException) {}

        if (!success) {
            // If startScan failed (throttled), send current results and close
            trySend(getCurrentWiFiScanResults())
            // We don't close immediately in case a scan was already in progress
            // and might finish soon, but we ensure we are responsive.
        }
        
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    suspend fun runStabilityTest(
        onProgress: (Float) -> Unit,
        onSample: (WiFiStabilitySample) -> Unit
    ): WiFiStabilityTestResult {
        val initialSnapshot = getWiFiHealthSnapshot()
        val isWifiConnected = initialSnapshot.ssid != null && initialSnapshot.ssid != "<unknown ssid>" && initialSnapshot.localIp != null
        
        val gateway = initialSnapshot.gateway
        val targets = mutableListOf<Pair<String, String>>()
        if (gateway != null) targets.add("Gateway" to gateway)
        targets.add("Google DNS" to "8.8.8.8")
        targets.add("Cloudflare DNS" to "1.1.1.1")

        val icmpLatencies = mutableMapOf<String, MutableList<Long>>()
        val samples = mutableListOf<WiFiStabilitySample>()
        val dnsMetrics = mutableListOf<DnsQueryMetric>()
        val httpsMetrics = mutableListOf<HttpsProbeResult>()

        val dnsDomains = listOf("google.com", "cloudflare.com", "wikipedia.org")
        var dnsIndex = 0

        val durationSeconds = 60
        for (i in 1..durationSeconds) {
            val currentSnapshot = getWiFiHealthSnapshot()
            val sample = WiFiStabilitySample(
                timestamp = System.currentTimeMillis(),
                rssi = currentSnapshot.rssi ?: -100,
                txLinkSpeedMbps = currentSnapshot.txLinkSpeedMbps ?: -1,
                rxLinkSpeedMbps = currentSnapshot.rxLinkSpeedMbps ?: -1
            )
            samples.add(sample)
            onSample(sample)

            // ICMP probes (Gateway, Google DNS, Cloudflare DNS)
            targets.forEach { (name, address) ->
                val latency = probeAddress(address)
                icmpLatencies.getOrPut(name) { mutableListOf() }.add(latency ?: -1L)
            }

            // Sub-loop: Every 5 seconds perform real DNS lookup and lightweight HTTPS check
            if (i % 5 == 0) {
                val isCacheResistant = (i % 20 == 0)
                val domainToQuery = if (isCacheResistant) {
                    "probe-${System.currentTimeMillis()}-${(1000..9999).random()}.check.netprobe.internal"
                } else {
                    dnsDomains[dnsIndex % dnsDomains.size].also { dnsIndex++ }
                }
                
                val dnsMetric = probeDnsQuery(domainToQuery, isCacheResistant)
                dnsMetrics.add(dnsMetric)

                val httpsMetric = probeHttpsDetailed("connectivitycheck.gstatic.com")
                httpsMetrics.add(httpsMetric)
            }

            onProgress(i.toFloat() / durationSeconds.toFloat())
            delay(1000)
        }

        // Measure Internet Speed & Gather Network Overview metrics as part of 60-second test
        val speedTest = SpeedTestRunner.runSpeedTest()

        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi‑Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Unknown"
        }
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = connectivityManager.isActiveNetworkMetered
        val interfaceName = linkProps?.interfaceName

        val currentScanResult = getCurrentWiFiScanResults().firstOrNull { it.isCurrent }
        val channelWidthLabel = currentScanResult?.channelWidth

        val vpnNetwork = connectivityManager.allNetworks.find { n ->
            connectivityManager.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val isVpnConnected = vpnNetwork != null
        val vpnInterface = vpnNetwork?.let { connectivityManager.getLinkProperties(it)?.interfaceName }

        // Calculate stats
        val rssiStats = calculateRssiStats(samples)
        val linkRateStats = calculateLinkRateStats(samples)
        val icmpProbeResults = calculateIcmpResults(targets, icmpLatencies, httpsMetrics, dnsMetrics)
        val dnsSummary = summarizeDnsMetrics(dnsMetrics)
        val correlation = calculateCorrelation(samples, icmpLatencies["Gateway"] ?: emptyList())

        return classifyStability(
            isWifiConnected = isWifiConnected,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbeResults,
            dnsSummary = dnsSummary,
            httpsProbes = httpsMetrics,
            correlation = correlation,
            speedTestResult = speedTest,
            transport = transport,
            isValidated = isValidated,
            isMetered = isMetered,
            channelWidthLabel = channelWidthLabel,
            interfaceName = interfaceName,
            isVpnConnected = isVpnConnected,
            vpnInterface = vpnInterface
        )
    }

    private fun probeAddress(address: String): Long? {
        return try {
            val startTime = System.nanoTime()
            val proc = Runtime.getRuntime().exec("ping -c 1 -W 1 $address")
            val exitCode = proc.waitFor()
            if (exitCode == 0) (System.nanoTime() - startTime) / 1_000_000 else null
        } catch (_: Exception) { null }
    }

    private fun probeDnsQuery(domain: String, isCacheResistant: Boolean): DnsQueryMetric {
        val startTime = System.nanoTime()
        return try {
            val addresses = InetAddress.getAllByName(domain)
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            DnsQueryMetric(
                domain = domain,
                resolverUsed = "System Resolver",
                isSuccess = addresses.isNotEmpty(),
                rcode = "NOERROR",
                responseTimeMs = durationMs,
                resolvedAddresses = addresses.map { it.hostAddress ?: "" },
                isCacheResistant = isCacheResistant
            )
        } catch (e: UnknownHostException) {
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            DnsQueryMetric(
                domain = domain,
                resolverUsed = "System Resolver",
                isSuccess = false,
                rcode = if (e.message?.contains("timed out", ignoreCase = true) == true) "TIMEOUT" else "UNKNOWN_HOST",
                responseTimeMs = durationMs,
                resolvedAddresses = emptyList(),
                isCacheResistant = isCacheResistant
            )
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            DnsQueryMetric(
                domain = domain,
                resolverUsed = "System Resolver",
                isSuccess = false,
                rcode = "ERROR",
                responseTimeMs = durationMs,
                resolvedAddresses = emptyList(),
                isCacheResistant = isCacheResistant
            )
        }
    }

    private fun probeHttpsDetailed(host: String): HttpsProbeResult {
        val targetUrl = "https://$host/generate_204"
        val startTotal = System.currentTimeMillis()
        var dnsTimeMs = -1L
        var tcpConnectTimeMs = -1L
        var tlsHandshakeTimeMs = -1L
        var ttfbMs = -1L
        var totalTimeMs = -1L

        return try {
            // Stage 1: DNS Lookup
            val dnsStart = System.currentTimeMillis()
            val inetAddr = InetAddress.getByName(host)
            dnsTimeMs = System.currentTimeMillis() - dnsStart

            // Stage 2: TCP Socket Connect
            val socket = Socket()
            val tcpStart = System.currentTimeMillis()
            socket.connect(InetSocketAddress(inetAddr, 443), 2500)
            tcpConnectTimeMs = System.currentTimeMillis() - tcpStart

            // Stage 3: TLS Handshake
            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val tlsStart = System.currentTimeMillis()
            val sslSocket = sslSocketFactory.createSocket(socket, host, 443, true) as SSLSocket
            sslSocket.startHandshake()
            tlsHandshakeTimeMs = System.currentTimeMillis() - tlsStart

            // Stage 4: HTTP Request / TTFB
            val url = URL(targetUrl)
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.instanceFollowRedirects = false
            
            val reqStart = System.currentTimeMillis()
            conn.connect()
            val responseCode = conn.responseCode
            ttfbMs = System.currentTimeMillis() - reqStart
            totalTimeMs = System.currentTimeMillis() - startTotal

            val contentType = conn.contentType ?: ""
            val redirectLocation = conn.getHeaderField("Location")

            val isRedirect = responseCode in 300..399 || redirectLocation != null
            val isHtmlResponse = contentType.contains("text/html", ignoreCase = true)
            val isCaptivePortal = isRedirect || (responseCode == 200 && isHtmlResponse)

            val isSuccess = responseCode == 204 || (responseCode in 200..399 && !isCaptivePortal)

            HttpsProbeResult(
                url = targetUrl,
                statusCode = responseCode,
                isSuccess = isSuccess,
                isCaptivePortalDetected = isCaptivePortal,
                dnsTimeMs = dnsTimeMs,
                tcpConnectTimeMs = tcpConnectTimeMs,
                tlsHandshakeTimeMs = tlsHandshakeTimeMs,
                ttfbMs = ttfbMs,
                totalTimeMs = totalTimeMs,
                redirectUrl = redirectLocation,
                responseContentType = contentType
            )
        } catch (e: Exception) {
            totalTimeMs = System.currentTimeMillis() - startTotal
            HttpsProbeResult(
                url = targetUrl,
                statusCode = -1,
                isSuccess = false,
                isCaptivePortalDetected = e.message?.contains("certificate", ignoreCase = true) == true,
                dnsTimeMs = dnsTimeMs,
                tcpConnectTimeMs = tcpConnectTimeMs,
                tlsHandshakeTimeMs = tlsHandshakeTimeMs,
                ttfbMs = ttfbMs,
                totalTimeMs = totalTimeMs,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun calculateRssiStats(samples: List<WiFiStabilitySample>): RssiStats {
        val validRssi = samples.map { it.rssi }.filter { it != 0 && it != -100 }
        if (validRssi.isEmpty()) {
            return RssiStats(
                avgRssi = -100.0,
                minRssi = -100,
                maxRssi = -100,
                stdDevRssi = 0.0,
                category = RssiCategory.VERY_WEAK
            )
        }
        val avg = validRssi.average()
        val min = validRssi.minOrNull() ?: -100
        val max = validRssi.maxOrNull() ?: -100
        val variance = validRssi.map { (it - avg) * (it - avg) }.average()
        val stdDev = sqrt(variance)

        val category = when {
            avg >= -55 -> RssiCategory.EXCELLENT
            avg >= -65 -> RssiCategory.GOOD
            avg >= -72 -> RssiCategory.FAIR
            avg >= -80 -> RssiCategory.WEAK
            else -> RssiCategory.VERY_WEAK
        }

        return RssiStats(
            avgRssi = avg,
            minRssi = min,
            maxRssi = max,
            stdDevRssi = stdDev,
            category = category
        )
    }

    private fun calculateLinkRateStats(samples: List<WiFiStabilitySample>): LinkRateStats {
        val txSpeeds = samples.map { it.txLinkSpeedMbps }.filter { it > 0 }
        val rxSpeeds = samples.map { it.rxLinkSpeedMbps }.filter { it > 0 }

        val avgTx = if (txSpeeds.isNotEmpty()) txSpeeds.average() else 0.0
        val minTx = txSpeeds.minOrNull() ?: 0
        val maxTx = txSpeeds.maxOrNull() ?: 0

        val avgRx = if (rxSpeeds.isNotEmpty()) rxSpeeds.average() else 0.0
        val minRx = rxSpeeds.minOrNull() ?: 0
        val maxRx = rxSpeeds.maxOrNull() ?: 0

        return LinkRateStats(
            avgTxMbps = avgTx,
            minTxMbps = minTx,
            maxTxMbps = maxTx,
            avgRxMbps = avgRx,
            minRxMbps = minRx,
            maxRxMbps = maxRx
        )
    }

    private fun calculateIcmpResults(
        targets: List<Pair<String, String>>,
        icmpLatencies: Map<String, List<Long>>,
        httpsProbes: List<HttpsProbeResult>,
        dnsMetrics: List<DnsQueryMetric>
    ): List<IcmpProbeResult> {
        val httpsSuccessRate = if (httpsProbes.isNotEmpty()) httpsProbes.count { it.isSuccess }.toDouble() / httpsProbes.size.toDouble() else 1.0
        val dnsSuccessRate = if (dnsMetrics.isNotEmpty()) dnsMetrics.count { it.isSuccess }.toDouble() / dnsMetrics.size.toDouble() else 1.0

        return targets.map { (name, address) ->
            val latencies = icmpLatencies[name] ?: emptyList()
            val successful = latencies.filter { it > 0 }
            val totalSent = latencies.size
            val successfulReceived = successful.size
            val loss = if (totalSent > 0) (totalSent - successfulReceived).toDouble() / totalSent.toDouble() * 100.0 else 0.0

            val sorted = successful.sorted()
            val median = if (sorted.isNotEmpty()) sorted[sorted.size / 2].toDouble() else 0.0
            val min = if (sorted.isNotEmpty()) sorted.first().toDouble() else 0.0
            val max = if (sorted.isNotEmpty()) sorted.last().toDouble() else 0.0

            var jitterSum = 0.0
            var jitterCount = 0
            for (j in 1 until successful.size) {
                jitterSum += abs(successful[j] - successful[j - 1])
                jitterCount++
            }
            val jitter = if (jitterCount > 0) jitterSum / jitterCount else 0.0

            val isSuspectedRateLimited = loss > 20.0 && httpsSuccessRate > 0.8 && dnsSuccessRate > 0.8

            IcmpProbeResult(
                targetName = name,
                targetAddress = address,
                totalSent = totalSent,
                successfulReceived = successfulReceived,
                minLatencyMs = min,
                maxLatencyMs = max,
                medianLatencyMs = median,
                packetLossPercent = loss,
                jitterMs = jitter,
                isSuspectedRateLimited = isSuspectedRateLimited
            )
        }
    }

    private fun summarizeDnsMetrics(dnsMetrics: List<DnsQueryMetric>): DnsTestSummary {
        val total = dnsMetrics.size
        if (total == 0) return DnsTestSummary()

        val successful = dnsMetrics.count { it.isSuccess }
        val failureRate = (total - successful).toDouble() / total.toDouble() * 100.0
        val successfulMetrics = dnsMetrics.filter { it.isSuccess }
        val avgTime = if (successfulMetrics.isNotEmpty()) successfulMetrics.map { it.responseTimeMs }.average() else 0.0
        val maxTime = dnsMetrics.maxOfOrNull { it.responseTimeMs } ?: 0L
        val rcodes = dnsMetrics.groupingBy { it.rcode }.eachCount()

        return DnsTestSummary(
            totalQueries = total,
            successfulQueries = successful,
            failureRatePercent = failureRate,
            avgResponseTimeMs = avgTime,
            maxResponseTimeMs = maxTime,
            rcodeCounts = rcodes
        )
    }

    private fun calculateCorrelation(
        samples: List<WiFiStabilitySample>,
        gatewayLatencies: List<Long>
    ): CorrelationMetrics {
        val validPairs = mutableListOf<Pair<Double, Double>>()
        val count = minOf(samples.size, gatewayLatencies.size)
        for (i in 0 until count) {
            val rssi = samples[i].rssi.toDouble()
            val latency = gatewayLatencies[i].toDouble()
            if (rssi != 0.0 && rssi != -100.0) {
                val latVal = if (latency > 0) latency else 1000.0
                validPairs.add(rssi to latVal)
            }
        }

        if (validPairs.size < 5) return CorrelationMetrics()

        val avgX = validPairs.map { it.first }.average()
        val avgY = validPairs.map { it.second }.average()

        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for ((x, y) in validPairs) {
            val dx = x - avgX
            val dy = y - avgY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }

        val denom = sqrt(denX * denY)
        val correlation = if (denom != 0.0) num / denom else 0.0

        return CorrelationMetrics(
            rssiVsGatewayLatencyCorrelation = correlation,
            isGatewayDegradationCorrelatedWithRssiDrop = correlation < -0.3
        )
    }

    fun classifyStability(
        isWifiConnected: Boolean,
        samples: List<WiFiStabilitySample>,
        rssiStats: RssiStats,
        linkRateStats: LinkRateStats,
        icmpProbes: List<IcmpProbeResult>,
        dnsSummary: DnsTestSummary,
        httpsProbes: List<HttpsProbeResult>,
        correlation: CorrelationMetrics,
        speedTestResult: SpeedTestResult? = null,
        transport: String? = null,
        isValidated: Boolean = true,
        isMetered: Boolean = false,
        channelWidthLabel: String? = null,
        interfaceName: String? = null,
        isVpnConnected: Boolean = false,
        vpnInterface: String? = null
    ): WiFiStabilityTestResult {
        val secondaryObservations = mutableListOf<String>()

        fun buildResult(
            classification: WiFiHealthClassification,
            confidence: ConfidenceLevel,
            explanation: String,
            causes: List<String> = emptyList(),
            actions: List<String> = emptyList()
        ) = WiFiStabilityTestResult(
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = correlation,
            primaryClassification = classification,
            confidence = confidence,
            primaryExplanation = explanation,
            secondaryObservations = secondaryObservations,
            possibleCauses = causes,
            recommendedActions = actions,
            speedTestResult = speedTestResult,
            transport = transport,
            isValidated = isValidated,
            isMetered = isMetered,
            channelWidthLabel = channelWidthLabel,
            interfaceName = interfaceName,
            isVpnConnected = isVpnConnected,
            vpnInterface = vpnInterface
        )

        if (!isWifiConnected) {
            return buildResult(
                classification = WiFiHealthClassification.NO_WIFI_CONNECTION,
                confidence = ConfidenceLevel.HIGH,
                explanation = "Device is not connected to an active Wi-Fi network.",
                causes = listOf("Wi-Fi turned off", "Device disconnected from AP"),
                actions = listOf("Enable Wi-Fi and connect to a network")
            )
        }

        val totalHttps = httpsProbes.size
        val successfulHttps = httpsProbes.count { it.isSuccess }
        val httpsSuccessRate = if (totalHttps > 0) successfulHttps.toDouble() / totalHttps.toDouble() else 0.0
        val captivePortalCount = httpsProbes.count { it.isCaptivePortalDetected }

        if (captivePortalCount >= 2 || (httpsSuccessRate < 0.2 && captivePortalCount >= 1)) {
            return buildResult(
                classification = WiFiHealthClassification.CAPTIVE_PORTAL_OR_RESTRICTED,
                confidence = ConfidenceLevel.HIGH,
                explanation = "Network connections are being intercepted or redirected to a captive portal login page.",
                causes = listOf("Guest Wi-Fi portal required", "Unauthenticated public Wi-Fi", "Walled garden network policy"),
                actions = listOf("Open web browser to complete network authentication")
            )
        }

        val isRssiCritical = rssiStats.avgRssi < -80 || rssiStats.minRssi < -84
        val isRfUnstable = rssiStats.stdDevRssi > 6.0 && linkRateStats.minTxMbps in 1..10

        if (isRssiCritical || (isRfUnstable && correlation.isGatewayDegradationCorrelatedWithRssiDrop)) {
            return buildResult(
                classification = WiFiHealthClassification.VERY_WEAK_OR_UNSTABLE_WIFI,
                confidence = if (isRssiCritical) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
                explanation = "Wi-Fi signal is critically weak (Avg: ${String.format(Locale.US, "%.1f", rssiStats.avgRssi)} dBm, Min: ${rssiStats.minRssi} dBm) or suffering severe signal level fluctuations.",
                causes = listOf("Excessive distance from Access Point", "Physical obstructions (concrete walls, metal shielding)", "Severe 2.4GHz/5GHz RF channel interference"),
                actions = listOf("Move closer to the Wi-Fi Access Point", "Switch to 5GHz or 6GHz band if available")
            )
        }

        val gatewayProbe = icmpProbes.find { it.targetName == "Gateway" }
        val gatewayLoss = gatewayProbe?.packetLossPercent ?: 0.0
        val gatewayJitter = gatewayProbe?.jitterMs ?: 0.0

        val isGatewayIcmpRateLimited = gatewayLoss > 20.0 && dnsSummary.failureRatePercent < 10.0 && httpsSuccessRate > 0.8
        if (isGatewayIcmpRateLimited) {
            secondaryObservations.add("Gateway router appears to be rate-limiting ICMP ping requests; actual DNS/web traffic is unaffected.")
        }

        val isGatewayUnstable = !isGatewayIcmpRateLimited && (gatewayLoss > 10.0 || gatewayJitter > 50.0)

        if (isGatewayUnstable) {
            val causeExplanation = if (correlation.isGatewayDegradationCorrelatedWithRssiDrop) {
                "Local gateway latency and packet loss correlate directly with Wi-Fi signal drops."
            } else {
                "Local gateway latency and packet loss detected while Wi-Fi RSSI remained stable (suggests router CPU overload or local LAN congestion)."
            }

            return buildResult(
                classification = WiFiHealthClassification.LOCAL_NETWORK_INSTABILITY,
                confidence = if (gatewayLoss > 25.0) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
                explanation = "Likely local network instability (Gateway loss: ${String.format(Locale.US, "%.1f", gatewayLoss)}%, jitter: ${gatewayJitter.toInt()}ms). $causeExplanation",
                causes = listOf("Local network congestion / heavy bandwidth usage", "Router/AP CPU overload or bufferbloat", "Mesh backhaul connection latency", "Weak Wi-Fi signal or local interference", "ICMP rate-limiting by gateway"),
                actions = listOf("Reboot local Wi-Fi router/AP", "Reduce bandwidth usage on local network devices")
            )
        }

        val isDnsFailing = dnsSummary.totalQueries > 0 && (dnsSummary.failureRatePercent > 25.0 || dnsSummary.avgResponseTimeMs > 800.0)
        val isIpReachabilityGood = gatewayLoss < 10.0

        if (isDnsFailing && isIpReachabilityGood) {
            return buildResult(
                classification = WiFiHealthClassification.DNS_ISSUE,
                confidence = ConfidenceLevel.HIGH,
                explanation = "Actual DNS queries are failing (${String.format(Locale.US, "%.1f", dnsSummary.failureRatePercent)}% failure rate, avg lookup time: ${dnsSummary.avgResponseTimeMs.toInt()}ms), while local IP routing is operational.",
                causes = listOf("Unresponsive or overloaded DNS resolver", "ISP DNS service outage", "Misconfigured local DNS server IP"),
                actions = listOf("Change device or router DNS settings to Cloudflare (1.1.1.1) or Google (8.8.8.8)")
            )
        }

        val publicProbes = icmpProbes.filter { it.targetName in listOf("Google DNS", "Cloudflare DNS") }
        val avgPublicIcmpLoss = if (publicProbes.isNotEmpty()) publicProbes.map { it.packetLossPercent }.average() else 0.0
        val isPublicIcmpRateLimited = avgPublicIcmpLoss > 30.0 && httpsSuccessRate > 0.85

        if (isPublicIcmpRateLimited) {
            secondaryObservations.add("Public DNS target IPs (8.8.8.8 / 1.1.1.1) are rate-limiting ICMP ping probes; web traffic is healthy.")
        }

        val validTtfb = httpsProbes.map { it.ttfbMs }.filter { it > 0 }
        val avgTtfb = if (validTtfb.isNotEmpty()) validTtfb.average() else 0.0

        val isUpstreamFailing = gatewayLoss < 10.0 && (
            (!isPublicIcmpRateLimited && avgPublicIcmpLoss > 20.0) ||
            (totalHttps > 0 && httpsSuccessRate < 0.70) ||
            avgTtfb > 1500.0
        )

        if (isUpstreamFailing) {
            return buildResult(
                classification = WiFiHealthClassification.UPSTREAM_CONNECTIVITY_ISSUE,
                confidence = if (httpsSuccessRate < 0.50) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
                explanation = "Gateway router is reachable, but connections to public Internet endpoints and HTTPS services are experiencing packet loss or high latency.",
                causes = listOf("ISP connection outage or WAN link degradation", "Upstream routing congestion", "Modem/fiber ONT disconnect"),
                actions = listOf("Check WAN status light on modem/router", "Contact Internet Service Provider (ISP)")
            )
        }

        val effectiveGatewayLoss = if (isGatewayIcmpRateLimited) 0.0 else gatewayLoss

        if (
            rssiStats.avgRssi >= -72 &&
            effectiveGatewayLoss <= 5.0 &&
            dnsSummary.failureRatePercent <= 10.0 &&
            (totalHttps == 0 || httpsSuccessRate >= 0.90)
        ) {
            return buildResult(
                classification = WiFiHealthClassification.HEALTHY,
                confidence = ConfidenceLevel.HIGH,
                explanation = "All Wi-Fi physical link, local gateway, DNS resolution, and HTTP end-to-end checks passed within normal thresholds."
            )
        }

        return buildResult(
            classification = WiFiHealthClassification.INCONCLUSIVE,
            confidence = ConfidenceLevel.LOW,
            explanation = "Diagnostic metrics showed mixed or borderline results without exceeding specific alert thresholds.",
            causes = listOf("Intermittent network traffic spikes", "Borderline Wi-Fi signal quality"),
            actions = listOf("Re-run the stability test or perform a manual speed test")
        )
    }

    suspend fun runThroughputTest(
        host: String,
        port: Int,
        isUdp: Boolean,
        durationSeconds: Int,
        onProgress: (Float) -> Unit
    ): WiFiThroughputResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + (durationSeconds * 1000)
            var totalBytes = 0L
            
            try {
                if (isUdp) {
                    val socket = DatagramSocket()
                    val address = InetAddress.getByName(host)
                    val buffer = ByteArray(1400)
                    while (System.currentTimeMillis() < endTime) {
                        socket.send(DatagramPacket(buffer, buffer.size, address, port))
                        totalBytes += buffer.size
                        onProgress(((System.currentTimeMillis() - startTime).toFloat() / (durationSeconds * 1000)).coerceAtMost(1f))
                    }
                    socket.close()
                } else {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 5000)
                    val out = socket.getOutputStream()
                    val buffer = ByteArray(16384)
                    while (System.currentTimeMillis() < endTime) {
                        out.write(buffer)
                        totalBytes += buffer.size
                        onProgress(((System.currentTimeMillis() - startTime).toFloat() / (durationSeconds * 1000)).coerceAtMost(1f))
                    }
                    socket.close()
                }
                
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val mbps = (totalBytes * 8.0) / (elapsedSeconds * 1_000_000.0)
                WiFiThroughputResult(throughputMbps = mbps, isRunning = false, progress = 1f)
            } catch (e: Exception) {
                WiFiThroughputResult(errorMessage = e.message ?: "Test failed", isRunning = false)
            }
        }
    }
}
