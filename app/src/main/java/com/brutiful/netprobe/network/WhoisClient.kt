package com.brutiful.netprobe.network

import android.util.Log
import com.brutiful.netprobe.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Scanner

object WhoisClient {
    private const val TAG = "WhoisClient"
    private const val BOOTSTRAP_IP = "https://rdap.arin.net/registry/ip/"
    private const val BOOTSTRAP_ASN = "https://rdap.arin.net/registry/autnum/"
    private const val BOOTSTRAP_DOMAIN = "https://rdap.org/domain/"

    suspend fun fetchReport(query: String): WhoisReport = withContext(Dispatchers.IO) {
        try {
            val type = detectTargetType(query)
            if (isPrivateAddress(query, type)) {
                return@withContext WhoisReport(query, type, isPrivate = true)
            }

            val rdapResult = tryFetchRdap(query, type)
            if (rdapResult != null) {
                return@withContext parseRdapResponse(query, type, rdapResult)
            }

            if (type == TargetType.DOMAIN) {
                val whoisResult = fetchWhoisFallback(query)
                return@withContext WhoisReport(
                    query = query,
                    type = type,
                    rawData = whoisResult,
                    summary = mapOf("Note" to "RDAP unavailable, showing raw WHOIS.")
                )
            }

            WhoisReport(query, type, errorMessage = "Could not fetch data for $query")
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
            WhoisReport(query, TargetType.UNKNOWN, errorMessage = e.message)
        }
    }

    private fun detectTargetType(query: String): TargetType {
        return when {
            query.matches(Regex("^[0-9.]+$")) -> TargetType.IPV4
            query.contains(":") -> TargetType.IPV6
            query.lowercase().startsWith("as") || query.matches(Regex("^[0-9]+$")) -> TargetType.ASN
            query.contains(".") -> TargetType.DOMAIN
            else -> TargetType.UNKNOWN
        }
    }

    private fun isPrivateAddress(query: String, type: TargetType): Boolean {
        if (type != TargetType.IPV4 && type != TargetType.IPV6) return false
        return try {
            val addr = InetAddress.getByName(query)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
        } catch (_: Exception) { false }
    }

    private fun tryFetchRdap(query: String, type: TargetType): String? {
        val baseUrl = when (type) {
            TargetType.IPV4, TargetType.IPV6 -> BOOTSTRAP_IP
            TargetType.ASN -> BOOTSTRAP_ASN
            TargetType.DOMAIN -> BOOTSTRAP_DOMAIN
            else -> return null
        }
        
        var currentUrl = baseUrl + query.removePrefix("as").removePrefix("AS")
        var redirects = 0
        
        while (redirects < 5) {
            Log.d(TAG, "Querying RDAP: $currentUrl")
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/rdap+json")
            }
            
            val status = connection.responseCode
            if (status in 300..399) {
                currentUrl = connection.getHeaderField("Location") ?: break
                redirects++
                continue
            }
            
            if (status == 200) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
            break
        }
        return null
    }

    private fun parseRdapResponse(query: String, type: TargetType, jsonStr: String): WhoisReport {
        val json = try { JSONObject(jsonStr) } catch (_: Exception) { return WhoisReport(query, type, errorMessage = "Invalid JSON") }
        
        val keys = json.keys()
        val keyList = mutableListOf<String>()
        while (keys.hasNext()) keyList.add(keys.next())
        Log.d(TAG, "RDAP response keys for $query: $keyList")

        return when (type) {
            TargetType.DOMAIN -> parseDomainRdap(query, json, jsonStr)
            else -> parseGenericRdap(query, type, json, jsonStr)
        }
    }

    private fun parseDomainRdap(query: String, json: JSONObject, jsonStr: String): WhoisReport {
        val summary = mutableMapOf<String, String>()
        val entities = mutableListOf<WhoisEntity>()
        val events = mutableListOf<WhoisEvent>()
        val notices = mutableListOf<String>()
        val nameservers = mutableListOf<String>()
        val statusList = mutableListOf<String>()

        val ldhName = json.optString("ldhName", "Not provided in RDAP response")
        val unicodeName = json.optString("unicodeName", "Not provided in RDAP response")
        val handle = json.optString("handle", "Not provided in RDAP response")

        summary["Domain Name"] = ldhName
        if (unicodeName != "Not provided in RDAP response" && unicodeName != ldhName) {
            summary["Unicode Name"] = unicodeName
        }
        summary["Handle"] = handle

        // Nameservers
        json.optJSONArray("nameservers")?.let { nsArray ->
            for (i in 0 until nsArray.length()) {
                val ns = nsArray.getJSONObject(i)
                val nsName = ns.optString("ldhName")
                if (nsName.isNotEmpty()) nameservers.add(nsName)
            }
        }
        Log.d(TAG, "Found ${nameservers.size} nameservers")

        // Status
        json.optJSONArray("status")?.let { sArray ->
            for (i in 0 until sArray.length()) {
                statusList.add(sArray.getString(i))
            }
        }
        Log.d(TAG, "Found ${statusList.size} status entries")

        // Events
        json.optJSONArray("events")?.let { eArray ->
            for (i in 0 until eArray.length()) {
                val ev = eArray.getJSONObject(i)
                val action = ev.optString("eventAction")
                val date = ev.optString("eventDate")
                events.add(WhoisEvent(action, date))
                
                when (action.lowercase()) {
                    "registration", "created" -> summary["Created"] = date
                    "last changed", "updated" -> summary["Updated"] = date
                    "expiration", "expiry" -> summary["Expires"] = date
                    "last update of rdap database" -> summary["RDAP Database Update"] = date
                }
            }
        }
        Log.d(TAG, "Found ${events.size} events")

        // DNSSEC
        json.optJSONObject("secureDNS")?.let { dns ->
            val signed = dns.optBoolean("delegationSigned")
            summary["DNSSEC"] = if (signed) "Enabled (Signed)" else "Disabled (Unsigned)"
        } ?: run {
            summary["DNSSEC"] = "Not provided in RDAP response"
        }

        // Entities
        json.optJSONArray("entities")?.let { eArray ->
            for (i in 0 until eArray.length()) {
                entities.add(parseEntity(eArray.getJSONObject(i)))
            }
        }
        Log.d(TAG, "Found ${entities.size} entities")

        // Notices
        json.optJSONArray("notices")?.let { nArray ->
            for (i in 0 until nArray.length()) {
                notices.add(nArray.getJSONObject(i).optString("title", "Notice"))
            }
        }

        return WhoisReport(
            query = query,
            type = TargetType.DOMAIN,
            summary = summary,
            entities = entities,
            events = events,
            notices = notices,
            nameservers = nameservers,
            status = statusList,
            domainInfo = DomainSpecificInfo(ldhName, unicodeName, json.optJSONObject("secureDNS")?.optBoolean("delegationSigned")),
            rawData = jsonStr
        )
    }

    private fun parseGenericRdap(query: String, type: TargetType, json: JSONObject, jsonStr: String): WhoisReport {
        val summary = mutableMapOf<String, String>()
        val entities = mutableListOf<WhoisEntity>()
        val events = mutableListOf<WhoisEvent>()
        val notices = mutableListOf<String>()

        summary["Handle"] = json.optString("handle", "N/A")
        summary["Name"] = json.optString("name", "N/A")
        summary["Start Address"] = json.optString("startAddress", "N/A")
        summary["End Address"] = json.optString("endAddress", "N/A")
        summary["IP Version"] = json.optString("ipVersion", "N/A")
        summary["Type"] = json.optString("type", "N/A")
        
        json.optJSONArray("status")?.let { sArray ->
            val statuses = mutableListOf<String>()
            for (i in 0 until sArray.length()) statuses.add(sArray.getString(i))
            summary["Status"] = statuses.joinToString(", ")
        } ?: run { summary["Status"] = "N/A" }
        
        summary["Registration Country"] = json.optString("country", "N/A")

        json.optJSONArray("entities")?.let { eArray ->
            for (i in 0 until eArray.length()) {
                entities.add(parseEntity(eArray.getJSONObject(i)))
            }
        }

        json.optJSONArray("events")?.let { eArray ->
            for (i in 0 until eArray.length()) {
                val ev = eArray.getJSONObject(i)
                events.add(WhoisEvent(ev.optString("eventAction"), ev.optString("eventDate")))
            }
        }

        json.optJSONArray("notices")?.let { nArray ->
            for (i in 0 until nArray.length()) {
                notices.add(nArray.getJSONObject(i).optString("title", "Notice"))
            }
        }

        return WhoisReport(query, type, summary, entities, events, notices, rawData = jsonStr)
    }

    private fun parseEntity(json: JSONObject): WhoisEntity {
        val handle = json.optString("handle", "N/A")
        val roles = mutableListOf<String>()
        json.optJSONArray("roles")?.let { rArray ->
            for (i in 0 until rArray.length()) roles.add(rArray.getString(i))
        }
        
        Log.d(TAG, "Parsing entity $handle with roles $roles")

        var name: String? = null
        var org: String? = null
        var email: String? = null
        var tel: String? = null
        var address: String? = null
        var contactUri: String? = null

        json.optJSONArray("vcardArray")?.optJSONArray(1)?.let { vcard ->
            for (i in 0 until vcard.length()) {
                val entry = vcard.getJSONArray(i)
                val fieldType = entry.optString(0)
                when (fieldType) {
                    "fn" -> name = entry.optString(3)
                    "org" -> org = entry.optString(3)
                    "email" -> email = entry.optString(3)
                    "tel" -> tel = entry.optString(3)
                    "contact-uri" -> contactUri = entry.optString(3)
                    "adr" -> {
                        val adrValues = entry.optJSONArray(3)
                        if (adrValues != null) {
                            val components = mutableListOf<String>()
                            for (j in 0 until adrValues.length()) {
                                val comp = adrValues.optString(j)
                                if (comp.isNotBlank() && comp != "null") {
                                    components.add(comp.trim())
                                }
                            }
                            address = components.joinToString(", ")
                        }
                    }
                }
            }
        }

        // If name/email/etc are still null but were present in the old parser's way, maybe check other places?
        // But the requirement specifically says to use vcardArray.

        // Fallback or "Redacted" messages if necessary
        // In many cases, if it's not in vcard, it's redacted or not provided.
        
        return WhoisEntity(
            handle = handle,
            roles = roles,
            name = name ?: "Redacted by registry/registrar",
            organization = org,
            email = email ?: "Redacted by registry/registrar",
            phone = tel,
            address = address,
            contactUri = contactUri
        )
    }

    private fun fetchWhoisFallback(query: String): String {
        return try {
            java.net.Socket("whois.iana.org", 43).use { socket ->
                socket.soTimeout = 5000
                socket.getOutputStream().write("$query\r\n".toByteArray())
                Scanner(socket.getInputStream()).useDelimiter("\\A").next()
            }
        } catch (e: Exception) {
            "WHOIS fallback failed: ${e.message}"
        }
    }
}
