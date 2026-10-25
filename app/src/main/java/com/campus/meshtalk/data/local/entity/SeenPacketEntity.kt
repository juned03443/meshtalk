package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_packets")
data class SeenPacketEntity(
    @PrimaryKey val messageId: String,
    val timestamp: Long = System.currentTimeMillis()
)
