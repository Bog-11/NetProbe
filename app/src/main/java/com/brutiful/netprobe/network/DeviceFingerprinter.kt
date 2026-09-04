package com.brutiful.netprobe.network

import android.util.Log
import com.brutiful.netprobe.model.DeviceFingerprint
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.FingerprintSource

object DeviceFingerprinter {
    private const val TAG = "DeviceFingerprinter"

    fun fingerprint(existing: DiscoveredDevice, fingerprint: DeviceFingerprint): DiscoveredDevice {
        Log.d(TAG, "Applying fingerprint from ${fingerprint.source} for ${existing.ipAddress}. Current score: ${existing.confidenceScore}, New score: ${fingerprint.confidence}")

        // Rules:
        // 1. Never overwrite strong identity with weaker one.
        // 2. Accumulate evidence.
        // 3. Prefer metadata-based names over hostnames.

        val newEvidence = existing.evidence.toMutableMap()
        fingerprint.rawMetadata.forEach { (k, v) -> newEvidence["${fingerprint.source}:$k"] = v }
        
        // Add specific evidence labels
        fingerprint.vendor?.let { newEvidence["Vendor:${fingerprint.source}"] = it }
        fingerprint.model?.let { newEvidence["Model:${fingerprint.source}"] = it }

        val shouldUpdateMainFields = fingerprint.confidence >= existing.confidenceScore || existing.manufacturer == null

        return existing.copy(
            vendorName = if (shouldUpdateMainFields) fingerprint.vendor ?: existing.vendorName else existing.vendorName,
            manufacturer = if (shouldUpdateMainFields) fingerprint.vendor ?: existing.manufacturer else existing.manufacturer,
            modelName = if (shouldUpdateMainFields) fingerprint.model ?: existing.modelName else existing.modelName,
            deviceType = if (shouldUpdateMainFields) fingerprint.deviceType ?: existing.deviceType else existing.deviceType,
            ssdpFriendlyName = if (fingerprint.source == FingerprintSource.SSDP) fingerprint.friendlyName ?: existing.ssdpFriendlyName else existing.ssdpFriendlyName,
            mdnsName = if (fingerprint.source == FingerprintSource.MDNS) fingerprint.friendlyName ?: existing.mdnsName else existing.mdnsName,
            hostName = if (fingerprint.source == FingerprintSource.HOSTNAME || existing.hostName == null) fingerprint.hostName ?: existing.hostName else existing.hostName,
            confidenceScore = maxOf(existing.confidenceScore, fingerprint.confidence),
            evidence = newEvidence,
            discoverySource = if (shouldUpdateMainFields) fingerprint.source.name else existing.discoverySource
        ).let { applyGlobalHeuristics(it) }
    }

    private fun applyGlobalHeuristics(device: DiscoveredDevice): DiscoveredDevice {
        var updated = device
        val evidence = device.evidence.toMutableMap()

        // 1. Printer detection
        val isPrinterMac = device.vendorName?.contains("HP", ignoreCase = true) == true || 
                          device.vendorName?.contains("Epson", ignoreCase = true) == true ||
                          device.vendorName?.contains("Canon", ignoreCase = true) == true ||
                          device.vendorName?.contains("Brother", ignoreCase = true) == true
        val hasPrinterPorts = device.openPorts.any { it in listOf(9100, 515, 631) }
        val hasPrinterMdns = device.mdnsName?.contains("printer", ignoreCase = true) == true || 
                            device.evidence.values.any { it.contains("_printer", ignoreCase = true) }

        if (isPrinterMac && hasPrinterPorts || hasPrinterMdns) {
            updated = updated.copy(
                deviceType = "Printer",
                confidenceScore = maxOf(updated.confidenceScore, 75)
            )
            evidence["Heuristic"] = "Printer Profile Matched"
        }

        // 2. Media Streaming / TV
        val isMediaMdns = device.evidence.values.any { it.contains("_googlecast", ignoreCase = true) || it.contains("_airplay", ignoreCase = true) }
        val isTvVendor = device.vendorName?.contains("Samsung", ignoreCase = true) == true || 
                        device.vendorName?.contains("LG Electronics", ignoreCase = true) == true ||
                        device.vendorName?.contains("Sony", ignoreCase = true) == true
        
        if (isMediaMdns) {
            val type = if (device.evidence.values.any { it.contains("googlecast", ignoreCase = true) }) "Chromecast" else "AirPlay Device"
            updated = updated.copy(
                deviceType = type,
                confidenceScore = maxOf(updated.confidenceScore, 85)
            )
            evidence["Heuristic"] = "Media Streamer Matched"
        } else if (isTvVendor && (device.ssdpFriendlyName != null || device.mdnsName != null)) {
            updated = updated.copy(
                deviceType = "Smart TV",
                confidenceScore = maxOf(updated.confidenceScore, 60)
            )
            evidence["Heuristic"] = "Smart TV Potential"
        }

        // 3. IoT / Smart Home
        if (device.vendorName?.contains("Philips Hue", ignoreCase = true) == true || 
            device.ssdpFriendlyName?.contains("Philips hue", ignoreCase = true) == true) {
            updated = updated.copy(
                deviceType = "Smart Hub",
                manufacturer = "Philips",
                modelName = "Hue Bridge",
                confidenceScore = maxOf(updated.confidenceScore, 95)
            )
            evidence["Heuristic"] = "Philips Hue Matched"
        }
        
        if (device.vendorName?.contains("Espressif", ignoreCase = true) == true || 
            device.hostName?.contains("espressif", ignoreCase = true) == true) {
            updated = updated.copy(
                deviceType = "IoT Device",
                manufacturer = "Espressif (ESP8266/ESP32)",
                confidenceScore = maxOf(updated.confidenceScore, 40)
            )
            evidence["Heuristic"] = "Generic IoT (Espressif)"
        }

        // 4. Networking Gear
        val isNetVendor = device.vendorName?.contains("Cisco", ignoreCase = true) == true || 
                         device.vendorName?.contains("Ubiquiti", ignoreCase = true) == true ||
                         device.vendorName?.contains("TP-LINK", ignoreCase = true) == true ||
                         device.vendorName?.contains("NETGEAR", ignoreCase = true) == true
        
        if (isNetVendor && device.confidenceScore < 40) {
            updated = updated.copy(
                deviceType = "Network Equipment",
                confidenceScore = maxOf(updated.confidenceScore, 30)
            )
            evidence["Heuristic"] = "Network Vendor Matched"
        }

        // 5. Apple Device Refinement
        val isAppleVendor = device.vendorName?.contains("Apple", ignoreCase = true) == true
        val appleEvidence = device.evidence.values.any { 
            it.contains("Apple", ignoreCase = true) || 
            it.contains("iPhone", ignoreCase = true) || 
            it.contains("iPad", ignoreCase = true) 
        }
        
        if (isAppleVendor || appleEvidence) {
            val hasAirPlay = device.openPorts.contains(7000) || device.evidence.values.any { it.contains("_airplay", ignoreCase = true) }
            val hasAppleTvV2 = device.evidence.values.any { it.contains("_appletv-v2", ignoreCase = true) }
            val hasRaop = device.evidence.values.any { it.contains("_raop", ignoreCase = true) }
            
            // Only refine if we don't have a high-confidence specialized type already
            val isSpecialized = updated.deviceType in listOf("Printer", "IP Camera", "Smart TV", "Smart Hub")
            
            if (!isSpecialized) {
                if (hasAppleTvV2 || (isAppleVendor && hasAirPlay && device.openPorts.contains(3689))) {
                    updated = updated.copy(
                        deviceType = "Apple TV",
                        confidenceScore = maxOf(updated.confidenceScore, 90)
                    )
                    evidence["Heuristic"] = "Apple TV Pattern"
                } else if (hasRaop) {
                    updated = updated.copy(
                        deviceType = "HomePod / Apple Audio",
                        confidenceScore = maxOf(updated.confidenceScore, 80)
                    )
                    evidence["Heuristic"] = "Apple Audio Pattern"
                } else if (isAppleVendor && updated.deviceType == null) {
                    updated = updated.copy(
                        deviceType = "Apple Device",
                        confidenceScore = maxOf(updated.confidenceScore, 25)
                    )
                }
            }
        }

        // 6. False Positive Protection
        // If it's identified as an "iPhone" but has ONVIF or other non-mobile services, override it.
        if (updated.hostName?.contains("iPhone", ignoreCase = true) == true) {
            val hasOnvif = device.discoverySource == "ONVIF" || device.openPorts.contains(3702)
            val hasIndustrialPorts = device.openPorts.any { it in listOf(502, 102, 44818) }
            
            if (hasOnvif || hasIndustrialPorts) {
                evidence["Identity Warning"] = "Overriding mock iPhone hostname for specialized hardware"
                if (hasOnvif && updated.deviceType == null) {
                    updated = updated.copy(deviceType = "IP Camera", confidenceScore = 60)
                }
            }
        }

        return updated.copy(evidence = evidence)
    }
}
