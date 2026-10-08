package com.brutiful.netprobe.network.discovery

import java.net.InetAddress

data class SubnetScanRange(
    val candidateAddresses: List<InetAddress>,
    val totalHostsInSubnet: Long,
    val scannedHostCount: Int,
    val isPartialCoverage: Boolean,
    val networkAddressStr: String,
    val prefixLength: Int
)

object SubnetCalculator {

    fun calculateScanRange(
        phoneIp: InetAddress,
        prefixLength: Int,
        maxHostCap: Int = 254
    ): SubnetScanRange {
        val clampedPrefix = prefixLength.coerceIn(0, 32)
        val phoneLong = ipToLong(phoneIp)
        val mask = calculateMask(clampedPrefix)
        val networkLong = phoneLong and mask
        val hostCount = calculateHostCount(clampedPrefix)

        if (clampedPrefix == 32) {
            // /32 is a single host (the phone itself)
            return SubnetScanRange(
                candidateAddresses = emptyList(),
                totalHostsInSubnet = 1,
                scannedHostCount = 0,
                isPartialCoverage = false,
                networkAddressStr = "${phoneIp.hostAddress}/32",
                prefixLength = 32
            )
        }

        if (clampedPrefix == 31) {
            // RFC 3021 point-to-point: both addresses are usable hosts
            val allIps = listOf(
                longToInetAddress(networkLong),
                longToInetAddress(networkLong + 1L)
            )
            val candidates = allIps.filter { it != phoneIp }
            return SubnetScanRange(
                candidateAddresses = candidates,
                totalHostsInSubnet = 2,
                scannedHostCount = candidates.size,
                isPartialCoverage = false,
                networkAddressStr = "${longToInetAddress(networkLong).hostAddress}/31",
                prefixLength = 31
            )
        }

        val minUsableLong = networkLong + 1L
        val maxUsableLong = networkLong + hostCount - 2L
        val usableHostCount = (maxUsableLong - minUsableLong + 1L).coerceAtLeast(0L)

        val isPartial = usableHostCount > maxHostCap
        val (startLong, endLong) = if (!isPartial) {
            Pair(minUsableLong, maxUsableLong)
        } else {
            val halfCap = maxHostCap / 2
            var start = (phoneLong - halfCap).coerceAtLeast(minUsableLong)
            var end = start + maxHostCap - 1L
            if (end > maxUsableLong) {
                end = maxUsableLong
                start = (end - maxHostCap + 1L).coerceAtLeast(minUsableLong)
            }
            Pair(start, end)
        }

        val candidates = mutableListOf<InetAddress>()
        var current = startLong
        while (current <= endLong) {
            val addr = longToInetAddress(current)
            if (addr != phoneIp) {
                candidates.add(addr)
            }
            current++
        }

        val netStr = "${longToInetAddress(networkLong).hostAddress}/$clampedPrefix"

        return SubnetScanRange(
            candidateAddresses = candidates,
            totalHostsInSubnet = usableHostCount,
            scannedHostCount = candidates.size + (if (candidates.size < (endLong - startLong + 1L)) 1 else 0),
            isPartialCoverage = isPartial,
            networkAddressStr = netStr,
            prefixLength = clampedPrefix
        )
    }

    fun ipToLong(ip: InetAddress): Long {
        val bytes = ip.address
        if (bytes.size != 4) return 0L
        return ((bytes[0].toLong() and 0xFF) shl 24) or
                ((bytes[1].toLong() and 0xFF) shl 16) or
                ((bytes[2].toLong() and 0xFF) shl 8) or
                (bytes[3].toLong() and 0xFF)
    }

    fun longToInetAddress(ipLong: Long): InetAddress {
        val bytes = byteArrayOf(
            ((ipLong ushr 24) and 0xFF).toByte(),
            ((ipLong ushr 16) and 0xFF).toByte(),
            ((ipLong ushr 8) and 0xFF).toByte(),
            (ipLong and 0xFF).toByte()
        )
        return InetAddress.getByAddress(bytes)
    }

    private fun calculateMask(prefix: Int): Long {
        if (prefix <= 0) return 0L
        if (prefix >= 32) return 0xFFFFFFFFL
        return (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }

    private fun calculateHostCount(prefix: Int): Long {
        if (prefix >= 32) return 1L
        if (prefix <= 0) return 0x100000000L // 2^32
        return 1L shl (32 - prefix)
    }
}
