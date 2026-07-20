package com.brutiful.netprobe.network

import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress

object NetworkUtils {

    fun getMacFromArp(ip: String): String? {
        try {
            val br = BufferedReader(FileReader("/proc/net/arp"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size >= 4 && ip == parts[0]) {
                    val mac = parts[3]
                    if (mac.matches("..:..:..:..:..:..".toRegex()) && mac != "00:00:00:00:00:00") {
                        return mac
                    }
                }
            }
            br.close()
        } catch (e: Exception) {
            // Ignore
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
