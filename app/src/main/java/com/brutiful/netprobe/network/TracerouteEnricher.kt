package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.HopEnrichment
import com.brutiful.netprobe.model.TargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

object TracerouteEnricher {
    private val cache = ConcurrentHashMap<String, HopEnrichment>()

    suspend fun enrich(ip: String): HopEnrichment = withContext(Dispatchers.IO) {
        cache[ip]?.let { return@withContext it }

        if (isPrivate(ip)) {
            val enrichment = HopEnrichment(isPrivate = true, organization = "Internal / Private Network")
            cache[ip] = enrichment
            return@withContext enrichment
        }

        val hostname = try {
            InetAddress.getByName(ip).hostName.let { if (it == ip) null else it }
        } catch (_: Exception) { null }

        val whois = WhoisClient.fetchReport(ip)
        val enrichment = HopEnrichment(
            hostname = hostname,
            asn = whois.summary["Handle"]?.takeIf { it.startsWith("AS", ignoreCase = true) },
            organization = whois.summary["Name"] ?: whois.summary["Type"],
            country = whois.summary["Registration Country"],
            location = null // GeoIP would go here if available
        )

        cache[ip] = enrichment
        enrichment
    }

    private fun isPrivate(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
        } catch (_: Exception) { false }
    }
}
