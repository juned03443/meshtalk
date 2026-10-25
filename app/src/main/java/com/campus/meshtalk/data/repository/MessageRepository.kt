package com.campus.meshtalk.data.repository

import com.campus.meshtalk.data.local.dao.ContactDao
import com.campus.meshtalk.data.local.dao.MessageDao
import com.campus.meshtalk.data.local.entity.MessageEntity
import com.campus.meshtalk.data.local.entity.MessageStatus
import com.campus.meshtalk.data.model.MeshPacket
import com.campus.meshtalk.mesh.MeshRouter
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val meshRouter: MeshRouter
) {

    fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForPeer(peerId)

    fun getAllMessages(): Flow<List<MessageEntity>> =
        messageDao.getAllMessages()

    fun getLatestMessagePerPeer(): Flow<List<MessageEntity>> =
        messageDao.getLatestMessagePerPeer()

    fun getUnreadCount(peerId: String): Flow<Int> =
        messageDao.getUnreadCount(peerId)

    suspend fun sendMessage(recipientPeerId: String, content: String): Result<MeshPacket> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }

        val isBroadcast = recipientPeerId == MeshPacket.DESTINATION_BROADCAST
        if (!isBroadcast) {
            val isAccepted = contactDao.isAcceptedContact(recipientPeerId) ||
                    contactDao.isAcceptedContactByDeviceId(recipientPeerId)
            if (!isAccepted) {
                return Result.failure(IllegalStateException("Can only send messages to accepted contacts"))
            }
        }

        val contact = contactDao.getContact(recipientPeerId)
            ?: contactDao.getContactByMeshId(recipientPeerId)

        return try {
            val packet = meshRouter.sendMessage(
                recipientId = recipientPeerId,
                recipientPublicKey = contact?.publicKeyBase64,
                content = trimmed
            )
            Result.success(packet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retryMessage(messageId: String): Boolean {
        return meshRouter.retryMessage(messageId)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }
}
