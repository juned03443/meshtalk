package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.ConnectionRequestEntity
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.local.entity.RequestStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ConnectionRequestEntity)

    @Query("SELECT * FROM connection_requests WHERE direction = :direction AND status = :status ORDER BY timestamp DESC")
    fun getRequestsByDirectionAndStatus(direction: RequestDirection, status: RequestStatus): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getAllPendingRequests(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests WHERE direction = 'INCOMING' AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getIncomingPendingRequests(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests WHERE direction = 'OUTGOING' AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getOutgoingPendingRequests(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests ORDER BY timestamp DESC")
    fun getAllRequests(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getRequest(requestId: String): ConnectionRequestEntity?

    @Query("SELECT * FROM connection_requests WHERE fromMeshId = :fromId AND toMeshId = :toId AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingRequestBetween(fromId: String, toId: String): ConnectionRequestEntity?

    @Query("SELECT * FROM connection_requests WHERE ((fromMeshId = :id1 AND toMeshId = :id2) OR (fromMeshId = :id2 AND toMeshId = :id1)) AND status = 'PENDING' LIMIT 1")
    suspend fun getAnyPendingBetween(id1: String, id2: String): ConnectionRequestEntity?

    @Query("SELECT * FROM connection_requests WHERE fromMeshId = :fromId AND toMeshId = :toId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRequestBetween(fromId: String, toId: String): ConnectionRequestEntity?

    @Query("SELECT * FROM connection_requests WHERE (fromMeshId = :meshId OR toMeshId = :meshId) AND status = 'PENDING' LIMIT 1")
    suspend fun getAnyPendingRequestForMeshId(meshId: String): ConnectionRequestEntity?

    @Query("SELECT COUNT(*) FROM connection_requests WHERE fromMeshId = :fromId AND timestamp > :sinceTimestamp")
    suspend fun countRequestsFromSenderSince(fromId: String, sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM connection_requests WHERE direction = 'INCOMING' AND status = 'PENDING'")
    suspend fun countIncomingPending(): Int

    @Query("UPDATE connection_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateStatus(requestId: String, status: RequestStatus)

    @Query("UPDATE connection_requests SET lastSentAt = :lastSentAt, sendAttempts = :sendAttempts WHERE requestId = :requestId")
    suspend fun updateSendAttempt(requestId: String, lastSentAt: Long, sendAttempts: Int)

    @Query("DELETE FROM connection_requests WHERE requestId = :requestId")
    suspend fun deleteRequest(requestId: String)

    @Query("UPDATE connection_requests SET status = 'EXPIRED' WHERE expiresAt < :now AND status = 'PENDING'")
    suspend fun expireOldRequests(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM connection_requests WHERE direction = 'INCOMING' AND status = 'PENDING'")
    fun getIncomingPendingCount(): Flow<Int>
}
