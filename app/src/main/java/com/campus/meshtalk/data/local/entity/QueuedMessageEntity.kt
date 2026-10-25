package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_messages")
data class QueuedMessageEntity(
    @PrimaryKey val messageId: String,
    val packetBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)
