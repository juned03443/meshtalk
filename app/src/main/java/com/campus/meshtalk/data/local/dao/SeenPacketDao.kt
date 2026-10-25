package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.SeenPacketEntity

@Dao
interface SeenPacketDao {

    @Query("SELECT EXISTS(SELECT 1 FROM seen_packets WHERE messageId = :messageId LIMIT 1)")
    suspend fun hasSeen(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(packet: SeenPacketEntity)

    @Query("DELETE FROM seen_packets WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldPackets(cutoffTimestamp: Long)
}
