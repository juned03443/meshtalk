package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signaling_outbox")
data class SignalingOutboxEntity(
    @PrimaryKey val id: String, // e.g. "$requestId:$type"
    val requestId: String,
    val type: String, // CONN_REQUEST, CONN_ACCEPT, CONN_DECLINE, CONN_CANCEL
    val targetMeshId: String,
    val payloadBytes: ByteArray,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignalingOutboxEntity

        if (id != other.id) return false
        if (requestId != other.requestId) return false
        if (type != other.type) return false
        if (targetMeshId != other.targetMeshId) return false
        if (!payloadBytes.contentEquals(other.payloadBytes)) return false
        if (attempts != other.attempts) return false
        if (createdAt != other.createdAt) return false
        if (nextAttemptAt != other.nextAttemptAt) return false
        if (expiresAt != other.expiresAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + requestId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + targetMeshId.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + attempts
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + nextAttemptAt.hashCode()
        result = 31 * result + expiresAt.hashCode()
        return result
    }
}
