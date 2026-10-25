package com.campus.meshtalk.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class MeshPacket(
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
    val senderPublicKey: String = "",
    val ephemeralPubKey: String = "",
    val nonce: String = "",
    val ciphertext: String,
    val signature: String = "",
    val hopLimit: Int = DEFAULT_MAX_HOPS,
    val hopCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + RELAY_EXPIRY_MS,
    val isAck: Boolean = false,
    val isAnnouncement: Boolean = false,
    val groupId: String? = null,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val fileChecksum: String? = null
) {
    fun toByteArray(): ByteArray {
        return Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        const val DESTINATION_BROADCAST = "BROADCAST"

        const val DEFAULT_MAX_HOPS = 5
        const val RELAY_EXPIRY_MS = 24 * 60 * 60 * 1000L
        const val DIRECT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L

        fun fromByteArray(bytes: ByteArray): MeshPacket? {
            return try {
                val jsonStr = String(bytes, StandardCharsets.UTF_8)
                Json.decodeFromString<MeshPacket>(jsonStr)
            } catch (e: Exception) {
                null
            }
        }
    }
}
