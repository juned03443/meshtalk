package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUserDao {

    @Query("SELECT * FROM blocked_users ORDER BY blockedAt DESC")
    fun getAllBlockedUsers(): Flow<List<BlockedUserEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE meshTalkId = :meshId LIMIT 1)")
    suspend fun isBlocked(meshId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(blockedUser: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE meshTalkId = :meshId")
    suspend fun unblockUser(meshId: String)
}
