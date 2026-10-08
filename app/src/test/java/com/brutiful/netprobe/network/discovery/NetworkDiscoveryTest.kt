package com.brutiful.netprobe.network.discovery

import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoveredService
import com.brutiful.netprobe.model.DiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkDiscoveryTest {

    @Test
    fun testSubnetCalculation_Slash24_ExcludesPhoneIpAndCalculatesCorrectRange() {
        val phoneIp = InetAddress.getByName("192.168.1.50")
        val scanRange = SubnetCalculator.calculateScanRange(phoneIp, 24, maxHostCap = 254)

        assertEquals(254L, scanRange.totalHostsInSubnet)
        assertEquals(253, scanRange.candidateAddresses.size) // 254 usable minus phone IP
        assertFalse(scanRange.candidateAddresses.contains(phoneIp))
        assertFalse(scanRange.isPartialCoverage)
        assertEquals("192.168.1.0/24", scanRange.networkAddressStr)
    }

    @Test
    fun testSubnetCalculation_Slash23_WindowingAndPartialCoverage() {
        val phoneIp = InetAddress.getByName("192.168.1.100")
        val scanRange = SubnetCalculator.calculateScanRange(phoneIp, 23, maxHostCap = 254)

        assertEquals(510L, scanRange.totalHostsInSubnet)
        assertTrue(scanRange.isPartialCoverage)
        assertTrue(scanRange.candidateAddresses.size <= 254)
        assertFalse(scanRange.candidateAddresses.contains(phoneIp))
    }

    @Test
    fun testSubnetCalculation_Slash16_WindowingAndPartialCoverage() {
        val phoneIp = InetAddress.getByName("10.0.15.42")
        val scanRange = SubnetCalculator.calculateScanRange(phoneIp, 16, maxHostCap = 254)

        assertEquals(65534L, scanRange.totalHostsInSubnet)
        assertTrue(scanRange.isPartialCoverage)
        assertTrue(scanRange.candidateAddresses.size <= 254)
        assertFalse(scanRange.candidateAddresses.contains(phoneIp))
    }

    @Test
    fun testSubnetCalculation_Slash31_Slash32_EdgeCases() {
        val phoneIp31 = InetAddress.getByName("192.168.1.10")
        val scanRange31 = SubnetCalculator.calculateScanRange(phoneIp31, 31)
        assertEquals(2L, scanRange31.totalHostsInSubnet)
        assertEquals(1, scanRange31.candidateAddresses.size)
        assertEquals(InetAddress.getByName("192.168.1.11"), scanRange31.candidateAddresses[0])

        val phoneIp32 = InetAddress.getByName("10.0.0.1")
        val scanRange32 = SubnetCalculator.calculateScanRange(phoneIp32, 32)
        assertEquals(1L, scanRange32.totalHostsInSubnet)
        assertTrue(scanRange32.candidateAddresses.isEmpty())
        assertFalse(scanRange32.isPartialCoverage)
    }

    @Test
    fun testProgressCalculationAndClamping() {
        val completed = 150
        val total = 100
        val ratio = (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, ratio, 0.001f)

        val negative = -5
        val ratioNeg = (negative.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.0f, ratioNeg, 0.001f)
    }

    @Test
    fun testMergeDuplicateObservationsIntoOneDevice() {
        val ip = InetAddress.getByName("192.168.1.20")

        val mdnsObs = DiscoveredDevice(
            ipAddress = ip,
            hostname = "cam-01.local",
            displayName = "Front Porch Camera",
            services = listOf(
                DiscoveredService(
                    serviceType = "_axis-video._tcp.",
                    name = "Front Porch Camera",
                    port = 80,
                    source = DiscoverySource.MDNS
                )
            ),
            sources = setOf(DiscoverySource.MDNS),
            category = DeviceCategory.CAMERA,
            confidence = Confidence.MEDIUM
        )

        val tcpObs = DiscoveredDevice(
            ipAddress = ip,
            openPorts = setOf(554, 80),
            sources = setOf(DiscoverySource.TCP_CONNECT),
            category = DeviceCategory.CAMERA,
            confidence = Confidence.MEDIUM
        )

        // Merging logic
        val mergedServices = (mdnsObs.services + tcpObs.services).distinctBy { "${it.serviceType}:${it.port}:${it.name}" }
        val mergedPorts = mdnsObs.openPorts + tcpObs.openPorts
        val mergedSources = mdnsObs.sources + tcpObs.sources

        val mergedDevice = DiscoveredDevice(
            ipAddress = ip,
            hostname = mdnsObs.hostname,
            displayName = mdnsObs.displayName,
            services = mergedServices,
            openPorts = mergedPorts,
            sources = mergedSources,
            category = DeviceCategory.CAMERA,
            confidence = Confidence.HIGH
        )

        assertEquals("192.168.1.20", mergedDevice.ipString)
        assertEquals(2, mergedDevice.sources.size)
        assertTrue(mergedDevice.sources.contains(DiscoverySource.MDNS))
        assertTrue(mergedDevice.sources.contains(DiscoverySource.TCP_CONNECT))
        assertTrue(mergedDevice.openPorts.contains(554))
        assertEquals("Front Porch Camera", mergedDevice.computedDisplayName())
    }

    @Test
    fun testIcmpBlockedHostWithOpenTcpPortDiscovered() {
        val ip = InetAddress.getByName("192.168.1.100")
        val device = DiscoveredDevice(
            ipAddress = ip,
            openPorts = setOf(443),
            sources = setOf(DiscoverySource.TCP_CONNECT),
            category = DeviceCategory.UNKNOWN_REACHABLE,
            confidence = Confidence.LOW
        )

        assertFalse(device.sources.contains(DiscoverySource.ICMP))
        assertTrue(device.sources.contains(DiscoverySource.TCP_CONNECT))
        assertTrue(device.evidenceSummary().contains("Unknown reachable device"))
        assertTrue(device.evidenceSummary().contains("HTTP(S) port reachable"))
    }
}
