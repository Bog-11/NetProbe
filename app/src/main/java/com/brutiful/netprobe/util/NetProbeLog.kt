package com.brutiful.netprobe.util

import android.util.Log
import com.brutiful.netprobe.BuildConfig

/**
 * Centralized logging utility for NetProbe.
 *
 * Implements:
 * 1. Build-type aware logging: Debug/Info logs are only emitted in DEBUG builds.
 * 2. R8-friendly code: Logs are wrapped in BuildConfig.DEBUG checks for complete stripping.
 * 3. Data Sanitization: Utilities to mask IPs and other sensitive data.
 */
object NetProbeLog {
    private const val TAG_PREFIX = "NetProbe_"

    /**
     * Masks an IP address for logging.
     * e.g., "192.168.1.5" -> "192.168.x.x"
     * e.g., "fd00:1::1" -> "fd00:1:x:x"
     */
    fun maskIp(ip: String?): String {
        if (ip == null) return "null"
        return when {
            ip.contains(":") -> { // IPv6
                val parts = ip.split(":")
                if (parts.size > 2) {
                    parts.take(2).joinToString(":") + ":x:x"
                } else {
                    "IPv6:x:x"
                }
            }
            ip.contains(".") -> { // IPv4
                val parts = ip.split(".")
                if (parts.size >= 2) {
                    parts.take(2).joinToString(".") + ".x.x"
                } else {
                    "IPv4:x.x"
                }
            }
            else -> "IP:x"
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG_PREFIX + tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG_PREFIX + tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        // Warnings are kept in release, but must be sanitized at the call site.
        if (throwable != null) {
            Log.w(TAG_PREFIX + tag, message, throwable)
        } else {
            Log.w(TAG_PREFIX + tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Errors are kept in release, but must be sanitized at the call site.
        if (throwable != null) {
            Log.e(TAG_PREFIX + tag, message, throwable)
        } else {
            Log.e(TAG_PREFIX + tag, message)
        }
    }
}
