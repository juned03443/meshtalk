package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val meshTalkId: String,
    val nickname: String = "",
    val reason: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)
