package com.brutiful.netprobe.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConnectionStatus {
    ACTIVE,
    INACTIVE
}

@Entity(tableName = "live_connections")
data class LiveConnection(
    @PrimaryKey val id: String,
    val destinationIp: String,
    val destinationPort: Int,
    val protocol: String,
    val uid: Int = -1,
    val packageName: String? = null,
    val packageNames: List<String> = emptyList(),
    val appLabel: String = "Unknown app",
    val destinationHost: String? = null,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val sentBytes: Long = 0,
    val receivedBytes: Long = 0,
    val totalPackets: Int = 0,
    val status: ConnectionStatus = ConnectionStatus.ACTIVE,
    val resolutionReason: String? = null
) {
    val totalBytes: Long get() = sentBytes + receivedBytes
    val isActive: Boolean get() = status == ConnectionStatus.ACTIVE
}

@Entity(tableName = "connection_history")
data class ConnectionHistory(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceIp: String? = null,
    val sourcePort: Int = 0,
    val destinationIp: String,
    val destinationPort: Int,
    val protocol: String,
    val uid: Int,
    val packageName: String?,
    val appLabel: String,
    val destinationHost: String?,
    val sentBytes: Long,
    val receivedBytes: Long
)

data class AppTrafficStats(
    val packageName: String?,
    val packageNames: List<String>,
    val appLabel: String,
    val uid: Int,
    val activeConnections: Int,
    val totalConnections: Int,
    val sentBytes: Long,
    val receivedBytes: Long,
    val lastSeen: Long,
    val resolutionReason: String? = null
) {
    val totalBytes: Long get() = sentBytes + receivedBytes
}
