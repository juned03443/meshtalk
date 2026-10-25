package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.MessageEntity
import com.campus.meshtalk.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessagesForGroup(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isAnnouncement = 1 ORDER BY timestamp DESC")
    fun getAnnouncements(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE mediaPath IS NOT NULL")
    suspend fun getMediaMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status, attemptCount = :attempts, lastAttemptAt = :lastAttempt WHERE messageId = :messageId")
    suspend fun updateDeliveryAttempt(messageId: String, status: MessageStatus, attempts: Int, lastAttempt: Long)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isOutgoing = 1 AND status IN ('PENDING', 'SENDING') AND expiresAt > :now ORDER BY timestamp ASC")
    suspend fun getPendingOutboxMessages(now: Long = System.currentTimeMillis()): List<MessageEntity>

    @Query("DELETE FROM messages WHERE peerId = :peerId AND isOutgoing = 1 AND status IN ('PENDING', 'SENDING')")
    suspend fun cancelPendingOutboxForPeer(peerId: String)

    @Query("UPDATE messages SET status = 'FAILED' WHERE isOutgoing = 1 AND status IN ('PENDING', 'SENDING') AND expiresAt <= :now")
    suspend fun markExpiredMessagesFailed(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE peerId = :peerId AND isOutgoing = 0 AND status != 'DELIVERED'")
    fun getUnreadCount(peerId: String): Flow<Int>

    /**
     * Returns the latest message per conversation (peerId).
     * Used for the chat dashboard conversation list.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE messageId IN (
            SELECT messageId FROM messages m2 
            WHERE m2.timestamp = (SELECT MAX(m3.timestamp) FROM messages m3 WHERE m3.peerId = m2.peerId)
            GROUP BY peerId
        )
        ORDER BY timestamp DESC
    """)
    fun getLatestMessagePerPeer(): Flow<List<MessageEntity>>
}
