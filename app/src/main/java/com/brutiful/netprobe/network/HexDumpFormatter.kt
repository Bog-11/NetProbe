package com.brutiful.netprobe.network

import android.util.Base64

data class HexDumpRow(
    val offset: String,
    val hex: String,
    val ascii: String,
    val full: String
)

object HexDumpFormatter {
    fun format(bytes: ByteArray): List<HexDumpRow> {
        val rows = mutableListOf<HexDumpRow>()
        val hexChars = "0123456789abcdef".toCharArray()

        for (i in bytes.indices step 16) {
            val offset = String.format("%08x", i)
            val hexBuilder = StringBuilder()
            val asciiBuilder = StringBuilder()
            val fullBuilder = StringBuilder()

            fullBuilder.append(offset).append("  ")

            for (j in 0 until 16) {
                val index = i + j
                if (index < bytes.size) {
                    val b = bytes[index].toInt() and 0xff
                    val h1 = hexChars[b shr 4]
                    val h2 = hexChars[b and 0x0f]
                    
                    hexBuilder.append(h1).append(h2).append(" ")
                    fullBuilder.append(h1).append(h2).append(" ")
                    
                    if (b in 32..126) {
                        asciiBuilder.append(b.toChar())
                    } else {
                        asciiBuilder.append(".")
                    }
                } else {
                    hexBuilder.append("   ")
                    fullBuilder.append("   ")
                    asciiBuilder.append(" ")
                }
                
                if (j == 7) {
                    hexBuilder.append(" ")
                    fullBuilder.append(" ")
                }
            }

            val ascii = asciiBuilder.toString()
            fullBuilder.append(" ").append(ascii)
            
            rows.add(
                HexDumpRow(
                    offset = offset,
                    hex = hexBuilder.toString().trimEnd(),
                    ascii = ascii.trimEnd(),
                    full = fullBuilder.toString()
                )
            )
        }
        return rows
    }

    fun getFullHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef".toCharArray()
        return bytes.joinToString(" ") { b ->
            val i = b.toInt() and 0xff
            "${hexChars[i shr 4]}${hexChars[i and 0x0f]}"
        }
    }

    fun getFullAscii(bytes: ByteArray): String {
        return bytes.map { b ->
            val i = b.toInt() and 0xff
            if (i in 32..126) i.toChar() else '.'
        }.joinToString("")
    }

    fun getFullDump(rows: List<HexDumpRow>): String {
        return rows.joinToString("\n") { it.full }
    }
    
    fun getBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
