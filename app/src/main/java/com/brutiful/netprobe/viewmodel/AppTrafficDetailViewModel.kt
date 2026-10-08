package com.brutiful.netprobe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.CapturedPacket
import com.brutiful.netprobe.network.PacketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppTrafficDetailViewModel : ViewModel() {
    private val _packets = MutableStateFlow<List<CapturedPacket>>(emptyList())
    val packets: StateFlow<List<CapturedPacket>> = _packets.asStateFlow()

    private var currentConnectionId: String? = null

    fun setConnection(connectionId: String) {
        if (currentConnectionId == connectionId) return
        currentConnectionId = connectionId
        
        viewModelScope.launch {
            PacketRepository.packets.collectLatest { allPackets ->
                _packets.value = allPackets[connectionId] ?: emptyList()
            }
        }
    }
}
