package com.example.netprobe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netprobe.model.DeviceType
import com.example.netprobe.model.DiscoveredDevice
import com.example.netprobe.model.LocalAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.pow

data class DiscoveryUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val currentSubnet: String? = null
)

class DiscoveryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    fun startDiscovery(localAddress: LocalAddress) {
        if (_uiState.value.isScanning) return

        val ip = localAddress.address
        val prefix = localAddress.prefixLength

        val ips = getSubnetIps(ip, prefix)
        _uiState.update { 
            it.copy(
                isScanning = true, 
                devices = emptyList(), 
                progress = 0f,
                currentSubnet = localAddress.getNetworkRange()
            ) 
        }

        viewModelScope.launch(Dispatchers.IO) {
            val total = ips.size
            val chunkSize = 15 
            val discovered = mutableListOf<DiscoveredDevice>()

            ips.chunked(chunkSize).forEachIndexed { index, chunk ->
                val results = chunk.map { targetIp ->
                    async {
                        inspectDevice(targetIp)
                    }
                }.awaitAll().filterNotNull()

                discovered.addAll(results)
                _uiState.update { 
                    it.copy(
                        devices = discovered.toList().sortedBy { d -> 
                            d.ipAddress.split(".").last().toIntOrNull() ?: 0 
                        },
                        progress = ((index + 1) * chunkSize).toFloat() / total
                    )
                }
            }

            _uiState.update { it.copy(isScanning = false, progress = 1f) }
        }
    }

    private suspend fun inspectDevice(ip: String): DiscoveredDevice? {
        return try {
            val address = InetAddress.getByName(ip)
            if (address.isReachable(350)) {
                val hostName = address.canonicalHostName.takeIf { it != ip }
                val fingerprint = fingerprintDevice(ip)
                
                DiscoveredDevice(
                    ipAddress = ip,
                    isReachable = true,
                    hostName = hostName ?: fingerprint.suggestedName,
                    deviceType = fingerprint.type,
                    modelName = fingerprint.model
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private data class Fingerprint(
        val type: DeviceType,
        val suggestedName: String? = null,
        val model: String? = null
    )

    private fun fingerprintDevice(ip: String): Fingerprint {
        val openPorts = listOf(62078, 445, 548, 8008, 9100, 22).map { port ->
            port to isPortOpen(ip, port, 150)
        }.filter { it.second }.map { it.first }

        return when {
            openPorts.contains(62078) -> Fingerprint(DeviceType.IPHONE, "Apple Device", "iPhone/iPad")
            openPorts.contains(548) -> Fingerprint(DeviceType.MAC, "Macintosh", "MacBook/iMac")
            openPorts.contains(445) -> Fingerprint(DeviceType.WINDOWS, "Windows PC", "Workstation")
            openPorts.contains(9100) -> Fingerprint(DeviceType.PRINTER, "Network Printer")
            openPorts.contains(8008) -> Fingerprint(DeviceType.IOT, "Google Home/Chromecast")
            openPorts.contains(22) -> Fingerprint(DeviceType.LINUX, "Linux Device", "Server/Embedded")
            else -> Fingerprint(DeviceType.UNKNOWN)
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getSubnetIps(ip: String, prefix: Int): List<String> {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return emptyList()

        val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = if (prefix == 0) 0 else (0xFFFFFFFF shl (32 - prefix)).toInt()
        val network = ipInt and mask
        val hosts = 2.0.pow(32 - prefix).toInt()

        val limit = if (hosts > 256) 256 else hosts

        return (1 until limit - 1).map { i ->
            val hostIp = network or i
            "${(hostIp ushr 24) and 0xFF}.${(hostIp ushr 16) and 0xFF}.${(hostIp ushr 8) and 0xFF}.${hostIp and 0xFF}"
        }
    }
}
