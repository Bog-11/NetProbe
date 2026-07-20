package com.brutiful.netprobe.network

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.util.Log
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

        return try {
            val local = InetSocketAddress(sourceIp, sourcePort)
            val remote = InetSocketAddress(destIp, destPort)
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        } catch (e: Exception) {
            Log.e("OwnerResolver", "Failed to resolve owner UID", e)
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
            Log.d("OwnerResolver", "UID $uid -> packages: ${packages?.joinToString() ?: "none"}")

            if (packages.isNullOrEmpty()) {
                return AppMetadata(uid, "UID $uid", "unknown").also { metadataCache[uid] = it }
            }

            if (packages.size > 1) {
                Log.i("OwnerResolver", "Shared UID case: $uid shared by ${packages.joinToString()}")
            }

            // Take the first package as the primary representative
            val packageName = packages[0]
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            
            try {
                val icon = packageManager.getApplicationIcon(appInfo)
                iconCache[packageName] = icon
            } catch (e: Exception) {
                Log.w("OwnerResolver", "Failed to load icon for $packageName", e)
            }

            val metadata = AppMetadata(uid, label, packageName)
            metadataCache[uid] = metadata
            Log.d("OwnerResolver", "Resolved: UID $uid -> $label ($packageName)")
            metadata
        } catch (e: Exception) {
            Log.e("OwnerResolver", "Failed to resolve metadata for UID $uid", e)
            // If label lookup fails but we have the UID, at least show the UID
            AppMetadata(uid, "UID $uid", "unknown").also { metadataCache[uid] = it }
        }
    }
}
