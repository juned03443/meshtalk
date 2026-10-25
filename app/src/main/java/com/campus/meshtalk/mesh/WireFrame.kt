package com.campus.meshtalk.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

enum class FrameType {
    MSG,
    HELLO,
    CONN_REQUEST,
    CONN_ACCEPT,
    CONN_DECLINE,
    CONN_CANCEL
}

@Serializable
data class WireFrame(
    val v: Int = 1,
    val type: FrameType,
    val body: ByteArray
) {
    fun toByteArray(): ByteArray {
        val jsonStr = Json.encodeToString(this)
        val jsonBytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
        val combined = ByteArray(MAGIC.size + jsonBytes.size)
        System.arraycopy(MAGIC, 0, combined, 0, MAGIC.size)
        System.arraycopy(jsonBytes, 0, combined, MAGIC.size, jsonBytes.size)
        return combined
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WireFrame

        if (v != other.v) return false
        if (type != other.type) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = v
        result = 31 * result + type.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object {
        const val MAX_FRAME_SIZE = 64 * 1024 // 64 KB
        val MAGIC = byteArrayOf(0x4D, 0x54, 0x57, 0x46) // "MTWF" (MeshTalk Wire Frame)

        fun isWireFrame(bytes: ByteArray): Boolean {
            if (bytes.size < MAGIC.size) return false
            for (i in MAGIC.indices) {
                if (bytes[i] != MAGIC[i]) return false
            }
            return true
        }

        /**
         * Decodes bytes into a WireFrame.
         * If it has MTWF magic: parses as WireFrame.
         * If it exceeds 64 KB: returns null (reject).
         * If it fails or is legacy: falls back to parsing as MeshPacket, returning WireFrame(type = FrameType.MSG, body = bytes).
         */
        fun decode(bytes: ByteArray): WireFrame? {
            if (bytes.size > MAX_FRAME_SIZE) {
                return null
            }

            if (isWireFrame(bytes)) {
                try {
                    val jsonBytes = bytes.copyOfRange(MAGIC.size, bytes.size)
                    val jsonStr = String(jsonBytes, StandardCharsets.UTF_8)
                    return Json.decodeFromString<WireFrame>(jsonStr)
                } catch (e: Exception) {
                    // corrupted WireFrame, fall through to fallback
                }
            }

            // Fallback for legacy MeshPacket
            val legacyPacket = com.campus.meshtalk.data.model.MeshPacket.fromByteArray(bytes)
            if (legacyPacket != null) {
                return WireFrame(v = 1, type = FrameType.MSG, body = bytes)
            }

            return null
        }
    }
}
