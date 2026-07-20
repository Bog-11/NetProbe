package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exports exact packet bytes captured from the Android VPN interface.
 * This is a literal capture of the TUN interface traffic.
 */
object RawPcapExporter {
    private const val MAGIC_NUMBER = 0xa1b2c3d4.toInt()
    private const val VERSION_MAJOR = 2.toShort()
    private const val VERSION_MINOR = 4.toShort()
    private const val LINKTYPE_RAW = 101 
    private const val SNAPLEN = 65535

    fun export(packets: List<CapturedPacket>, outputStream: OutputStream, stripPayload: Boolean = false) {
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
                
                val originalBytes = packet.rawBytes
                val packetData = if (stripPayload) {
                    sanitizePacket(originalBytes)
                } else {
                    originalBytes
                }
                
                val length = packetData.size

                packetHeaderBuffer.clear()
                packetHeaderBuffer.putInt(seconds)
                packetHeaderBuffer.putInt(microseconds)
                packetHeaderBuffer.putInt(length)
                packetHeaderBuffer.putInt(length)
                
                os.write(packetHeaderBuffer.array())
                os.write(packetData)
            }
            os.flush()
        }
    }

    private fun sanitizePacket(data: ByteArray): ByteArray {
        if (data.size < 20) return data // Not a full IP header
        
        val version = (data[0].toInt() shr 4) and 0x0F
        if (version != 4) return data // Only IPv4 sanitization for now
        
        val ihl = (data[0].toInt() and 0x0F) * 4
        if (data.size < ihl) return data // Malformed
        
        val protocol = data[9].toInt() and 0xFF
        val totalIpLen = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getShort(2).toInt() and 0xFFFF
        
        var headerOnlyLen = ihl
        when (protocol) {
            6 -> { // TCP
                if (data.size >= ihl + 20) {
                    val tcpHeaderLen = ((data[ihl + 12].toInt() shr 4) and 0x0F) * 4
                    headerOnlyLen = ihl + tcpHeaderLen
                }
            }
            17 -> { // UDP
                headerOnlyLen = ihl + 8
            }
        }
        
        val sanitizedLen = headerOnlyLen.coerceAtMost(data.size)
        val sanitized = data.copyOf(sanitizedLen)
        
        // Update IP Total Length field to match the new size
        if (sanitized.size >= 4) {
            ByteBuffer.wrap(sanitized).order(ByteOrder.BIG_ENDIAN).putShort(2, sanitized.size.toShort())
        }
        
        return sanitized
    }
}
