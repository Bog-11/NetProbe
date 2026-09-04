package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

object PacketRepository {
    private const val MAX_PACKETS_PER_CONNECTION = 500
    private const val TOTAL_MAX_PACKETS = 10000

    private val _packets = MutableStateFlow<Map<String, List<CapturedPacket>>>(emptyMap())
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Throttled StateFlow for UI consumption
    private val _throttledPackets = _packets.asStateFlow()
        .sample(1000) // Update UI at most once per second for packets
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val packets: StateFlow<Map<String, List<CapturedPacket>>> = _throttledPackets

    private var totalCapturedCount = 0

    fun addPacket(packet: CapturedPacket) {
        _packets.update { currentMap ->
            val connectionPackets = currentMap[packet.connectionId]?.toMutableList() ?: mutableListOf()
            
            // Add new packet
            connectionPackets.add(0, packet) // Newest first
            totalCapturedCount++
            
            // Limit per connection
            if (connectionPackets.size > MAX_PACKETS_PER_CONNECTION) {
                connectionPackets.removeAt(connectionPackets.size - 1)
                totalCapturedCount--
            }
            
            val newMap = currentMap.toMutableMap()
            newMap[packet.connectionId] = connectionPackets
            
            // Global limit enforcement (drop oldest from connection with oldest packet)
            if (totalCapturedCount > TOTAL_MAX_PACKETS) {
                findAndRemoveOldest(newMap)
            }
            
            newMap
        }
    }

    private fun findAndRemoveOldest(map: MutableMap<String, List<CapturedPacket>>) {
        var oldestTime = Long.MAX_VALUE
        var oldestConnId: String? = null
        
        for ((connId, list) in map) {
            if (list.isNotEmpty()) {
                val last = list.last()
                if (last.timestamp < oldestTime) {
                    oldestTime = last.timestamp
                    oldestConnId = connId
                }
            }
        }
        
        oldestConnId?.let { id ->
            val list = map[id]?.toMutableList() ?: return
            if (list.isNotEmpty()) {
                list.removeAt(list.size - 1)
                totalCapturedCount--
                if (list.isEmpty()) map.remove(id)
                else map[id] = list
            }
        }
    }

    fun getPacketsForConnection(connectionId: String): List<CapturedPacket> {
        return _packets.value[connectionId] ?: emptyList()
    }

    fun clearPackets(connectionId: String) {
        _packets.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            newMap.remove(connectionId)
            newMap
        }
    }

    fun deleteAllPackets() {
        _packets.update { emptyMap() }
    }
}
