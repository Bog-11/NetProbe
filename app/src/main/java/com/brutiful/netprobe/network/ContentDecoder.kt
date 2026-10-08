package com.brutiful.netprobe.network

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object ContentDecoder {

    fun decodeSmart(data: ByteArray): String? {
        // 1. Try Decompression first (Gzip/Deflate)
        val decompressed = tryDecompress(data)
        val workingData = decompressed ?: data
        
        val str = try { String(workingData, StandardCharsets.UTF_8) } catch (_: Exception) { return null }
        if (str.isBlank()) return null

        val results = mutableListOf<String>()
        if (decompressed != null) results.add("--- Decompressed Content ---")

        // 2. Try JWT
        val jwt = tryDecodeJwt(str)
        if (jwt != null) return results.joinToString("\n") + "\n" + jwt

        // 3. Try JSON
        val prettyJson = tryPrettyPrintJson(str)
        if (prettyJson != null) return results.joinToString("\n") + "\n" + prettyJson

        // 4. Try XML
        val prettyXml = tryPrettyPrintXml(str)
        if (prettyXml != null) return results.joinToString("\n") + "\n" + prettyXml

        // 5. Try URL Decoding
        if (str.contains("%") || str.contains("+")) {
            val decoded = try { URLDecoder.decode(str, "UTF-8") } catch (_: Exception) { null }
            if (decoded != null && decoded != str) {
                val nestedJson = tryPrettyPrintJson(decoded)
                return "URL Decoded:\n$decoded" + (if (nestedJson != null) "\n\nDetected JSON inside:\n$nestedJson" else "")
            }
        }

        // 6. Try Base64
        if (str.length > 8 && str.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try {
                val decodedBytes = Base64.decode(str, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, StandardCharsets.UTF_8)
                if (decodedStr.any { it.isLetterOrDigit() }) {
                    val nestedJson = tryPrettyPrintJson(decodedStr)
                    return "Base64 Decoded:\n$decodedStr" + (if (nestedJson != null) "\n\nDetected JSON inside:\n$nestedJson" else "")
                }
            } catch (_: Exception) {}
        }

        return if (decompressed != null) results.joinToString("\n") + "\n" + str else null
    }

    private fun tryDecompress(data: ByteArray): ByteArray? {
        if (data.size < 2) return null
        
        // Gzip header: 0x1f 0x8b
        if (data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            try {
                return GZIPInputStream(ByteArrayInputStream(data)).readBytes()
            } catch (_: Exception) {}
        }
        
        // Deflate (Zlib) header: 0x78
        if (data[0] == 0x78.toByte()) {
            try {
                return InflaterInputStream(ByteArrayInputStream(data)).readBytes()
            } catch (_: Exception) {}
        }
        
        return null
    }

    private fun tryDecodeJwt(text: String): String? {
        val parts = text.split(".")
        if (parts.size != 3) return null
        
        try {
            val header = String(Base64.decode(parts[0], Base64.URL_SAFE), StandardCharsets.UTF_8)
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8)
            
            val prettyHeader = tryPrettyPrintJson(header) ?: header
            val prettyPayload = tryPrettyPrintJson(payload) ?: payload
            
            return "JWT Detected:\n\nHeader:\n$prettyHeader\n\nPayload:\n$prettyPayload"
        } catch (_: Exception) {
            return null
        }
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

    private fun tryPrettyPrintXml(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("<")) return null
        // Simple indentation for XML (naive)
        return try {
            var indent = 0
            val result = StringBuilder()
            val tokens = trimmed.replace(">", ">\n").replace("<", "\n<").split("\n")
            for (token in tokens) {
                val t = token.trim()
                if (t.isEmpty()) continue
                if (t.startsWith("</")) indent--
                repeat(indent) { result.append("  ") }
                result.append(t).append("\n")
                if (t.startsWith("<") && !t.startsWith("</") && !t.endsWith("/>") && !t.startsWith("<?")) indent++
            }
            result.toString()
        } catch (_: Exception) { null }
    }
}
