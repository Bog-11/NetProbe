package com.example.netprobe.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.example.netprobe.model.LocalAddress
import com.example.netprobe.model.NetworkOverview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkInfoRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val wifiManager = 
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun observeNetworkOverview(): Flow<NetworkOverview> = callbackFlow {
        var currentCapabilities: NetworkCapabilities? = null
        var currentLinkProperties: LinkProperties? = null

        fun buildOverview(network: Network?): NetworkOverview {
            if (network == null) return NetworkOverview()

            val caps = currentCapabilities ?: connectivityManager.getNetworkCapabilities(network)
            val link = currentLinkProperties ?: connectivityManager.getLinkProperties(network)

            if (caps == null || link == null) return NetworkOverview()

            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }

            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || (
                android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            )

            var wifiName: String? = null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // Try modern way first (API 29+)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val info = caps.transportInfo
                    if (info is WifiInfo) {
                        wifiName = info.ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
                    }
                }
                
                // Fallback to WifiManager (can be more reliable for current connection SSID)
                if (wifiName == null) {
                    try {
                        @Suppress("DEPRECATION")
                        val info = wifiManager.connectionInfo
                        if (info != null) {
                            wifiName = info.ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
                        }
                    } catch (_: SecurityException) {
                        // Permission missing or denied at runtime
                    }
                }
            }

            val localAddrs = link.linkAddresses
                .mapNotNull {
                    val host = it.address.hostAddress
                    if (host != null && !host.contains(":")) {
                        LocalAddress(host, it.prefixLength)
                    } else null
                }

            return NetworkOverview(
                isConnected = true,
                transport = transport,
                isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                isMetered = connectivityManager.isActiveNetworkMetered,
                dnsServers = link.dnsServers.map { it.hostAddress ?: it.toString() },
                localAddresses = localAddrs,
                primaryIp = localAddrs.firstOrNull()?.address,
                interfaceName = link.interfaceName,
                wifiName = wifiName,
                wifiPermissionGranted = hasLocation
            )
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentCapabilities = connectivityManager.getNetworkCapabilities(network)
                currentLinkProperties = connectivityManager.getLinkProperties(network)
                trySend(buildOverview(network))
            }

            override fun onLost(network: Network) {
                currentCapabilities = null
                currentLinkProperties = null
                trySend(buildOverview(connectivityManager.activeNetwork))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                currentCapabilities = networkCapabilities
                trySend(buildOverview(network))
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                currentLinkProperties = linkProperties
                trySend(buildOverview(network))
            }
        }

        val activeNetwork = connectivityManager.activeNetwork
        currentCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        currentLinkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
        trySend(buildOverview(activeNetwork))

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}