package com.brutiful.netprobe.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_identities")
data class DeviceIdentity(
    @PrimaryKey val macAddress: String, // Use MAC as primary key for persistence
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
        return DiscoveredDevice(
            ipAddress = currentIp,
            macAddress = macAddress,
            vendorName = vendorName,
            hostName = hostName,
            mdnsName = mdnsName,
            ssdpFriendlyName = ssdpFriendlyName,
            manufacturer = manufacturer,
            modelName = modelName,
            deviceType = deviceType,
            confidenceScore = confidenceScore,
            isReachable = isReachable
        )
    }
}
