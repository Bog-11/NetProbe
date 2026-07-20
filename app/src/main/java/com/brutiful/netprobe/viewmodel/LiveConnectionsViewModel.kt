package com.brutiful.netprobe.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.network.ConnectionTracker
import com.brutiful.netprobe.network.ExportMode
import com.brutiful.netprobe.network.PacketRepository
import com.brutiful.netprobe.network.RawPcapExporter
import com.brutiful.netprobe.network.ReconstructedPcapExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveConnectionsViewModel : ViewModel() {
    val activeConnections = ConnectionTracker.activeConnections

    private val _exportStatus = MutableStateFlow<ExportStatus?>(null)
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus.asStateFlow()

    fun exportPcap(context: Context, uri: Uri, mode: ExportMode, localIpStr: String? = null) {
        viewModelScope.launch {
            _exportStatus.value = ExportStatus.Loading
            try {
                val allPackets = withContext(Dispatchers.IO) {
                    PacketRepository.packets.value.values.flatten()
                }
                
                if (allPackets.isEmpty()) {
                    _exportStatus.value = ExportStatus.Error("No packets to export")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        when (mode) {
                            ExportMode.RAW_VPN -> {
                                RawPcapExporter.export(allPackets, os, stripPayload = false)
                            }
                            ExportMode.SANITIZED_PCAP -> {
                                RawPcapExporter.export(allPackets, os, stripPayload = true)
                            }
                            ExportMode.RECONSTRUCTED_SESSION -> {
                                val localIp = localIpStr?.let { 
                                    try { java.net.InetAddress.getByName(it) } catch (_: Exception) { null }
                                } ?: java.net.InetAddress.getByName("127.0.0.1")
                                ReconstructedPcapExporter.export(allPackets, os, localIp)
                            }
                        }
                    } ?: throw Exception("Could not open output stream")
                }
                _exportStatus.value = ExportStatus.Success(mode)
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }
}

sealed class ExportStatus {
    object Loading : ExportStatus()
    data class Success(val mode: ExportMode) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}


