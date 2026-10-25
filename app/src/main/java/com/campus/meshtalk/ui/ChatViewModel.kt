package com.campus.meshtalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campus.meshtalk.data.local.dao.MessageDao
import com.campus.meshtalk.data.local.entity.MessageEntity
import com.campus.meshtalk.mesh.MeshPeer
import com.campus.meshtalk.mesh.MeshRouter
import com.campus.meshtalk.mesh.NearbyMeshManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val peerId: String,
    private val meshRouter: MeshRouter,
    private val nearbyManager: NearbyMeshManager,
    private val messageDao: MessageDao,
    private val contactDao: com.campus.meshtalk.data.local.dao.ContactDao? = null
) : ViewModel() {

    val messages: StateFlow<List<MessageEntity>> = messageDao
        .getMessagesForPeer(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeers: StateFlow<Map<String, MeshPeer>> = nearbyManager.connectedPeers

    fun sendMessage(content: String, hopLimit: Int = 4) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val contact = contactDao?.getContactByMeshId(peerId) ?: contactDao?.getContact(peerId)
            val pubKey = contact?.publicKeyBase64
            meshRouter.sendMessage(
                recipientId = peerId,
                recipientPublicKey = pubKey,
                content = content.trim(),
                hopLimit = hopLimit
            )
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageDao.deleteMessage(messageId)
        }
    }

    class Factory(
        private val peerId: String,
        private val meshRouter: MeshRouter,
        private val nearbyManager: NearbyMeshManager,
        private val messageDao: MessageDao,
        private val contactDao: com.campus.meshtalk.data.local.dao.ContactDao? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(peerId, meshRouter, nearbyManager, messageDao, contactDao) as T
        }
    }
}
