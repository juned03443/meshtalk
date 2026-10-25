package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.RelayPacketEntity

@Dao
interface RelayPacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelay(entity: RelayPacketEntity)

    @Query("SELECT * FROM relay_store WHERE expiresAt > :now ORDER BY storedAt ASC")
    suspend fun getActiveRelayPackets(now: Long = System.currentTimeMillis()): List<RelayPacketEntity>

    @Query("SELECT COUNT(*) FROM relay_store")
    suspend fun getRelayCount(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM relay_store")
    suspend fun getTotalSizeBytes(): Long

    @Query("DELETE FROM relay_store WHERE messageId = :messageId")
    suspend fun deleteRelay(messageId: String)

    @Query("DELETE FROM relay_store WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM relay_store WHERE messageId IN (SELECT messageId FROM relay_store ORDER BY storedAt ASC LIMIT :limit)")
    suspend fun evictOldest(limit: Int)
}
