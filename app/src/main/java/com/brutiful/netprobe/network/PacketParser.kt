package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import java.nio.charset.StandardCharsets

object PacketParser {

    fun parsePacket(packet: CapturedPacket): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        metadata["Timestamp"] = java.util.Date(packet.timestamp).toString()
        metadata["Protocol"] = packet.protocol
        metadata["Source"] = "${packet.sourceIp}:${packet.sourcePort}"
        metadata["Destination"] = "${packet.destinationIp}:${packet.destinationPort}"
        metadata["Length"] = "${packet.length} bytes"
        
        // Basic payload-based heuristic parsing
        val payloadStr = try {
            String(packet.rawBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) { "" }

        if (packet.protocol == "TCP") {
            if (payloadStr.startsWith("GET ") || payloadStr.startsWith("POST ") || 
                payloadStr.startsWith("HTTP/1.") || payloadStr.startsWith("HTTP/2")) {
                metadata["App Protocol"] = "HTTP"
                val lines = payloadStr.split("\r\n")
                if (lines.isNotEmpty()) {
                    metadata["HTTP Line"] = lines[0]
                }
            } else if (packet.rawBytes.size > 5 && packet.rawBytes[0].toInt() == 0x16 && packet.rawBytes[1].toInt() == 0x03) {
                metadata["App Protocol"] = "TLS/SSL"
                metadata["TLS Content Type"] = "Handshake (0x16)"
                // Version can be parsed from bytes 1,2
                val version = when {
                    packet.rawBytes[1].toInt() == 0x03 && packet.rawBytes[2].toInt() == 0x01 -> "TLS 1.0"
                    packet.rawBytes[1].toInt() == 0x03 && packet.rawBytes[2].toInt() == 0x02 -> "TLS 1.1"
                    packet.rawBytes[1].toInt() == 0x03 && packet.rawBytes[3].toInt() == 0x03 -> "TLS 1.2"
                    packet.rawBytes[1].toInt() == 0x03 && packet.rawBytes[3].toInt() == 0x04 -> "TLS 1.3"
                    else -> "Unknown TLS"
                }
                metadata["TLS Version"] = version
            }
        } else if (packet.protocol == "UDP") {
            if (packet.destinationPort == 53 || packet.sourcePort == 53) {
                metadata["App Protocol"] = "DNS"
            }
        }
        
        return metadata
    }
}
