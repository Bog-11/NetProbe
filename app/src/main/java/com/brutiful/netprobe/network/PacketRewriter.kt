package com.brutiful.netprobe.network

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketRewriter {

    fun rewriteSourceIp(packet: ByteArray, realLocalIp: InetAddress): ByteArray {
        if (packet.isEmpty()) return packet
        
        // Only handle IPv4 for now as per requirements
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return packet

        val modifiedPacket = packet.copyOf()
        val buffer = ByteBuffer.wrap(modifiedPacket).order(ByteOrder.BIG_ENDIAN)

        val ihl = (modifiedPacket[0].toInt() and 0x0F) * 4
        if (modifiedPacket.size < ihl || ihl < 20) return packet // Malformed or too small for IPv4
        
        val protocol = modifiedPacket[9].toInt() and 0xFF
        val totalLength = buffer.getShort(2).toInt() and 0xFFFF
        
        // 1. Replace Source IP (Offset 12-15)
        val realIpBytes = realLocalIp.address
        if (realIpBytes.size == 4) {
            if (modifiedPacket.size >= 16) {
                System.arraycopy(realIpBytes, 0, modifiedPacket, 12, 4)
            } else return packet
        } else {
            return packet // Not an IPv4 address
        }

        // 2. Recalculate IPv4 Header Checksum (Offset 10-11)
        if (modifiedPacket.size >= 12) {
            buffer.putShort(10, 0) // Zero out old checksum
            var ipChecksum = 0L
            for (i in 0 until ihl step 2) {
                if (i + 1 < modifiedPacket.size) {
                    ipChecksum += buffer.getShort(i).toLong() and 0xFFFFL
                }
            }
            while (ipChecksum shr 16 > 0) ipChecksum = (ipChecksum and 0xFFFFL) + (ipChecksum shr 16)
            buffer.putShort(10, (ipChecksum.inv() and 0xFFFFL).toShort())
        }

        // 3. Recalculate TCP/UDP Checksum
        val transportLen = totalLength - ihl
        if (transportLen < 0 || modifiedPacket.size < totalLength) return modifiedPacket

        when (protocol) {
            6 -> { // TCP
                if (transportLen >= 20 && modifiedPacket.size >= ihl + 18) {
                    buffer.putShort(ihl + 16, 0) // Zero out TCP checksum
                    val tcpChecksum = calculateTransportChecksum(buffer, ihl, transportLen, protocol)
                    buffer.putShort(ihl + 16, tcpChecksum)
                }
            }
            17 -> { // UDP
                if (transportLen >= 8 && modifiedPacket.size >= ihl + 8) {
                    buffer.putShort(ihl + 6, 0) // Zero out UDP checksum
                    val udpChecksum = calculateTransportChecksum(buffer, ihl, transportLen, protocol)
                    buffer.putShort(ihl + 6, udpChecksum)
                }
            }
        }

        return modifiedPacket
    }

    private fun calculateTransportChecksum(buffer: ByteBuffer, ihl: Int, transportLen: Int, protocol: Int): Short {
        var sum = 0L
        
        // Pseudo-header
        val srcIp1 = buffer.getShort(12).toLong() and 0xFFFFL
        val srcIp2 = buffer.getShort(14).toLong() and 0xFFFFL
        val dstIp1 = buffer.getShort(16).toLong() and 0xFFFFL
        val dstIp2 = buffer.getShort(18).toLong() and 0xFFFFL
        
        sum += srcIp1 + srcIp2 + dstIp1 + dstIp2
        sum += protocol.toLong()
        sum += transportLen.toLong()

        // Transport Header + Payload
        for (i in 0 until transportLen step 2) {
            if (i + 1 < transportLen) {
                sum += buffer.getShort(ihl + i).toLong() and 0xFFFFL
            } else {
                sum += (buffer.get(ihl + i).toLong() and 0xFFL) shl 8
            }
        }

        while (sum shr 16 > 0) sum = (sum and 0xFFFFL) + (sum shr 16)
        return (sum.inv() and 0xFFFFL).toShort()
    }
}
