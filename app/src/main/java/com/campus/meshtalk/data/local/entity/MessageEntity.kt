package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE_NOTE,
    SYSTEM,
    ANNOUNCEMENT
}

enum class MessageStatus {
    PENDING,
    SENDING,
    RELAYED,
    DELIVERED,
    FAILED;

    companion object {
        // Compatibility aliases for legacy state strings
        val QUEUED = PENDING
        val SENT = SENDING
    }
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["peerId"]),
        Index(value = ["groupId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val peerId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val hopCount: Int = 0,
    val status: MessageStatus = MessageStatus.PENDING,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
    val messageType: MessageType = MessageType.TEXT,
    val mediaPath: String? = null,
    val mediaThumbnailBase64: String? = null,
    val mediaSizeBytes: Long = 0L,
    val mediaDurationSec: Int = 0,
    val transferProgress: Float = 1.0f,
    val groupId: String? = null,
    val isAnnouncement: Boolean = false
)
