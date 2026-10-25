package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.QueuedMessageEntity

@Dao
interface QueuedMessageDao {

    @Query("SELECT * FROM queued_messages ORDER BY timestamp ASC")
    suspend fun getAllQueued(): List<QueuedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueued(queuedMessage: QueuedMessageEntity)

    @Query("DELETE FROM queued_messages WHERE messageId = :messageId")
    suspend fun deleteQueued(messageId: String)

    @Query("DELETE FROM queued_messages WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldQueued(cutoffTimestamp: Long)
}
