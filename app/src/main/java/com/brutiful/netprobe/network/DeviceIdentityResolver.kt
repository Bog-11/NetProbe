package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceIdentity
import com.brutiful.netprobe.model.DiscoveredDevice

object DeviceIdentityResolver {

    fun fromEntity(entity: DeviceIdentity, currentIp: String): DiscoveredDevice {
        return entity.toDiscoveredDevice(currentIp)
    }

    fun toEntity(device: DiscoveredDevice): DeviceIdentity? {
        val mac = device.macAddress ?: return null
        val score = when (device.confidence) {
            Confidence.HIGH -> 80
            Confidence.MEDIUM -> 50
            Confidence.LOW -> 20
        }
        return DeviceIdentity(
            macAddress = mac,
            lastIpAddress = device.ipString,
            vendorName = device.vendorName,
            hostName = device.hostname,
            mdnsName = device.displayName,
            ssdpFriendlyName = device.displayName,
            manufacturer = device.manufacturer,
            modelName = device.modelName,
            deviceType = device.category.name,
            confidenceScore = score,
            lastSeen = device.lastSeenMillis
        )
    }
}
