package com.brutiful.netprobe.util

import com.brutiful.netprobe.model.DiscoveredDevice
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class HostnameSanitizationTest {

    @Test
    fun testSanitizeStationSuffix() {
        assertEquals("my-macbook", NetworkUtils.sanitizeHostName("my-macbook.station"))
        assertEquals("my-macbook", NetworkUtils.sanitizeHostName("my-macbook.station."))
        assertEquals("workstation-01", NetworkUtils.sanitizeHostName("workstation-01.STATION"))
        assertEquals("workstation-01", NetworkUtils.sanitizeHostName("workstation-01.STATION."))
    }

    @Test
    fun testSanitizeOtherLocalSuffixes() {
        assertEquals("iphone", NetworkUtils.sanitizeHostName("iphone.local"))
        assertEquals("iphone", NetworkUtils.sanitizeHostName("iphone.local."))
        assertEquals("router", NetworkUtils.sanitizeHostName("router.lan"))
        assertEquals("printer", NetworkUtils.sanitizeHostName("printer.home.arpa"))
        assertEquals("desktop", NetworkUtils.sanitizeHostName("desktop.home"))
        assertEquals("device", NetworkUtils.sanitizeHostName("device.domain"))
    }

    @Test
    fun testNestedSuffixes() {
        assertEquals("device", NetworkUtils.sanitizeHostName("device.local.station"))
        assertEquals("device", NetworkUtils.sanitizeHostName("device.lan.station."))
    }

    @Test
    fun testNullOrIp() {
        assertNull(NetworkUtils.sanitizeHostName(null))
        assertNull(NetworkUtils.sanitizeHostName(""))
        assertNull(NetworkUtils.sanitizeHostName("   "))
        assertNull(NetworkUtils.sanitizeHostName("192.168.1.1"))
        assertNull(NetworkUtils.sanitizeHostName("192.168.1.1.station"))
    }

    @Test
    fun testDiscoveredDeviceDisplayName() {
        val deviceWithStation = DiscoveredDevice(
            ipAddress = InetAddress.getByName("192.168.1.50"),
            hostname = NetworkUtils.sanitizeHostName("smart-tv.station")
        )
        assertEquals("smart-tv", deviceWithStation.computedDisplayName())

        val deviceWithMdnsStation = DiscoveredDevice(
            ipAddress = InetAddress.getByName("192.168.1.51"),
            displayName = NetworkUtils.sanitizeHostName("living-room-speaker.station.")
        )
        assertEquals("living-room-speaker", deviceWithMdnsStation.computedDisplayName())
    }
}
