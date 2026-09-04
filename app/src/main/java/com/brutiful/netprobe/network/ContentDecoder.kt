package com.brutiful.netprobe.network

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ContentDecoder {

    fun decodeSmart(data: ByteArray): String? {
        val str = try { String(data, StandardCharsets.UTF_8) } catch (_: Exception) { return null }
        if (str.isBlank()) return null

        // Try JSON
        val prettyJson = tryPrettyPrintJson(str)
        if (prettyJson != null) return prettyJson

        // Try URL Decoding
        if (str.contains("%") || str.contains("+")) {
            val decoded = try { URLDecoder.decode(str, "UTF-8") } catch (_: Exception) { null }
            if (decoded != null && decoded != str) {
                val nestedJson = tryPrettyPrintJson(decoded)
                return "URL Decoded:\n$decoded" + (if (nestedJson != null) "\n\nDetected JSON inside:\n$nestedJson" else "")
            }
        }

        // Try Base64 (only if it looks like a reasonable length and format)
        if (str.length > 8 && str.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try {
                val decodedBytes = Base64.decode(str, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, StandardCharsets.UTF_8)
                if (decodedStr.all { it.isLetterOrDigit() || it.isWhitespace() || "{}[]\",:".contains(it) }) {
                    val nestedJson = tryPrettyPrintJson(decodedStr)
                    return "Base64 Decoded:\n$decodedStr" + (if (nestedJson != null) "\n\nDetected JSON inside:\n$nestedJson" else "")
                }
            } catch (_: Exception) {}
        }

        return null // No special decoding found
    }

    private fun tryPrettyPrintJson(text: String): String? {
        val trimmed = text.trim()
        return try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(4)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(4)
            } else null
        } catch (_: Exception) { null }
    }
}
