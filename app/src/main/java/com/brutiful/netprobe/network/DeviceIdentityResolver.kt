package com.brutiful.netprobe.network

import android.util.Log
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DeviceIdentity

object DeviceIdentityResolver {
    private const val TAG = "IdentityResolver"

    fun merge(existing: DiscoveredDevice, newInfo: DiscoveredDevice): DiscoveredDevice {
        // Preference: Strong identities (Score > 20) should not be overwritten by weak ones (Score < 10)
        val isExistingStrong = existing.confidenceScore >= 20
        val isNewWeak = newInfo.confidenceScore < 10 && newInfo.hostName != null
        
        if (isExistingStrong && isNewWeak) {
            Log.d(TAG, "Preserving strong identity for ${existing.ipAddress}. Ignoring weak hostname update: ${newInfo.hostName}")
            return existing.copy(
                isReachable = existing.isReachable || newInfo.isReachable,
                openPorts = (existing.openPorts + newInfo.openPorts).distinct()
            )
        }

        val updatedEvidence = existing.evidence.toMutableMap()
        newInfo.evidence.forEach { (k, v) -> updatedEvidence[k] = v }
        
        // Add implicit evidence based on incoming data
        newInfo.ssdpFriendlyName?.let { updatedEvidence["SSDP"] = it }
        newInfo.mdnsName?.let { updatedEvidence["mDNS"] = it }
        newInfo.vendorName?.let { updatedEvidence["MAC Vendor"] = it }
        newInfo.manufacturer?.let { updatedEvidence["Manufacturer"] = it }

        val merged = existing.copy(
            macAddress = newInfo.macAddress ?: existing.macAddress,
            vendorName = newInfo.vendorName ?: existing.vendorName,
            hostName = newInfo.hostName ?: existing.hostName,
            mdnsName = newInfo.mdnsName ?: existing.mdnsName,
            ssdpFriendlyName = newInfo.ssdpFriendlyName ?: existing.ssdpFriendlyName,
            manufacturer = newInfo.manufacturer ?: existing.manufacturer,
            modelName = newInfo.modelName ?: existing.modelName,
            deviceType = newInfo.deviceType ?: existing.deviceType,
            confidenceScore = maxOf(existing.confidenceScore, newInfo.confidenceScore),
            isReachable = existing.isReachable || newInfo.isReachable,
            openPorts = (existing.openPorts + newInfo.openPorts).distinct(),
            discoverySource = newInfo.discoverySource ?: existing.discoverySource,
            evidence = updatedEvidence
        )

        return applyHeuristics(merged)
    }

    private fun applyHeuristics(device: DiscoveredDevice): DiscoveredDevice {
        var updated = device
        val evidence = device.evidence.toMutableMap()
        
        // Hikvision detection heuristic
        val isHikMac = device.vendorName?.contains("Hikvision", ignoreCase = true) == true
        val hasOnvifCamera = device.deviceType == "IP Camera"
        val hasHikKeywords = listOf(device.ssdpFriendlyName, device.manufacturer, device.modelName, device.hostName)
            .any { it?.contains("HIK", ignoreCase = true) == true }
        
        val cctvPorts = listOf(8000, 554, 80)
        val openCctvPorts = device.openPorts.filter { it in cctvPorts }

        if ((isHikMac && (hasOnvifCamera || openCctvPorts.isNotEmpty())) || (hasOnvifCamera && hasHikKeywords)) {
            evidence["Heuristic"] = "Hikvision Pattern Matched"
            updated = updated.copy(
                manufacturer = "Hikvision",
                deviceType = "Hikvision Camera",
                confidenceScore = maxOf(updated.confidenceScore, 85),
                evidence = evidence
            )
            Log.i(TAG, "Heuristic match: Hikvision Camera at ${device.ipAddress}. Confidence: ${updated.confidenceScore}")
        }

        return updated
    }

    fun fromEntity(entity: DeviceIdentity, currentIp: String): DiscoveredDevice {
        return entity.toDiscoveredDevice(currentIp)
    }

    fun toEntity(device: DiscoveredDevice): DeviceIdentity? {
        val mac = device.macAddress ?: return null
        return DeviceIdentity(
            macAddress = mac,
            lastIpAddress = device.ipAddress,
            vendorName = device.vendorName,
            hostName = device.hostName,
            mdnsName = device.mdnsName,
            ssdpFriendlyName = device.ssdpFriendlyName,
            manufacturer = device.manufacturer,
            modelName = device.modelName,
            deviceType = device.deviceType,
            confidenceScore = device.confidenceScore,
            lastSeen = System.currentTimeMillis()
        )
    }
}
