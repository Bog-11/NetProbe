package com.brutiful.netprobe.util

import android.net.wifi.ScanResult
import android.util.Log
import android.util.Patterns
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress

object NetworkUtils {

    fun getMacFromArp(ip: String): String? {
        Log.d("NetworkUtils", "Attempting to read /proc/net/arp for IP: $ip")
        try {
            val file = File("/proc/net/arp")
            if (!file.exists()) {
                Log.e("NetworkUtils", "/proc/net/arp does not exist (likely Android 10+ restriction)")
                return null
            }
            if (!file.canRead()) {
                Log.e("NetworkUtils", "/proc/net/arp exists but is not readable (likely Android 10+ restriction)")
                return null
            }
            
            val br = BufferedReader(FileReader(file))
            var line: String?
            var foundCount = 0
            while (br.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size >= 4 && ip == parts[0]) {
                    val mac = parts[3]
                    if (mac.matches("..:..:..:..:..:..".toRegex()) && mac != "00:00:00:00:00:00") {
                        Log.d("NetworkUtils", "Found MAC for $ip: $mac")
                        return mac
                    }
                }
                foundCount++
            }
            br.close()
            Log.d("NetworkUtils", "Finished reading /proc/net/arp. Scanned $foundCount lines. No match for $ip.")
        } catch (e: Exception) {
            Log.e("NetworkUtils", "Error reading /proc/net/arp: ${e.message}", e)
        }
        return null
    }

    /**
     * Sanitizes hostnames and device names by removing trailing domain suffixes
     * like .station, .local, .lan, .home, .home.arpa, trailing dots, etc.
     */
    fun sanitizeHostName(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        var name = rawName.trim()

        if (name.endsWith(".")) {
            name = name.dropLast(1).trim()
        }

        val suffixesToStrip = listOf(
            ".station",
            ".local",
            ".lan",
            ".home.arpa",
            ".home",
            ".domain",
            ".router",
            "._workstation._tcp",
            "._workstation"
        )

        var stripped = true
        while (stripped) {
            stripped = false
            for (suffix in suffixesToStrip) {
                if (name.endsWith(suffix, ignoreCase = true)) {
                    name = name.substring(0, name.length - suffix.length).trim()
                    if (name.endsWith(".")) {
                        name = name.dropLast(1).trim()
                    }
                    stripped = true
                    break
                }
            }
        }

        if (name.isBlank() || isValidIp(name)) {
            return null
        }

        return name
    }

    fun getHostname(ip: String): String? {
        return try {
            val address = InetAddress.getByName(ip)
            val hostname = address.canonicalHostName
            if (hostname != ip) sanitizeHostName(hostname) else null
        } catch (e: Exception) {
            null
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

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
     * Converts Wi-Fi frequency in MHz to a channel number.
     */
    fun getWifiChannelFromFrequency(mhz: Int): Int? {
        return when {
            mhz == 2484 -> 14
            mhz in 2412..2472 -> (mhz - 2412) / 5 + 1
            mhz in 5170..5825 -> (mhz - 5170) / 5 + 34
            mhz in 5945..7105 -> (mhz - 5945) / 5 + 1
            else -> null
        }
    }

    private val IPV4_REGEX = Regex("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")

    /**
     * Validates if a string is a valid IPv4 or IPv6 address.
     */
    fun isValidIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        return try {
            val pattern = Patterns.IP_ADDRESS
            if (pattern != null) {
                pattern.matcher(ip).matches()
            } else {
                IPV4_REGEX.matches(ip) || ip.contains(":")
            }
        } catch (_: Throwable) {
            IPV4_REGEX.matches(ip) || ip.contains(":")
        }
    }

    /**
     * Validates if a string is a valid domain name.
     */
    fun isValidDomain(domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        return try {
            val pattern = Patterns.DOMAIN_NAME
            if (pattern != null) {
                pattern.matcher(domain).matches()
            } else {
                domain.contains(".")
            }
        } catch (_: Throwable) {
            domain.contains(".")
        }
    }

    /**
     * Validates if a string is either a valid IP address or a valid domain name.
     */
    fun isValidIpOrDomain(input: String?): Boolean {
        return isValidIp(input) || isValidDomain(input)
    }

    /**
     * Sanitizes SSID by removing surrounding quotes and handling unknown SSID placeholders.
     */
    fun sanitizeSsid(ssid: String?): String? {
        return ssid?.removeSurrounding("\"")?.takeIf {
            it.isNotEmpty() && it != "<unknown ssid>"
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
