package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import com.brutiful.netprobe.model.PacketDirection
import java.io.OutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a synthetic PCAP using the real endpoints and session metadata shown in the app.
 * This mode is "honest" about being reconstructed and not a literal wire capture.
 * It rewrites internal VPN addresses to the device's actual local IP.
 */
object ReconstructedPcapExporter {
    private const val MAGIC_NUMBER = 0xa1b2c3d4.toInt()
    private const val VERSION_MAJOR = 2.toShort()
    private const val VERSION_MINOR = 4.toShort()
    private const val LINKTYPE_RAW = 101 
    private const val SNAPLEN = 65535

    fun export(packets: List<CapturedPacket>, outputStream: OutputStream, realLocalIp: InetAddress) {
        outputStream.use { os ->
            val globalHeader = ByteBuffer.allocate(24).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(MAGIC_NUMBER)
                putShort(VERSION_MAJOR)
                putShort(VERSION_MINOR)
                putInt(0)
                putInt(0)
                putInt(SNAPLEN)
                putInt(LINKTYPE_RAW)
            }
            os.write(globalHeader.array())

            val sortedPackets = packets.sortedBy { it.timestamp }
            val packetHeaderBuffer = ByteBuffer.allocate(16).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            sortedPackets.forEach { packet ->
                val seconds = (packet.timestamp / 1000).toInt()
                val microseconds = ((packet.timestamp % 1000) * 1000).toInt()
                
                // Reconstruct/Rewrite packet for better readability
                val reconstructedBytes = reconstructPacket(packet, realLocalIp)
                val length = reconstructedBytes.size

                packetHeaderBuffer.clear()
                packetHeaderBuffer.putInt(seconds)
                packetHeaderBuffer.putInt(microseconds)
                packetHeaderBuffer.putInt(length)
                packetHeaderBuffer.putInt(length)
                
                os.write(packetHeaderBuffer.array())
                os.write(reconstructedBytes)
            }
            os.flush()
        }
    }

    private fun reconstructPacket(packet: CapturedPacket, realLocalIp: InetAddress): ByteArray {
        val data = packet.rawBytes.copyOf()
        if (data.size < 20) return data // Not a valid IP packet

        val version = (data[0].toInt() shr 4) and 0x0F
        if (version != 4) return data // Only handle IPv4 reconstruction for now

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val ihl = (data[0].toInt() and 0x0F) * 4
        if (data.size < ihl) return data // Too small for declared IHL
        
        val protocol = data[9].toInt() and 0xFF
        val totalLen = buffer.getShort(2).toInt() and 0xFFFF
        if (data.size < totalLen) return data // Incomplete packet

        val realIpBytes = realLocalIp.address
        if (realIpBytes.size != 4) return data

        // Replace VPN address (usually 10.0.0.1) with real local IP
        if (packet.direction == PacketDirection.UPSTREAM) {
            // Upstream: Replace Source IP
            if (data.size >= 16) System.arraycopy(realIpBytes, 0, data, 12, 4)
        } else {
            // Downstream: Replace Destination IP
            if (data.size >= 20) System.arraycopy(realIpBytes, 0, data, 16, 4)
        }

        // Recalculate IP Checksum
        if (data.size >= 12) {
            buffer.putShort(10, 0)
            var ipSum = 0L
            for (i in 0 until ihl step 2) {
                if (i + 1 < data.size) {
                    ipSum += buffer.getShort(i).toLong() and 0xFFFFL
                }
            }
            while (ipSum shr 16 > 0) ipSum = (ipSum and 0xFFFFL) + (ipSum shr 16)
            buffer.putShort(10, (ipSum.inv() and 0xFFFFL).toShort())
        }

        // Recalculate Transport Checksum (TCP/UDP)
        val transportLen = totalLen - ihl
        if (transportLen >= 8 && data.size >= totalLen) {
            when (protocol) {
                6 -> if (transportLen >= 20 && data.size >= ihl + 18) buffer.putShort(ihl + 16, 0) // TCP
                17 -> if (data.size >= ihl + 8) buffer.putShort(ihl + 6, 0) // UDP
            }
            val checksum = calculatePseudoChecksum(buffer, ihl, transportLen, protocol)
            when (protocol) {
                6 -> if (transportLen >= 20 && data.size >= ihl + 18) buffer.putShort(ihl + 16, checksum)
                17 -> if (data.size >= ihl + 8) buffer.putShort(ihl + 6, checksum)
            }
        }

        return data
    }

    private fun calculatePseudoChecksum(buffer: ByteBuffer, ihl: Int, transportLen: Int, protocol: Int): Short {
        var sum = 0L
        sum += (buffer.getShort(12).toLong() and 0xFFFFL) + (buffer.getShort(14).toLong() and 0xFFFFL)
        sum += (buffer.getShort(16).toLong() and 0xFFFFL) + (buffer.getShort(18).toLong() and 0xFFFFL)
        sum += protocol.toLong()
        sum += transportLen.toLong()

        for (i in 0 until transportLen step 2) {
            if (ihl + i + 1 < buffer.capacity()) {
                sum += buffer.getShort(ihl + i).toLong() and 0xFFFFL
            } else if (ihl + i < buffer.capacity()) {
                sum += (buffer.get(ihl + i).toLong() and 0xFFL) shl 8
            }
        }
        while (sum shr 16 > 0) sum = (sum and 0xFFFFL) + (sum shr 16)
        return (sum.inv() and 0xFFFFL).toShort()
    }
}
