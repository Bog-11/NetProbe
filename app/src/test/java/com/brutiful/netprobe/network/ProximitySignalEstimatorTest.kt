package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.*
import org.junit.Assert.*
import org.junit.Test

class ProximitySignalEstimatorTest {

    @Test
    fun testRssiSmoothingAndVariance() {
        val estimator = ProximitySignalEstimator()
        estimator.addSample(-50)
        estimator.addSample(-52)
        estimator.addSample(-48)

        val smoothed = estimator.getSmoothedRssi()
        assertNotNull(smoothed)
        assertTrue(smoothed!! >= -55.0 && smoothed <= -45.0)

        val variance = estimator.getVariance()
        assertNotNull(variance)
        assertTrue(variance!! > 0.0)
    }

    @Test
    fun testTrendClassification() {
        val estimator = ProximitySignalEstimator()
        // Strong getting stronger trend
        estimator.addSample(-80)
        estimator.addSample(-78)
        estimator.addSample(-75)
        estimator.addSample(-60)
        estimator.addSample(-55)
        estimator.addSample(-50)

        assertEquals(SignalTrend.GETTING_STRONGER, estimator.getTrend())

        estimator.clear()
        // Strong getting weaker trend
        estimator.addSample(-50)
        estimator.addSample(-52)
        estimator.addSample(-55)
        estimator.addSample(-75)
        estimator.addSample(-78)
        estimator.addSample(-80)

        assertEquals(SignalTrend.GETTING_WEAKER, estimator.getTrend())
    }

    @Test
    fun testProximityBandThresholds() {
        val estimator = ProximitySignalEstimator()
        assertEquals(ProximityBand.VERY_CLOSE, estimator.computeProximityBand(-45.0))
        assertEquals(ProximityBand.NEAR, estimator.computeProximityBand(-55.0))
        assertEquals(ProximityBand.NEARBY, estimator.computeProximityBand(-65.0))
        assertEquals(ProximityBand.FAR, estimator.computeProximityBand(-75.0))
        assertEquals(ProximityBand.VERY_FAR, estimator.computeProximityBand(-85.0))
    }

    @Test
    fun testLogDistanceRangeCalculationAndRounding() {
        val estimator = ProximitySignalEstimator()
        val estimate = estimator.estimateDistance(
            smoothedRssi = -45.0,
            txPower = -59,
            txPowerSource = TxPowerSource.ADVERTISED,
            profile = EnvironmentProfile.TYPICAL_INDOOR,
            calibration = null
        )
        assertNotNull(estimate)
        assertEquals("Approx. 1–3 m", estimate!!.displayLabel)
    }

    @Test
    fun testDeterministicConfidenceScoringHardCaps() {
        val estimator = ProximitySignalEstimator()
        
        // Target assumed ceiling fallback cap rule check
        val (confAssumed, _) = estimator.calculateConfidence(
            smoothedRssi = -55.0,
            variance = 2.0,
            sampleCount = 20,
            txPowerSource = TxPowerSource.ASSUMED,
            isStale = false,
            isRandomizedBle = false,
            profile = EnvironmentProfile.TYPICAL_INDOOR,
            hasCalibration = false,
            targetId = "00:11:22:33:44:55"
        )
        assertEquals(ProximityConfidence.LOW, confAssumed)

        // Randomized private BLE target cap ceiling verification
        val (confRandom, _) = estimator.calculateConfidence(
            smoothedRssi = -50.0,
            variance = 1.0,
            sampleCount = 25,
            txPowerSource = TxPowerSource.ADVERTISED,
            isStale = false,
            isRandomizedBle = true,
            profile = EnvironmentProfile.TYPICAL_INDOOR,
            hasCalibration = false,
            targetId = "77:88:99:AA:BB:CC"
        )
        assertEquals(ProximityConfidence.LOW, confRandom)

        // Stale condition validation
        val (confStale, _) = estimator.calculateConfidence(
            smoothedRssi = -50.0,
            variance = 1.0,
            sampleCount = 25,
            txPowerSource = TxPowerSource.ADVERTISED,
            isStale = true,
            isRandomizedBle = false,
            profile = EnvironmentProfile.TYPICAL_INDOOR,
            hasCalibration = false,
            targetId = "77:88:99:AA:BB:CC"
        )
        assertEquals(ProximityConfidence.VERY_LOW, confStale)
    }
}
