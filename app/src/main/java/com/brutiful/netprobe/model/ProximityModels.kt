package com.brutiful.netprobe.model

enum class LiveTrackingState {
    INACTIVE,
    STARTING,
    TRACKING,
    SIGNAL_STALE,
    BLUETOOTH_DISABLED,
    ACCESS_POINT_CHANGED,
    PERMISSION_REQUIRED,
    STOPPED,
    TARGET_CHANGED,
    ERROR
}

enum class ProximityTargetType {
    BLE_DEVICE,
    CLASSIC_BLUETOOTH_DEVICE,
    CONNECTED_WIFI_ACCESS_POINT,
    NEARBY_WIFI_ACCESS_POINT
}

enum class SignalTrend {
    GETTING_STRONGER,
    STABLE,
    GETTING_WEAKER,
    UNKNOWN
}

enum class ProximityBand(val label: String, val typicalRange: String) {
    VERY_CLOSE("Very close", "Approx. 1–3 m"),
    NEAR("Near", "Approx. 2–6 m"),
    NEARBY("Nearby", "Approx. 5–12 m"),
    FAR("Far", "Approx. 10–25 m"),
    VERY_FAR("Very far / weak signal", "20 m+ / unreliable"),
    UNKNOWN("Unknown proximity", "Distance estimate unavailable")
}

enum class TxPowerSource {
    ADVERTISED,
    CALIBRATED,
    ASSUMED,
    UNAVAILABLE
}

enum class ProximityConfidence {
    MEDIUM,
    LOW,
    VERY_LOW,
    UNAVAILABLE
}

enum class EnvironmentProfile(val exponent: Double) {
    OPEN_SPACE(2.0),
    TYPICAL_INDOOR(3.0),
    DENSE_WALLS(4.0)
}

data class DistanceEstimate(
    val minMeters: Double,
    val maxMeters: Double,
    val displayLabel: String
)

data class ProximityTrackingSession(
    val targetId: String,
    val targetType: ProximityTargetType,
    val displayName: String,
    val startedAt: Long,
    val lastUpdatedAt: Long?,
    val state: LiveTrackingState,
    val rawRssi: Int?,
    val smoothedRssi: Double?,
    val rssiVariance: Double?,
    val trend: SignalTrend,
    val proximityBand: ProximityBand,
    val distanceEstimate: DistanceEstimate?,
    val confidence: ProximityConfidence,
    val confidenceDetails: String,
    val sampleCount: Int,
    val lastSeenAt: Long?,
    val isStale: Boolean,
    val txPower: Int? = null,
    val txPowerSource: TxPowerSource = TxPowerSource.UNAVAILABLE,
    val isRandomizedBle: Boolean = false,
    val bleServiceUuids: List<String> = emptyList(),
    val bleManufacturerDataSignature: String? = null
)

data class ProximityCalibrationData(
    val targetId: String,
    val targetType: ProximityTargetType,
    val medianRssiAtOneMeter: Int,
    val sampleCount: Int,
    val variance: Double,
    val environmentProfile: EnvironmentProfile,
    val calibratedAt: Long
)
