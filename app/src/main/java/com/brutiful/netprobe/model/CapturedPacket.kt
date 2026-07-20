package com.brutiful.netprobe.model

import java.util.UUID

enum class PacketDirection {
    UPSTREAM,
    DOWNSTREAM
}

enum class DecryptionStatus {
    NOT_ENCRYPTED,
    ENCRYPTED_NOT_DECRYPTED,
    DECRYPTED,
    DECRYPTION_FAILED
}

data class CapturedPacket(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val direction: PacketDirection,
    val protocol: String,
    val sourceIp: String,
    val sourcePort: Int,
    val destinationIp: String,
    val destinationPort: Int,
    val length: Int,
    val rawBytes: ByteArray,
    val parsedMetadata: Map<String, String> = emptyMap(),
    val decryptedPayload: ByteArray? = null,
    val decryptionStatus: DecryptionStatus = DecryptionStatus.NOT_ENCRYPTED,
    val summary: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CapturedPacket
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
