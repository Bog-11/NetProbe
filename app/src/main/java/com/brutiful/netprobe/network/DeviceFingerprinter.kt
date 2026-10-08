package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.DeviceFingerprint
import com.brutiful.netprobe.model.DiscoveredDevice

object DeviceFingerprinter {
    fun fingerprint(existing: DiscoveredDevice, fingerprint: DeviceFingerprint): DiscoveredDevice {
        return existing
    }
}
