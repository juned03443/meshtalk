package com.campus.meshtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.campus.meshtalk.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY lastSeen DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    fun getAllContactsIncludingBlocked(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getContact(deviceId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE meshTalkId = :meshTalkId LIMIT 1")
    suspend fun getContactByMeshId(meshTalkId: String): ContactEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM contacts WHERE meshTalkId = :meshTalkId AND isBlocked = 0 LIMIT 1)")
    suspend fun isAcceptedContact(meshTalkId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM contacts WHERE deviceId = :deviceId AND isBlocked = 0 LIMIT 1)")
    suspend fun isAcceptedContactByDeviceId(deviceId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE deviceId = :deviceId")
    suspend fun setBlocked(deviceId: String, blocked: Boolean)

    @Query("UPDATE contacts SET isMuted = :muted WHERE deviceId = :deviceId")
    suspend fun setMuted(deviceId: String, muted: Boolean)

    @Query("UPDATE contacts SET disappearingMessagesDurationSec = :durationSec WHERE deviceId = :deviceId")
    suspend fun setDisappearingDuration(deviceId: String, durationSec: Long)

    @Query("SELECT * FROM contacts WHERE isBlocked = 1")
    fun getBlockedContacts(): Flow<List<ContactEntity>>

    @Query("UPDATE contacts SET lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)

    @Query("DELETE FROM contacts WHERE deviceId = :deviceId")
    suspend fun deleteContact(deviceId: String)
}
