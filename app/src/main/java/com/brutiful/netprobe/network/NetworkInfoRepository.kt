package com.brutiful.netprobe.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.brutiful.netprobe.model.LocalAddress
import com.brutiful.netprobe.model.NetworkOverview
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkInfoRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val wifiManager = 
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

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
            var freqMhz: Int? = null
            var band: String? = null
            var width: String? = null

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // Try modern way first (API 29+)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val info = caps.transportInfo
                    if (info is WifiInfo) {
                        wifiName = info.ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
                        freqMhz = info.frequency.takeIf { it > 0 }
                        freqMhz?.let { band = NetworkUtils.getWifiBandLabel(it) }
                        // Note: WifiInfo doesn't directly expose channel width on all API levels.
                        // Future: can be extracted from ScanResult matching current BSSID if needed.
                        width = null 
                    }
                }
                
                // Fallback to WifiManager (can be more reliable for current connection SSID)
                if (wifiName == null) {
                    try {
                        @Suppress("DEPRECATION")
                        val info = wifiManager.connectionInfo
                        if (info != null) {
                            wifiName = info.ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
                            if (freqMhz == null) {
                                freqMhz = info.frequency.takeIf { it > 0 }
                                freqMhz?.let { band = NetworkUtils.getWifiBandLabel(it) }
                            }
                        }
                    } catch (_: SecurityException) {
                        // Permission missing or denied at runtime
                    }
                }
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        val allCellInfo = telephonyManager.allCellInfo
                        val activeCell = allCellInfo?.firstOrNull { it.isRegistered }
                        
                        if (activeCell is CellInfoLte) {
                            val identity = activeCell.cellIdentity
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                val bands = identity.bands
                                if (bands.isNotEmpty()) {
                                    band = NetworkUtils.formatCellularBandLabel("LTE", bands)
                                }
                            }
                            // Frequency derivation for LTE EARFCN is complex; leaving null as per conservative requirement
                        } else if (android.os.Build.VERSION.SDK_INT >= 29 && activeCell is CellInfoNr) {
                            val identity = activeCell.cellIdentity as? android.telephony.CellIdentityNr
                            if (identity != null && android.os.Build.VERSION.SDK_INT >= 30) {
                                val bands = identity.bands
                                if (bands.isNotEmpty()) {
                                    band = NetworkUtils.formatCellularBandLabel("NR", bands)
                                }
                            }
                        }
                    } catch (_: SecurityException) {
                        // Permission issues or location disabled
                    }
                }
            }

            val localAddrs = mutableListOf<LocalAddress>()
            connectivityManager.allNetworks.forEach { net ->
                val c = connectivityManager.getNetworkCapabilities(net)
                val l = connectivityManager.getLinkProperties(net)
                val isVpn = c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                
                l?.linkAddresses?.forEach { addr ->
                    val host = addr.address.hostAddress
                    if (host != null && !host.contains(":") && !host.startsWith("127.")) {
                        localAddrs.add(LocalAddress(host, addr.prefixLength, isVpn))
                    }
                }
            }
            
            // Prefer non-VPN addresses first for discovery defaults
            val sortedAddrs = localAddrs.distinct().sortedBy { it.isVpn }

            val vpnNetwork = connectivityManager.allNetworks.find { n ->
                val c = connectivityManager.getNetworkCapabilities(n)
                c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            
            var vpnProviderName: String? = null
            if (vpnNetwork != null) {
                val vpnProps = connectivityManager.getLinkProperties(vpnNetwork)
                // Attempt to extract provider from interface or domains
                vpnProviderName = vpnProps?.interfaceName?.let { name ->
                    when {
                        name.contains("tun") -> "Standard TUN VPN"
                        name.contains("ppp") -> "Point-to-Point VPN"
                        name.contains("wireguard") -> "WireGuard VPN"
                        else -> name
                    }
                }
                
                // Fallback to searching through active network domains if available
                if (vpnProps?.domains != null) {
                    vpnProviderName = vpnProps.domains
                }
            }

            return NetworkOverview(
                isConnected = true,
                transport = transport,
                isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                isMetered = connectivityManager.isActiveNetworkMetered,
                dnsServers = link.dnsServers.map { it.hostAddress ?: it.toString() },
                localAddresses = sortedAddrs,
                primaryIp = sortedAddrs.firstOrNull()?.address,
                interfaceName = link.interfaceName,
                wifiName = wifiName,
                wifiPermissionGranted = hasLocation,
                isVpnConnected = vpnNetwork != null,
                vpnInterface = vpnProviderName ?: vpnNetwork?.toString(),
                radioFrequencyMhz = freqMhz,
                radioBandLabel = band,
                channelWidthLabel = width
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
