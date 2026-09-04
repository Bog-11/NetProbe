package com.brutiful.netprobe.network

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.brutiful.netprobe.util.NetProbeLog
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

data class AppMetadata(
    val uid: Int,
    val label: String,
    val packageName: String
)

/**
 * Resolves the application owning a network connection using Android 10+ APIs.
 */
class ConnectionOwnerResolver(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    
    private val metadataCache = ConcurrentHashMap<Int, AppMetadata>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    fun clearCache() {
        metadataCache.clear()
        iconCache.clear()
    }

    fun getOwnerUid(
        protocol: Int,
        sourceIp: InetAddress,
        sourcePort: Int,
        destIp: InetAddress,
        destPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Process.INVALID_UID

        // Only TCP (6) and UDP (17) are supported by getConnectionOwnerUid
        if (protocol != 6 && protocol != 17) return Process.INVALID_UID

        // 1. Try actual captured local + remote
        val uid = tryLookup(protocol, sourceIp, sourcePort, destIp, destPort)
        if (uid != Process.INVALID_UID) {
            NetProbeLog.d("OwnerResolver", "Resolved UID $uid via actual address ($protocol)")
            return uid
        }

        // 2. Try wildcard fallback (Experimental)
        return try {
            val wildcardIp = if (sourceIp is java.net.Inet6Address) {
                InetAddress.getByName("::")
            } else {
                InetAddress.getByName("0.0.0.0")
            }
            val fallbackUid = tryLookup(protocol, wildcardIp, sourcePort, destIp, destPort)
            if (fallbackUid != Process.INVALID_UID) {
                NetProbeLog.d("OwnerResolver", "Resolved UID $fallbackUid via wildcard fallback ($protocol)")
            }
            fallbackUid
        } catch (e: Exception) {
            Process.INVALID_UID
        }
    }

    private fun tryLookup(protocol: Int, localIp: InetAddress, localPort: Int, remoteIp: InetAddress, remotePort: Int): Int {
        return try {
            val local = InetSocketAddress(localIp, localPort)
            val remote = InetSocketAddress(remoteIp, remotePort)
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        } catch (e: Exception) {
            Process.INVALID_UID
        }
    }

    fun getCachedMetadata(uid: Int): AppMetadata? = metadataCache[uid]
    
    fun getIcon(packageName: String?): Drawable? = packageName?.let { iconCache[it] }

    fun resolveMetadata(uid: Int): AppMetadata {
        metadataCache[uid]?.let { return it }

        if (uid == 0) return AppMetadata(0, "System (root)", "root").also { metadataCache[uid] = it }
        if (uid == 1000) return AppMetadata(1000, "System Server", "android").also { metadataCache[uid] = it }

        return try {
            val packages = packageManager?.getPackagesForUid(uid)
            NetProbeLog.d("OwnerResolver", "UID resolved to ${packages?.size ?: 0} packages")

            if (packages.isNullOrEmpty()) {
                return AppMetadata(uid, "UID $uid", "unknown").also { metadataCache[uid] = it }
            }

            if (packages.size > 1) {
                NetProbeLog.i("OwnerResolver", "Shared UID case detected")
            }

            // Take the first package as the primary representative
            val packageName = packages[0]
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            
            try {
                val icon = packageManager.getApplicationIcon(appInfo)
                iconCache[packageName] = icon
            } catch (e: Exception) {
                NetProbeLog.w("OwnerResolver", "Failed to load icon")
            }

            val metadata = AppMetadata(uid, label, packageName)
            metadataCache[uid] = metadata
            NetProbeLog.d("OwnerResolver", "Resolved metadata for UID")
            metadata
        } catch (e: Exception) {
            NetProbeLog.e("OwnerResolver", "Failed to resolve metadata for UID")
            // If label lookup fails but we have the UID, at least show the UID
            AppMetadata(uid, "UID $uid", "unknown").also { metadataCache[uid] = it }
        }
    }
}
