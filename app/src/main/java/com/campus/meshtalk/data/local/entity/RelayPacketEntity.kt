package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relay_store")
data class RelayPacketEntity(
    @PrimaryKey val messageId: String,
    val packetBytes: ByteArray,
    val sizeBytes: Int,
    val expiresAt: Long,
    val storedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RelayPacketEntity
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
