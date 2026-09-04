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

        val bytes = packet.rawBytes
        if (bytes.size < 20) return metadata

        val ihl = (bytes[0].toInt() and 0x0F) * 4
        var payloadOffset = ihl
        
        if (packet.protocol == "TCP") {
            if (bytes.size < ihl + 20) return metadata
            val dataOffset = ((bytes[ihl + 12].toInt() shr 4) and 0x0F) * 4
            payloadOffset += dataOffset
        } else if (packet.protocol == "UDP") {
            payloadOffset += 8
        }

        if (payloadOffset >= bytes.size) {
            metadata["Payload"] = "None (Control Packet)"
            return metadata
        }
        
        val payloadLen = bytes.size - payloadOffset
        
        // Basic payload-based heuristic parsing
        val payloadStr = try {
            String(bytes, payloadOffset, payloadLen.coerceAtMost(1000), StandardCharsets.UTF_8)
        } catch (_: Exception) { "" }

        // Set initial decryption status based on ports/protocol
        if (packet.protocol == "TCP" && (packet.destinationPort == 443 || packet.sourcePort == 443)) {
            metadata["_decryption_status"] = "ENCRYPTED_NOT_DECRYPTED"
        } else if (packet.protocol == "UDP" && (packet.destinationPort == 443 || packet.sourcePort == 443)) {
            metadata["_decryption_status"] = "ENCRYPTED_NOT_DECRYPTED"
        } else {
            metadata["_decryption_status"] = "NOT_ENCRYPTED"
        }

        if (packet.protocol == "TCP") {
            val firstByte = bytes[payloadOffset].toInt() and 0xFF
            val secondByte = if (payloadLen > 1) bytes[payloadOffset + 1].toInt() and 0xFF else 0
            
            if (payloadStr.startsWith("GET ") || payloadStr.startsWith("POST ") || 
                payloadStr.startsWith("HTTP/1.") || payloadStr.startsWith("HTTP/2")) {
                metadata["App Protocol"] = "HTTP"
                metadata["HTTP Line"] = payloadStr.substringBefore("\r\n")
            } else if (firstByte in 0x14..0x17 && secondByte == 0x03) {
                // TLS/SSL Record Layer
                metadata["App Protocol"] = "TLS/SSL"
                val recordType = when (firstByte) {
                    0x14 -> "Change Cipher Spec"
                    0x15 -> "Alert"
                    0x16 -> "Handshake"
                    0x17 -> "Application Data (Encrypted)"
                    else -> "Unknown TLS Type"
                }
                metadata["TLS Record Type"] = recordType
                
                // Handshake detail
                if (firstByte == 0x16 && payloadLen > 5) {
                    val hsType = bytes[payloadOffset + 5].toInt() and 0xFF
                    metadata["Handshake Type"] = when (hsType) {
                        0x01 -> "Client Hello"
                        0x02 -> "Server Hello"
                        0x0b -> "Certificate"
                        0x0c -> "Server Key Exchange"
                        0x0e -> "Server Hello Done"
                        0x10 -> "Client Key Exchange"
                        else -> "Type $hsType"
                    }
                }
            } else if (packet.destinationPort == 443 || packet.sourcePort == 443) {
                metadata["App Protocol"] = "Likely HTTPS"
                metadata["Note"] = "Encrypted binary data on standard HTTPS port"
            } else if (packet.destinationPort == 22 || packet.sourcePort == 22) {
                metadata["App Protocol"] = "SSH"
                if (payloadStr.startsWith("SSH-2.0")) {
                    metadata["SSH Version"] = payloadStr.substringBefore("\r\n")
                }
            }
        } else if (packet.protocol == "UDP") {
            if (packet.destinationPort == 53 || packet.sourcePort == 53) {
                metadata["App Protocol"] = "DNS"
                if (payloadLen > 12) {
                    val id = ((bytes[payloadOffset].toInt() and 0xFF) shl 8) or (bytes[payloadOffset + 1].toInt() and 0xFF)
                    metadata["DNS Transaction ID"] = "0x${id.toString(16).uppercase()}"
                }
            } else if (packet.destinationPort == 443 || packet.sourcePort == 443) {
                metadata["App Protocol"] = "QUIC / HTTP3"
            }
        }
        
        return metadata
    }
}
