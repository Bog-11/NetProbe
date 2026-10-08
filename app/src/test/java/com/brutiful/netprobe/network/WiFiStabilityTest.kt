package com.brutiful.netprobe.network

import android.content.ContextWrapper
import com.brutiful.netprobe.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WiFiStabilityTest {

    private class DummyContext : ContextWrapper(null)

    private val repository = WiFiHealthRepository(DummyContext())

    private fun createSampleList(
        rssiValues: List<Int>,
        txSpeed: Int = 100,
        rxSpeed: Int = 100
    ): List<WiFiStabilitySample> {
        return rssiValues.mapIndexed { index, rssi ->
            WiFiStabilitySample(
                timestamp = System.currentTimeMillis() + (index * 1000),
                rssi = rssi,
                txLinkSpeedMbps = txSpeed,
                rxLinkSpeedMbps = rxSpeed
            )
        }
    }

    private fun createRssiStats(
        avg: Double,
        min: Int,
        max: Int,
        stdDev: Double = 2.0
    ): RssiStats {
        val category = when {
            avg >= -55 -> RssiCategory.EXCELLENT
            avg >= -65 -> RssiCategory.GOOD
            avg >= -72 -> RssiCategory.FAIR
            avg >= -80 -> RssiCategory.WEAK
            else -> RssiCategory.VERY_WEAK
        }
        return RssiStats(avgRssi = avg, minRssi = min, maxRssi = max, stdDevRssi = stdDev, category = category)
    }

    private fun createLinkRateStats(
        avgTx: Double = 150.0,
        minTx: Int = 100,
        maxTx: Int = 200,
        avgRx: Double = 150.0,
        minRx: Int = 100,
        maxRx: Int = 200
    ): LinkRateStats {
        return LinkRateStats(
            avgTxMbps = avgTx,
            minTxMbps = minTx,
            maxTxMbps = maxTx,
            avgRxMbps = avgRx,
            minRxMbps = minRx,
            maxRxMbps = maxRx
        )
    }

    @Test
    fun tc01_strongWifi_badWan_classifiedAsUpstreamIssue() {
        val samples = createSampleList(List(60) { -50 })
        val rssiStats = createRssiStats(-50.0, -50, -50)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 10.0, 3.0, 0.0, 1.0),
            IcmpProbeResult("Google DNS", "8.8.8.8", 60, 12, 20.0, 200.0, 50.0, 80.0, 10.0),
            IcmpProbeResult("Cloudflare DNS", "1.1.1.1", 60, 10, 20.0, 200.0, 50.0, 83.3, 12.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 25.0, 50L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = false, statusCode = -1)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.UPSTREAM_CONNECTIVITY_ISSUE, result.primaryClassification)
        assertTrue(result.confidence == ConfidenceLevel.HIGH || result.confidence == ConfidenceLevel.MEDIUM)
    }

    @Test
    fun tc02_weakWifi_stableWan_classifiedAsVeryWeakWifi() {
        val samples = createSampleList(List(60) { -85 })
        val rssiStats = createRssiStats(-85.0, -88, -82)
        val linkRateStats = createLinkRateStats(avgTx = 15.0, minTx = 5, maxTx = 20)

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 57, 2.0, 15.0, 4.0, 5.0, 2.0),
            IcmpProbeResult("Google DNS", "8.8.8.8", 60, 57, 20.0, 40.0, 25.0, 5.0, 3.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 30.0, 60L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = true, statusCode = 204)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.VERY_WEAK_OR_UNSTABLE_WIFI, result.primaryClassification)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun tc03_gatewayIcmpRateLimiting_healthyBrowsing_classifiedAsHealthy() {
        val samples = createSampleList(List(60) { -55 })
        val rssiStats = createRssiStats(-55.0, -58, -52)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 36, 2.0, 10.0, 3.0, 40.0, 2.0, isSuspectedRateLimited = true),
            IcmpProbeResult("Google DNS", "8.8.8.8", 60, 60, 15.0, 25.0, 18.0, 0.0, 2.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 20.0, 40L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = true, statusCode = 204)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.HEALTHY, result.primaryClassification)
        assertTrue(result.secondaryObservations.any { it.contains("rate-limiting", ignoreCase = true) })
    }

    @Test
    fun tc04_dnsPingsOk_dnsQueriesFail_classifiedAsDnsIssue() {
        val samples = createSampleList(List(60) { -58 })
        val rssiStats = createRssiStats(-58.0, -60, -55)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 8.0, 3.0, 0.0, 1.0),
            IcmpProbeResult("Google DNS", "8.8.8.8", 60, 60, 15.0, 25.0, 18.0, 0.0, 2.0)
        )

        val dnsSummary = DnsTestSummary(12, 0, 100.0, 2500.0, 2500L, mapOf("TIMEOUT" to 12))
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = false, statusCode = -1)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.DNS_ISSUE, result.primaryClassification)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun tc05_publicIcmpRateLimiting_httpsWorks_classifiedAsHealthy() {
        val samples = createSampleList(List(60) { -52 })
        val rssiStats = createRssiStats(-52.0, -55, -50)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 8.0, 3.0, 0.0, 1.0),
            IcmpProbeResult("Google DNS", "8.8.8.8", 60, 24, 15.0, 25.0, 18.0, 60.0, 2.0, isSuspectedRateLimited = true)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 20.0, 40L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = true, statusCode = 204, ttfbMs = 80)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.HEALTHY, result.primaryClassification)
        assertTrue(result.secondaryObservations.any { it.contains("rate-limiting", ignoreCase = true) })
    }

    @Test
    fun tc06_captivePortalRedirect_classifiedAsCaptivePortal() {
        val samples = createSampleList(List(60) { -55 })
        val rssiStats = createRssiStats(-55.0, -58, -52)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 8.0, 3.0, 0.0, 1.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 20.0, 40L)
        val httpsProbes = List(12) {
            HttpsProbeResult(
                url = "https://connectivitycheck.gstatic.com/generate_204",
                statusCode = 302,
                isSuccess = false,
                isCaptivePortalDetected = true,
                redirectUrl = "http://192.168.1.1/login.html"
            )
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.CAPTIVE_PORTAL_OR_RESTRICTED, result.primaryClassification)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun tc07_wifiDisconnected_classifiedAsNoWifiConnection() {
        val result = repository.classifyStability(
            isWifiConnected = false,
            samples = emptyList(),
            rssiStats = createRssiStats(-100.0, -100, -100),
            linkRateStats = createLinkRateStats(0.0, 0, 0, 0.0, 0, 0),
            icmpProbes = emptyList(),
            dnsSummary = DnsTestSummary(),
            httpsProbes = emptyList(),
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.NO_WIFI_CONNECTION, result.primaryClassification)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun tc08_unknownLinkSpeed_handledWithoutError() {
        val samples = createSampleList(List(60) { -60 }, txSpeed = -1, rxSpeed = -1)
        val rssiStats = createRssiStats(-60.0, -62, -58)
        val linkRateStats = createLinkRateStats(0.0, 0, 0, 0.0, 0, 0)

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 8.0, 3.0, 0.0, 1.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 20.0, 40L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = true, statusCode = 204)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics()
        )

        assertEquals(WiFiHealthClassification.HEALTHY, result.primaryClassification)
    }

    @Test
    fun tc09_mloMultiLinkRssiVariance_stableGateway_classifiedAsHealthy() {
        val rssiValues = List(60) { i -> if (i % 2 == 0) -55 else -68 }
        val samples = createSampleList(rssiValues)
        val rssiStats = createRssiStats(-61.5, -68, -55, stdDev = 6.5)
        val linkRateStats = createLinkRateStats()

        val icmpProbes = listOf(
            IcmpProbeResult("Gateway", "192.168.1.1", 60, 60, 2.0, 8.0, 3.0, 0.0, 1.0)
        )

        val dnsSummary = DnsTestSummary(12, 12, 0.0, 20.0, 40L)
        val httpsProbes = List(12) {
            HttpsProbeResult("https://connectivitycheck.gstatic.com/generate_204", isSuccess = true, statusCode = 204)
        }

        val result = repository.classifyStability(
            isWifiConnected = true,
            samples = samples,
            rssiStats = rssiStats,
            linkRateStats = linkRateStats,
            icmpProbes = icmpProbes,
            dnsSummary = dnsSummary,
            httpsProbes = httpsProbes,
            correlation = CorrelationMetrics(isGatewayDegradationCorrelatedWithRssiDrop = false)
        )

        assertEquals(WiFiHealthClassification.HEALTHY, result.primaryClassification)
    }
}
