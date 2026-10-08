package com.brutiful.netprobe.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.net.InetAddress

@Entity(tableName = "device_identities")
data class DeviceIdentity(
    @PrimaryKey val macAddress: String,
    val lastIpAddress: String,
    val vendorName: String? = null,
    val hostName: String? = null,
    val mdnsName: String? = null,
    val ssdpFriendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val deviceType: String? = null,
    val confidenceScore: Int = 0,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toDiscoveredDevice(currentIp: String, isReachable: Boolean = true): DiscoveredDevice {
        val inet = try { InetAddress.getByName(currentIp) } catch (_: Exception) { null }
        return DiscoveredDevice(
            ipAddress = inet,
            hostname = hostName,
            displayName = ssdpFriendlyName ?: mdnsName ?: hostName,
            macAddress = macAddress,
            vendorName = vendorName,
            manufacturer = manufacturer,
            modelName = modelName,
            confidence = if (confidenceScore >= 50) Confidence.HIGH else Confidence.MEDIUM,
            presenceState = if (isReachable) PresenceState.CONFIRMED_ACTIVE else PresenceState.PREVIOUSLY_SEEN
        )
    }
}
