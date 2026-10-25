package com.campus.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    CANCELLED
}

enum class RequestDirection {
    OUTGOING,
    INCOMING
}

@Entity(
    tableName = "connection_requests",
    indices = [
        Index(value = ["fromMeshId", "toMeshId"]),
        Index(value = ["status"])
    ]
)
data class ConnectionRequestEntity(
    @PrimaryKey val requestId: String,
    val fromDeviceId: String,
    val fromMeshId: String,
    val fromNickname: String,
    val fromPublicKey: String,
    val fromAvatarUri: String? = null,
    val toMeshId: String,
    val toDeviceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
    val status: RequestStatus = RequestStatus.PENDING,
    val direction: RequestDirection = RequestDirection.OUTGOING,
    val signature: String = "",
    val lastSentAt: Long = 0L,
    val sendAttempts: Int = 0
)
