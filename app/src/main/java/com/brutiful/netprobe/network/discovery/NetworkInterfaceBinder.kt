package com.brutiful.netprobe.network.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.net.DatagramSocket
import java.net.Socket

object NetworkInterfaceBinder {
    private const val TAG = "NetworkInterfaceBinder"

    fun getActiveWifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        val activeNet = cm.activeNetwork
        if (activeNet != null) {
            val caps = cm.getNetworkCapabilities(activeNet)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return activeNet
            }
        }

        // Fallback: search all networks for a Wi-Fi network without VPN
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    fun bindSocketToWifi(context: Context, socket: DatagramSocket): Boolean {
        return try {
            val wifiNet = getActiveWifiNetwork(context)
            if (wifiNet != null) {
                wifiNet.bindSocket(socket)
                Log.d(TAG, "Successfully bound DatagramSocket to Wi-Fi network $wifiNet")
                true
            } else {
                Log.w(TAG, "No explicit Wi-Fi network found to bind socket")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind DatagramSocket to Wi-Fi network: ${e.message}")
            false
        }
    }

    fun bindSocketToWifi(context: Context, socket: Socket): Boolean {
        return try {
            val wifiNet = getActiveWifiNetwork(context)
            if (wifiNet != null) {
                wifiNet.bindSocket(socket)
                Log.d(TAG, "Successfully bound Socket to Wi-Fi network $wifiNet")
                true
            } else {
                Log.w(TAG, "No explicit Wi-Fi network found to bind socket")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Socket to Wi-Fi network: ${e.message}")
            false
        }
    }
}
