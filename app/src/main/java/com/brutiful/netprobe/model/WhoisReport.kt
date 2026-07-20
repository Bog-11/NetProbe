package com.brutiful.netprobe.model

data class WhoisReport(
    val query: String,
    val type: TargetType,
    val summary: Map<String, String> = emptyMap(),
    val entities: List<WhoisEntity> = emptyList(),
    val events: List<WhoisEvent> = emptyList(),
    val notices: List<String> = emptyList(),
    val nameservers: List<String> = emptyList(),
    val status: List<String> = emptyList(),
    val domainInfo: DomainSpecificInfo? = null,
    val rawData: String? = null,
    val errorMessage: String? = null,
    val isPrivate: Boolean = false
)

enum class TargetType {
    IPV4, IPV6, DOMAIN, ASN, UNKNOWN
}

data class DomainSpecificInfo(
    val ldhName: String? = null,
    val unicodeName: String? = null,
    val secureDNS: Boolean? = null
)

data class WhoisEntity(
    val handle: String? = null,
    val roles: List<String> = emptyList(),
    val name: String? = null,
    val organization: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val country: String? = null,
    val contactUri: String? = null
)

data class WhoisEvent(
    val action: String,
    val date: String
)
