package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.*
import kotlin.math.pow
import kotlin.math.sqrt

class ProximitySignalEstimator {

    private val samples = mutableListOf<Pair<Long, Int>>()
    private val maxWindowMs = 20_000L
    private val maxSampleCount = 40

    fun addSample(rssi: Int, timestamp: Long = System.currentTimeMillis()) {
        samples.add(Pair(timestamp, rssi))
        trimWindow(timestamp)
    }

    fun clear() {
        samples.clear()
    }

    private fun trimWindow(now: Long) {
        samples.removeAll { now - it.first > maxWindowMs }
        while (samples.size > maxSampleCount) {
            samples.removeAt(0)
        }
    }

    fun getRawRssi(): Int? = samples.lastOrNull()?.second

    fun getSmoothedRssi(): Double? {
        if (samples.isEmpty()) return null
        
        // Step 1: Compute rolling median window to filter spikes
        val sortedValues = samples.map { it.second }.sorted()
        val median = if (sortedValues.size % 2 == 1) {
            sortedValues[sortedValues.size / 2].toDouble()
        } else {
            val mid = sortedValues.size / 2
            (sortedValues[mid - 1] + sortedValues[mid]) / 2.0
        }

        // Step 2: Use EMA filter logic to avoid high-frequency oscillations
        var ema = samples.first().second.toDouble()
        val alpha = 0.25
        for (i in 1 until samples.size) {
            ema = alpha * samples[i].second + (1.0 - alpha) * ema
        }
        
        // Return blend of exponential smoothed value and median floor
        return 0.5 * median + 0.5 * ema
    }

    fun getVariance(): Double? {
        if (samples.size < 2) return 0.0
        val values = samples.map { it.second.toDouble() }
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.sum() / (values.size - 1)
    }

    fun getTrend(): SignalTrend {
        if (samples.size < 6) return SignalTrend.UNKNOWN
        
        val mid = samples.size / 2
        val firstHalfAvg = samples.take(mid).map { it.second }.average()
        val secondHalfAvg = samples.drop(mid).map { it.second }.average()
        
        val delta = secondHalfAvg - firstHalfAvg
        return when {
            delta > 2.5 -> SignalTrend.GETTING_STRONGER
            delta < -2.5 -> SignalTrend.GETTING_WEAKER
            else -> SignalTrend.STABLE
        }
    }

    fun computeProximityBand(smoothedRssi: Double?): ProximityBand {
        val rssi = smoothedRssi ?: return ProximityBand.UNKNOWN
        return when {
            rssi >= -50.0 -> ProximityBand.VERY_CLOSE
            rssi >= -60.0 -> ProximityBand.NEAR
            rssi >= -70.0 -> ProximityBand.NEARBY
            rssi >= -80.0 -> ProximityBand.FAR
            else -> ProximityBand.VERY_FAR
        }
    }

    fun estimateDistance(
        smoothedRssi: Double?,
        txPower: Int?,
        txPowerSource: TxPowerSource,
        profile: EnvironmentProfile,
        calibration: ProximityCalibrationData?
    ): DistanceEstimate? {
        val rssi = smoothedRssi ?: return null
        if (txPowerSource == TxPowerSource.UNAVAILABLE && calibration == null) return null

        val rssiAtOneMeter = when {
            calibration != null -> calibration.medianRssiAtOneMeter.toDouble()
            txPower != null -> txPower.toDouble()
            else -> -59.0 // assumed fallback default
        }

        val exponent = profile.exponent
        val calculatedDistance = 10.0.pow((rssiAtOneMeter - rssi) / (10.0 * exponent))

        return when {
            calculatedDistance < 3.5 -> DistanceEstimate(1.0, 3.0, "Approx. 1–3 m")
            calculatedDistance < 7.0 -> DistanceEstimate(2.0, 6.0, "Approx. 2–6 m")
            calculatedDistance < 14.0 -> DistanceEstimate(5.0, 12.0, "Approx. 5–12 m")
            calculatedDistance < 25.0 -> DistanceEstimate(10.0, 25.0, "Approx. 10–25 m")
            else -> DistanceEstimate(20.0, 100.0, "20 m+ / unreliable")
        }
    }

    fun calculateConfidence(
        smoothedRssi: Double?,
        variance: Double?,
        sampleCount: Int,
        txPowerSource: TxPowerSource,
        isStale: Boolean,
        isRandomizedBle: Boolean,
        profile: EnvironmentProfile,
        hasCalibration: Boolean,
        targetId: String
    ): Pair<ProximityConfidence, String> {
        if (smoothedRssi == null || targetId.isBlank() || targetId == "02:00:00:00:00:00") {
            return Pair(ProximityConfidence.UNAVAILABLE, "Signal or identity info unavailable.")
        }
        if (isStale) {
            return Pair(ProximityConfidence.VERY_LOW, "Signal is stale. No packet received recently.")
        }

        var score = 50
        val details = StringBuilder()

        if (hasCalibration) {
            score += 30
            details.append("Custom 1m calibration applied. ")
        }

        when (txPowerSource) {
            TxPowerSource.ADVERTISED -> {
                score += 15
                details.append("Using advertised transmitter reference. ")
            }
            TxPowerSource.ASSUMED -> {
                score -= 20
                details.append("Using generic default transmitter power reference. ")
            }
            else -> {}
        }

        val v = variance ?: 0.0
        if (v < 4.0) {
            score += 15
            details.append("Signal is highly stable. ")
        } else if (v > 12.0) {
            score -= 25
            details.append("High signal variance due to interference or obstructions. ")
        }

        if (sampleCount >= 15) {
            score += 10
        } else if (sampleCount < 5) {
            score -= 15
            details.append("Few signal samples collected. ")
        }

        if (smoothedRssi >= -60.0) {
            score += 10
        } else if (smoothedRssi <= -80.0) {
            score -= 15
            details.append("Weak radio signal received. ")
        }

        if (isRandomizedBle) {
            score -= 15
            details.append("Randomized private address. ")
        }

        if (profile == EnvironmentProfile.DENSE_WALLS) {
            score -= 10
        }

        val finalScore = score.coerceIn(0, 100)
        var confidence = when {
            finalScore >= 75 -> ProximityConfidence.MEDIUM
            finalScore >= 40 -> ProximityConfidence.LOW
            else -> ProximityConfidence.VERY_LOW
        }

        // Apply Hard Caps / Ceilings
        if (txPowerSource == TxPowerSource.ASSUMED && confidence == ProximityConfidence.MEDIUM) {
            confidence = ProximityConfidence.LOW
            details.append("(Confidence capped due to assumed reference power)")
        }
        if (isRandomizedBle && confidence == ProximityConfidence.MEDIUM) {
            confidence = ProximityConfidence.LOW
            details.append("(Confidence capped due to rotating private ID)")
        }

        return Pair(confidence, details.toString().trim())
    }
}
