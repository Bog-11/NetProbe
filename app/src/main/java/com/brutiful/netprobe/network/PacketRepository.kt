package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

object PacketRepository {
    private const val MAX_PACKETS_PER_CONNECTION = 500
    private const val TOTAL_MAX_PACKETS = 10000

    private val _packets = MutableStateFlow<Map<String, List<CapturedPacket>>>(emptyMap())
    val packets: StateFlow<Map<String, List<CapturedPacket>>> = _packets.asStateFlow()

    fun addPacket(packet: CapturedPacket) {
        _packets.update { currentMap ->
            val connectionPackets = currentMap[packet.connectionId]?.toMutableList() ?: mutableListOf()
            
            // Add new packet
            connectionPackets.add(0, packet) // Newest first
            
            // Limit per connection
            if (connectionPackets.size > MAX_PACKETS_PER_CONNECTION) {
                connectionPackets.removeAt(connectionPackets.size - 1)
            }
            
            val newMap = currentMap.toMutableMap()
            newMap[packet.connectionId] = connectionPackets
            
            // TODO: Implement total limit across all connections if needed
            
            newMap
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
