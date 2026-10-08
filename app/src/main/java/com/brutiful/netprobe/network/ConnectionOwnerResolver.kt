package com.brutiful.netprobe.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import com.brutiful.netprobe.util.NetProbeLog
import kotlinx.coroutines.delay
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

data class AppMetadata(
    val uid: Int,
    val label: String,
    val packageNames: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val resolutionReason: String = "SUCCESS"
)

data class CacheEntry(
    val uid: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "SUCCESS"
)

/**
 * Resolves the application owning a network connection using Android 10+ APIs.
 * Implements strict caching, retry logic, and detailed diagnostics.
 */
class ConnectionOwnerResolver(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    
    private val uidCache = ConcurrentHashMap<String, CacheEntry>()
    private val metadataCache = ConcurrentHashMap<Int, AppMetadata>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    companion object {
        const val INVALID_UID = -1
        private const val POSITIVE_UID_TTL = 30_000L // 30 seconds
        private const val NEGATIVE_UID_TTL = 500L    // 500 ms (as requested)
        private const val METADATA_TTL = 300_000L   // 5 minutes for app info
    }

    fun clearCache() {
        uidCache.clear()
        metadataCache.clear()
        iconCache.clear()
    }

    /**
     * Attempts to resolve the UID of the process owning the given ORIGINAL flow tuple.
     * Logs exact diagnostics for troubleshooting.
     */
    suspend fun getOwnerUidWithRetry(
        protocol: Int,
        sourceIp: InetAddress,
        sourcePort: Int,
        destIp: InetAddress,
        destPort: Int
    ): Int {
        val cacheKey = "$protocol:$sourceIp:$sourcePort->$destIp:$destPort"
        val now = System.currentTimeMillis()

        uidCache[cacheKey]?.let { entry ->
            val ttl = if (entry.uid != INVALID_UID) POSITIVE_UID_TTL else NEGATIVE_UID_TTL
            if (now - entry.timestamp < ttl) {
                return entry.uid
            }
        }

        NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Resolving flow $cacheKey (Proto: $protocol)")

        // 1. Initial lookup
        var uid = getOwnerUid(protocol, sourceIp, sourcePort, destIp, destPort)
        var reason = if (uid != INVALID_UID) "SUCCESS" else "OWNER_UID_UNAVAILABLE"

        // 2. Retry logic (200ms delay as requested)
        if (uid == INVALID_UID) {
            NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Initial lookup failed, retrying in 200ms...")
            delay(200L)
            uid = getOwnerUid(protocol, sourceIp, sourcePort, destIp, destPort)
            if (uid != INVALID_UID) {
                reason = "SUCCESS_AFTER_RETRY"
                NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Retry success! UID: $uid")
            } else {
                reason = "OWNER_UID_UNAVAILABLE_AFTER_RETRY"
                NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Retry failed.")
            }
        } else {
            NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Initial lookup success! UID: $uid")
        }

        uidCache[cacheKey] = CacheEntry(uid, now, reason)
        return uid
    }

    private fun getOwnerUid(
        protocol: Int,
        sourceIp: InetAddress,
        sourcePort: Int,
        destIp: InetAddress,
        destPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return INVALID_UID
        
        return try {
            val local = InetSocketAddress(sourceIp, sourcePort)
            val remote = InetSocketAddress(destIp, destPort)
            // CRITICAL: Ensure we pass the ORIGINAL app-facing tuple
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        } catch (e: Exception) {
            NetProbeLog.e("OwnerResolver", "DIAGNOSTIC: getConnectionOwnerUid error: ${e.message}")
            INVALID_UID
        }
    }

    fun getCachedMetadata(uid: Int): AppMetadata? {
        val metadata = metadataCache[uid] ?: return null
        if (System.currentTimeMillis() - metadata.timestamp > METADATA_TTL) {
            metadataCache.remove(uid)
            return null
        }
        return metadata
    }
    
    fun getIcon(packageName: String?): Drawable? = packageName?.let { iconCache[it] }

    fun resolveMetadata(uid: Int): AppMetadata {
        getCachedMetadata(uid)?.let { return it }

        NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: Resolving metadata for UID $uid")

        // Known Android System UIDs
        val systemLabel = when (uid) {
            0 -> "System (root)"
            1000 -> "Android System"
            1001 -> "Telephony/Phone"
            1013 -> "Media Server"
            1027 -> "NFC"
            1073 -> "Network Stack"
            else -> null
        }
        
        if (systemLabel != null) {
            return AppMetadata(uid, systemLabel, listOf("android.system.$uid"), resolutionReason = "SYSTEM_UID").also { metadataCache[uid] = it }
        }

        if (uid in 0..1999) {
            return AppMetadata(uid, "System Service ($uid)", listOf("android.system.$uid"), resolutionReason = "SYSTEM_UID_RANGE").also { metadataCache[uid] = it }
        }

        if (uid == INVALID_UID) return AppMetadata(uid, "Unknown app", emptyList(), resolutionReason = "OWNER_UID_UNAVAILABLE").also { metadataCache[uid] = it }

        return try {
            val packages = packageManager?.getPackagesForUid(uid)?.toList()
            NetProbeLog.d("OwnerResolver", "DIAGNOSTIC: getPackagesForUid($uid) returned: ${packages?.joinToString(", ") ?: "null"}")

            if (packages.isNullOrEmpty()) {
                val reason = "NO_PACKAGES_FOR_UID" // Likely Package Visibility issue
                NetProbeLog.w("OwnerResolver", "DIAGNOSTIC: $reason for UID $uid. Most likely Android 11+ Package Visibility restriction.")
                return AppMetadata(uid, "Unknown app (UID $uid)", emptyList(), resolutionReason = reason).also { metadataCache[uid] = it }
            }

            // Shared UID handling
            val primaryPackage = packages[0]
            val appInfo = try {
                packageManager.getApplicationInfo(primaryPackage, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // Try to find any package that is visible
                var foundInfo: ApplicationInfo? = null
                for (pkg in packages) {
                    try {
                        foundInfo = packageManager.getApplicationInfo(pkg, 0)
                        break
                    } catch (_: Exception) {}
                }
                foundInfo ?: throw e
            }
            
            val label = packageManager.getApplicationLabel(appInfo).toString()
            
            try {
                val icon = packageManager.getApplicationIcon(appInfo)
                iconCache[primaryPackage] = icon
            } catch (e: Exception) {
                NetProbeLog.w("OwnerResolver", "DIAGNOSTIC: Icon resolution failed for $primaryPackage: ${e.message}")
            }

            val metadata = AppMetadata(uid, label, packages, resolutionReason = "SUCCESS")
            metadataCache[uid] = metadata
            metadata
        } catch (e: Exception) {
            val reason = when (e) {
                is PackageManager.NameNotFoundException -> "APPLICATION_INFO_NOT_FOUND"
                is SecurityException -> "PACKAGE_NOT_VISIBLE"
                else -> "UNKNOWN_RESOLUTION_ERROR"
            }
            NetProbeLog.e("OwnerResolver", "DIAGNOSTIC: Metadata resolution failed for UID $uid: $reason (${e.message})")
            AppMetadata(uid, "UID $uid", emptyList(), resolutionReason = reason).also { metadataCache[uid] = it }
        }
    }
}
