package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.SignalingOutboxEntity

@Dao
interface SignalingOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(item: SignalingOutboxEntity)

    @Query("SELECT * FROM signaling_outbox WHERE expiresAt > :now AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC")
    suspend fun getPendingOutbox(now: Long = System.currentTimeMillis()): List<SignalingOutboxEntity>

    @Query("UPDATE signaling_outbox SET attempts = :attempts, nextAttemptAt = :nextAttemptAt WHERE id = :id")
    suspend fun updateAttempt(id: String, attempts: Int, nextAttemptAt: Long)

    @Query("DELETE FROM signaling_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM signaling_outbox WHERE requestId = :requestId")
    suspend fun deleteByRequestId(requestId: String)

    @Query("DELETE FROM signaling_outbox WHERE expiresAt <= :now")
    suspend fun pruneExpired(now: Long = System.currentTimeMillis())
}
