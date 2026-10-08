package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.util.Log
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoverySource
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class HostProbeResult(
    val address: InetAddress,
    val isIcmpReachable: Boolean,
    val openPorts: Set<Int>,
    val hostname: String?
)

object TcpProbeScanner {
    private const val TAG = "TcpProbeScanner"
    val CONSERVATIVE_PORTS = listOf(80, 443, 445, 631, 554, 9100)

    suspend fun probeCandidates(
        context: Context,
        candidates: List<InetAddress>,
        maxConcurrency: Int = 24,
        connectTimeoutMs: Int = 350,
        onProgressUpdate: (completed: Int, total: Int, progress: Float) -> Unit
    ): List<DiscoveredDevice> = coroutineScope {
        val total = candidates.size
        if (total == 0) return@coroutineScope emptyList()

        val semaphore = Semaphore(maxConcurrency)
        val completedCount = AtomicInteger(0)
        val results = mutableListOf<DiscoveredDevice>()

        val jobs = candidates.map { addr ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val probeRes = probeSingleHost(context, addr, connectTimeoutMs)
                    val done = completedCount.incrementAndGet()
                    val progressRatio = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    onProgressUpdate(done, total, progressRatio)

                    if (probeRes != null) {
                        val sources = mutableSetOf<DiscoverySource>()
                        if (probeRes.isIcmpReachable) sources.add(DiscoverySource.ICMP)
                        if (probeRes.openPorts.isNotEmpty()) sources.add(DiscoverySource.TCP_CONNECT)

                        val category = when {
                            probeRes.openPorts.contains(554) -> DeviceCategory.CAMERA
                            probeRes.openPorts.contains(631) || probeRes.openPorts.contains(9100) -> DeviceCategory.PRINTER
                            probeRes.openPorts.contains(445) -> DeviceCategory.COMPUTER_MOBILE
                            probeRes.openPorts.contains(80) || probeRes.openPorts.contains(443) -> DeviceCategory.UNKNOWN_REACHABLE
                            else -> DeviceCategory.UNKNOWN_REACHABLE
                        }

                        val notes = mutableListOf<String>()
                        if (probeRes.isIcmpReachable) notes.add("ICMP Echo reply")
                        if (probeRes.openPorts.isNotEmpty()) notes.add("Open TCP ports: ${probeRes.openPorts.joinToString()}")

                        val device = DiscoveredDevice(
                            ipAddress = addr,
                            hostname = probeRes.hostname,
                            openPorts = probeRes.openPorts,
                            sources = sources,
                            category = category,
                            confidence = if (probeRes.openPorts.isNotEmpty()) Confidence.MEDIUM else Confidence.LOW,
                            notes = notes
                        )
                        synchronized(results) {
                            results.add(device)
                        }
                    }
                }
            }
        }

        jobs.awaitAll()
        return@coroutineScope results
    }

    private fun probeSingleHost(
        context: Context,
        address: InetAddress,
        connectTimeoutMs: Int
    ): HostProbeResult? {
        var icmpReachable = false
        try {
            icmpReachable = address.isReachable(connectTimeoutMs)
        } catch (_: Exception) {}

        val openPorts = mutableSetOf<Int>()
        for (port in CONSERVATIVE_PORTS) {
            if (isPortOpen(context, address, port, connectTimeoutMs)) {
                openPorts.add(port)
            }
        }

        if (!icmpReachable && openPorts.isEmpty()) {
            return null // Device unconfirmed
        }

        // Bounded non-blocking reverse DNS lookup (max 200ms)
        var hostname: String? = null
        try {
            val rawHost = address.canonicalHostName
            if (rawHost != address.hostAddress) {
                hostname = NetworkUtils.sanitizeHostName(rawHost)
            }
        } catch (_: Exception) {}

        return HostProbeResult(
            address = address,
            isIcmpReachable = icmpReachable,
            openPorts = openPorts,
            hostname = hostname
        )
    }

    private fun isPortOpen(context: Context, address: InetAddress, port: Int, timeoutMs: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            NetworkInterfaceBinder.bindSocketToWifi(context, socket)
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
