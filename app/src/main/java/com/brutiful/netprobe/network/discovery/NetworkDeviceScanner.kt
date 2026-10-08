package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoverySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class NetworkScanResult(
    val devices: List<DiscoveredDevice>,
    val scanRange: SubnetScanRange,
    val isMulticastBlocked: Boolean = false,
    val isPartialCoverage: Boolean = false,
    val warningMessage: String? = null
)

class NetworkDeviceScanner(private val context: Context) {
    private val TAG = "NetworkDeviceScanner"
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun runBestEffortScan(
        phoneIp: InetAddress,
        prefixLength: Int,
        maxHostCap: Int = 254,
        onProgress: (progress: Float, phase: String) -> Unit
    ): NetworkScanResult = withContext(Dispatchers.IO) {
        val multicastLock = wifiManager.createMulticastLock("NetProbeDiscoveryLock").apply {
            setReferenceCounted(false)
        }

        try {
            Log.d(TAG, "Acquiring MulticastLock for network device discovery...")
            try {
                multicastLock.acquire()
            } catch (e: Exception) {
                Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
            }

            onProgress(0.05f, "Layer 1: Multicast discovery (mDNS, SSDP, WS-Discovery)...")
            ensureActive()

            // 1. Multicast Discovery Layer (mDNS, SSDP, WS-Discovery)
            val mdnsScanner = MdnsDiscoveryScanner(context)
            val mdnsJob = async { mdnsScanner.discoverAll(timeoutMs = 3000) }
            val ssdpJob = async { SsdpDiscoveryScanner.discover(context, timeoutMs = 2000, passes = 2) }
            val wsJob = async { WsDiscoveryScanner.discover(context, timeoutMs = 2000, passes = 2) }

            val mdnsDevices = mdnsJob.await()
            val ssdpResponses = ssdpJob.await()
            val wsResponses = wsJob.await()

            ensureActive()
            onProgress(0.35f, "Layer 2: Subnet range calculation & TCP probe sweep...")

            val ssdpDevices = ssdpResponses.map { SsdpDiscoveryScanner.toDiscoveredDevice(it) }
            val wsDevices = wsResponses.map { WsDiscoveryScanner.toDiscoveredDevice(it) }

            val multicastBlocked = mdnsDevices.isEmpty() && ssdpResponses.isEmpty() && wsResponses.isEmpty()

            // 2. Subnet Calculation & Bounded TCP Connect Sweep
            val scanRange = SubnetCalculator.calculateScanRange(phoneIp, prefixLength, maxHostCap)

            val tcpDevices = TcpProbeScanner.probeCandidates(
                context = context,
                candidates = scanRange.candidateAddresses,
                maxConcurrency = 24,
                connectTimeoutMs = 350
            ) { completed, total, ratio ->
                val overallProgress = (0.35f + (ratio * 0.45f)).coerceIn(0f, 1f)
                onProgress(overallProgress, "Layer 2: Probing candidate hosts ($completed/$total)...")
            }

            ensureActive()
            onProgress(0.80f, "Layer 3: Evidence merging & secondary probing...")

            // 3. Evidence-Based Merging
            val allObserved = mdnsDevices + ssdpDevices + wsDevices + tcpDevices
            val mergedDevices = mergeAndGroupDevices(allObserved)

            // 4. Layer 4: Secondary Evidence Probing (ONVIF/RTSP, IPP, UPnP, HTTP)
            val finalDevices = mergedDevices.map { dev ->
                ensureActive()
                SecondaryProtocolProber.probeSecondaryEvidence(context, dev)
            }

            onProgress(1.0f, "Scan completed.")

            val warningMsg = when {
                scanRange.isPartialCoverage && multicastBlocked ->
                    "Partial subnet coverage (${scanRange.scannedHostCount} of ${scanRange.totalHostsInSubnet} hosts scanned). Multicast discovery returned no responses (Wi-Fi AP client isolation or multicast filtering may be active)."
                scanRange.isPartialCoverage ->
                    "Partial subnet coverage: Scanned ${scanRange.scannedHostCount} of ${scanRange.totalHostsInSubnet} hosts centered around ${phoneIp.hostAddress}."
                multicastBlocked ->
                    "Multicast discovery returned no responses. Guest Wi-Fi isolation, AP client isolation, or multicast filtering may be blocking zero-config discovery."
                else -> null
            }

            return@withContext NetworkScanResult(
                devices = finalDevices,
                scanRange = scanRange,
                isMulticastBlocked = multicastBlocked,
                isPartialCoverage = scanRange.isPartialCoverage,
                warningMessage = warningMsg
            )
        } finally {
            if (multicastLock.isHeld) {
                try {
                    multicastLock.release()
                    Log.d(TAG, "Released MulticastLock")
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing MulticastLock: ${e.message}")
                }
            }
        }
    }

    private fun mergeAndGroupDevices(devices: List<DiscoveredDevice>): List<DiscoveredDevice> {
        val grouped = mutableMapOf<String, DiscoveredDevice>()

        for (dev in devices) {
            val key = dev.ipAddress?.hostAddress ?: dev.hostname ?: dev.computedDisplayName()
            val existing = grouped[key]

            if (existing == null) {
                grouped[key] = dev
            } else {
                val mergedIp = existing.ipAddress ?: dev.ipAddress
                val mergedIpv6 = existing.ipv6Addresses + dev.ipv6Addresses
                val mergedHostname = existing.hostname ?: dev.hostname
                val mergedDisplayName = existing.displayName ?: dev.displayName
                val mergedMac = existing.macAddress ?: dev.macAddress
                val mergedServices = (existing.services + dev.services).distinctBy { "${it.serviceType}:${it.port}:${it.name}" }
                val mergedPorts = existing.openPorts + dev.openPorts
                val mergedSources = existing.sources + dev.sources
                val mergedNotes = (existing.notes + dev.notes).distinct()

                val mergedCategory = selectHighestPriorityCategory(existing.category, dev.category)
                val mergedConfidence = selectHighestConfidence(existing.confidence, dev.confidence)

                grouped[key] = DiscoveredDevice(
                    ipAddress = mergedIp,
                    ipv6Addresses = mergedIpv6,
                    hostname = mergedHostname,
                    displayName = mergedDisplayName,
                    macAddress = mergedMac,
                    services = mergedServices,
                    openPorts = mergedPorts,
                    sources = mergedSources,
                    category = mergedCategory,
                    confidence = mergedConfidence,
                    lastSeenMillis = maxOf(existing.lastSeenMillis, dev.lastSeenMillis),
                    notes = mergedNotes,
                    vendorName = existing.vendorName ?: dev.vendorName,
                    modelName = existing.modelName ?: dev.modelName,
                    manufacturer = existing.manufacturer ?: dev.manufacturer
                )
            }
        }

        return grouped.values.toList()
    }

    private fun selectHighestPriorityCategory(c1: DeviceCategory, c2: DeviceCategory): DeviceCategory {
        val priorityMap = mapOf(
            DeviceCategory.CAMERA to 6,
            DeviceCategory.PRINTER to 5,
            DeviceCategory.MEDIA_STREAMER to 4,
            DeviceCategory.SMART_HOME to 3,
            DeviceCategory.NETWORK_EQUIPMENT to 2,
            DeviceCategory.COMPUTER_MOBILE to 1,
            DeviceCategory.UNKNOWN_REACHABLE to 0
        )
        val p1 = priorityMap[c1] ?: 0
        val p2 = priorityMap[c2] ?: 0
        return if (p1 >= p2) c1 else c2
    }

    private fun selectHighestConfidence(c1: Confidence, c2: Confidence): Confidence {
        if (c1 == Confidence.HIGH || c2 == Confidence.HIGH) return Confidence.HIGH
        if (c1 == Confidence.MEDIUM || c2 == Confidence.MEDIUM) return Confidence.MEDIUM
        return Confidence.LOW
    }
}
