package com.brutiful.netprobe.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.brutiful.netprobe.model.DeviceFingerprint
import com.brutiful.netprobe.model.FingerprintSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class MdnsDiscoveryHelper(context: Context) {
    private val TAG = "MdnsDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        val COMMON_SERVICES = listOf(
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

    suspend fun discoverAll(timeoutMs: Long = 6000): List<DeviceFingerprint> = coroutineScope {
        COMMON_SERVICES.map { serviceType ->
            async { discover(serviceType, timeoutMs) }
        }.awaitAll().flatten()
    }

    suspend fun discover(serviceType: String, timeoutMs: Long): List<DeviceFingerprint> {
        val fingerprints = mutableListOf<DeviceFingerprint>()
        val discoveryFinished = CompletableDeferred<Unit>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName} ($serviceType)")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val ip = resolvedInfo.host.hostAddress
                        val hostName = resolvedInfo.host.hostName
                        
                        val fingerprint = DeviceFingerprint(
                            source = FingerprintSource.MDNS,
                            friendlyName = resolvedInfo.serviceName,
                            hostName = if (hostName != ip) hostName else null,
                            confidence = 45,
                            rawMetadata = mapOf(
                                "ip" to ip,
                                "serviceType" to resolvedInfo.serviceType,
                                "host" to hostName
                            )
                        )
                        synchronized(fingerprints) { fingerprints.add(fingerprint) }
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
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error in mDNS discovery for $serviceType", e)
        }

        return fingerprints
    }
}
