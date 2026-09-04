package com.brutiful.netprobe.network

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress

object NetworkUtils {

    fun getMacFromArp(ip: String): String? {
        Log.d("NetworkUtils", "Attempting to read /proc/net/arp for IP: $ip")
        try {
            val file = java.io.File("/proc/net/arp")
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

    fun getHostname(ip: String): String? {
        return try {
            val address = InetAddress.getByName(ip)
            val hostname = address.canonicalHostName
            if (hostname != ip) hostname else null
        } catch (e: Exception) {
            null
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
