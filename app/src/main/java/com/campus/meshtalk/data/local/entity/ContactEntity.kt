package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["meshTalkId"], unique = true)]
)
data class ContactEntity(
    @PrimaryKey val deviceId: String,
    val meshTalkId: String,
    val nickname: String,
    val publicKeyBase64: String = "",
    val avatarInitial: String = "",
    val status: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val disappearingMessagesDurationSec: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val keyChanged: Boolean = false
)
