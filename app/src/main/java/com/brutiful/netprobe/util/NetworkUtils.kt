package com.brutiful.netprobe.util

import android.net.wifi.ScanResult

object NetworkUtils {
    /**
     * Converts Wi-Fi frequency in MHz to a human-readable band label.
     */
    fun getWifiBandLabel(mhz: Int): String {
        return when (mhz) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }
    }

    /**
     * Maps Wi-Fi channel width constants (from ScanResult or WifiInfo) to human-readable labels.
     */
    fun getWifiChannelWidthLabel(constant: Int): String? {
        return when (constant) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
            // CHANNEL_WIDTH_320MHZ was added in API 33. Use literal if not available in current SDK compile version.
            5 -> "320 MHz" 
            else -> null
        }
    }

    /**
     * Formats cellular band information into a human-readable label.
     */
    fun formatCellularBandLabel(type: String, bands: IntArray): String? {
        if (bands.isEmpty()) return null
        val bandStr = bands.joinToString(", ")
        return "$type Band${if (bands.size > 1) "s" else ""} $bandStr"
    }
}
