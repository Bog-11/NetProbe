package com.brutiful.netprobe.model

data class TracerouteHop(
    val ttl: Int,
    val ip: String?,
    val latencyMs: String?,
    val enrichment: HopEnrichment? = null
)

data class HopEnrichment(
    val hostname: String? = null,
    val asn: String? = null,
    val organization: String? = null,
    val country: String? = null,
    val location: String? = null,
    val isPrivate: Boolean = false,
    val countryCode: String? = null
)
