package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoveredService
import com.brutiful.netprobe.model.DiscoverySource
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

class MdnsDiscoveryScanner(private val context: Context) {
    private val TAG = "MdnsDiscoveryScanner"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        val SERVICE_TYPES = listOf(
            "_http._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_googlecast._tcp.",
            "_airplay._tcp.",
            "_spotify-connect._tcp.",
            "_workstation._tcp.",
            "_axis-video._tcp.",
            "_daap._tcp."
        )
    }

    suspend fun discoverAll(timeoutMs: Long = 4000): List<DiscoveredDevice> = coroutineScope {
        SERVICE_TYPES.map { serviceType ->
            async { discoverServiceType(serviceType, timeoutMs) }
        }.awaitAll().flatten()
    }

    suspend fun discoverServiceType(serviceType: String, timeoutMs: Long): List<DiscoveredDevice> {
        val devices = mutableListOf<DiscoveredDevice>()
        val discoveryFinished = CompletableDeferred<Unit>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${serviceInfo.serviceName} ($serviceType)")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val hostAddr = resolvedInfo.host ?: return
                        val rawHostName = hostAddr.hostName
                        val cleanHostName = NetworkUtils.sanitizeHostName(rawHostName)
                        val cleanServiceName = NetworkUtils.sanitizeHostName(resolvedInfo.serviceName) ?: resolvedInfo.serviceName

                        val txtRecords = mutableMapOf<String, String>()
                        try {
                            resolvedInfo.attributes.forEach { (key, value) ->
                                txtRecords[key] = String(value)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading TXT records for ${resolvedInfo.serviceName}", e)
                        }

                        val service = DiscoveredService(
                            serviceType = serviceType,
                            name = cleanServiceName,
                            port = resolvedInfo.port,
                            txtRecords = txtRecords,
                            hostTarget = cleanHostName,
                            source = DiscoverySource.MDNS
                        )

                        val category = when {
                            serviceType.contains("printer") || serviceType.contains("ipp") -> DeviceCategory.PRINTER
                            serviceType.contains("googlecast") || serviceType.contains("airplay") || serviceType.contains("spotify") || serviceType.contains("daap") -> DeviceCategory.MEDIA_STREAMER
                            serviceType.contains("axis-video") -> DeviceCategory.CAMERA
                            else -> DeviceCategory.UNKNOWN_REACHABLE
                        }

                        val device = DiscoveredDevice(
                            ipAddress = hostAddr,
                            hostname = if (cleanHostName != hostAddr.hostAddress) cleanHostName else null,
                            displayName = cleanServiceName,
                            services = listOf(service),
                            sources = setOf(DiscoverySource.MDNS),
                            category = category,
                            confidence = Confidence.MEDIUM,
                            notes = listOf("Discovered via mDNS ($serviceType)")
                        )

                        synchronized(devices) {
                            devices.add(device)
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onDiscoveryStopped(regType: String) {
                discoveryFinished.complete(Unit)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed for $serviceType: $errorCode")
                discoveryFinished.complete(Unit)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryFinished.complete(Unit)
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            withTimeoutOrNull(timeoutMs) {
                discoveryFinished.await()
            }
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Error in mDNS discovery for $serviceType: ${e.message}")
        }

        return devices
    }
}
