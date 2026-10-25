package com.campus.meshtalk.mesh

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.campus.meshtalk.crypto.CryptoEngine
import com.campus.meshtalk.data.local.dao.BlockedUserDao
import com.campus.meshtalk.data.local.dao.ConnectionRequestDao
import com.campus.meshtalk.data.local.dao.ContactDao
import com.campus.meshtalk.data.local.dao.MessageDao
import com.campus.meshtalk.data.local.dao.QueuedMessageDao
import com.campus.meshtalk.data.local.dao.RelayPacketDao
import com.campus.meshtalk.data.local.dao.SeenPacketDao
import com.campus.meshtalk.data.local.entity.ConnectionRequestEntity
import com.campus.meshtalk.data.local.entity.ContactEntity
import com.campus.meshtalk.data.local.entity.MessageEntity
import com.campus.meshtalk.data.local.entity.MessageStatus
import com.campus.meshtalk.data.local.entity.QueuedMessageEntity
import com.campus.meshtalk.data.local.entity.RelayPacketEntity
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.local.entity.RequestStatus
import com.campus.meshtalk.data.local.entity.SeenPacketEntity
import com.campus.meshtalk.data.model.MeshPacket
import com.campus.meshtalk.data.preferences.BleScanMode
import com.campus.meshtalk.data.preferences.MeshBatteryPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.KeyPair
import java.util.UUID

class MeshRouter(
    private val context: Context? = null,
    val myMeshId: String,
    private val myNickname: String,
    private val keyPair: KeyPair,
    private val nearbyManager: NearbyMeshManager,
    private val messageDao: MessageDao,
    private val seenPacketDao: SeenPacketDao,
    private val queuedMessageDao: QueuedMessageDao,
    private val relayPacketDao: RelayPacketDao? = null,
    private val contactDao: ContactDao? = null,
    private val blockedUserDao: BlockedUserDao? = null,
    private val connectionRequestDao: ConnectionRequestDao? = null,
    var contactSignaling: ContactSignaling? = null,
    private val batteryPrefsRepo: MeshBatteryPreferencesRepository? = null,
    private val scope: CoroutineScope
) {

    val myPublicKeyBase64: String = CryptoEngine.keyToBase64(keyPair.public.encoded)

    private val _incomingMessages = MutableSharedFlow<MessageEntity>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessageEntity> = _incomingMessages.asSharedFlow()

    private val _batteryNoticeFlow = MutableStateFlow<String?>(null)
    val batteryNoticeFlow: StateFlow<String?> = _batteryNoticeFlow.asStateFlow()

    private var currentScanMode = BleScanMode.BALANCED
    private var relayForOthers = true
    private var relayOnlyWhileCharging = false

    init {
        listenForIncomingPackets()
        setupStoreAndForwardTrigger()
        setupDeliveryCallbacks()
        startPeriodicOutboxRetryLoop()
        startPeriodicExpiryAndCleanupLoop()
        observeBatteryAndPreferences()
    }

    private fun setupDeliveryCallbacks() {
        nearbyManager.onPayloadSuccessCallback = { messageId ->
            scope.launch(Dispatchers.IO) {
                messageDao.updateMessageStatus(messageId, MessageStatus.SENDING)
            }
        }
        nearbyManager.onPayloadFailureCallback = { messageId ->
            scope.launch(Dispatchers.IO) {
                Log.w("MeshRouter", "Nearby reported payload failure for message $messageId")
            }
        }
    }

    private fun observeBatteryAndPreferences() {
        if (batteryPrefsRepo != null) {
            scope.launch(Dispatchers.IO) {
                batteryPrefsRepo.scanModeFlow.collectLatest { currentScanMode = it }
            }
            scope.launch(Dispatchers.IO) {
                batteryPrefsRepo.relayForOthersFlow.collectLatest { relayForOthers = it }
            }
            scope.launch(Dispatchers.IO) {
                batteryPrefsRepo.relayOnlyWhileChargingFlow.collectLatest { relayOnlyWhileCharging = it }
            }
        }
    }

    private fun isDeviceCharging(): Boolean {
        if (context == null) return false
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter) ?: return false
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getBatteryPercentage(): Int {
        if (context == null) return 100
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter) ?: return 100
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    private fun listenForIncomingPackets() {
        scope.launch(Dispatchers.IO) {
            nearbyManager.incomingPayloads.collect { (sourceEndpointId, bytes) ->
                scope.launch(Dispatchers.IO) {
                    val frame = WireFrame.decode(bytes)
                    if (frame != null) {
                        if (frame.type == FrameType.MSG) {
                            handleIncomingBytes(sourceEndpointId, frame.body)
                        } else {
                            contactSignaling?.handleFrame(sourceEndpointId, frame)
                        }
                    } else {
                        // Attempt fallback to raw MeshPacket
                        handleIncomingBytes(sourceEndpointId, bytes)
                    }
                }
            }
        }
    }

    private fun setupStoreAndForwardTrigger() {
        nearbyManager.addOnPeerConnectedListener { peerMtId ->
            scope.launch(Dispatchers.IO) {
                sendHello(peerMtId)
                flushQueuedMessages()
                forwardStoredRelayPackets()
            }
        }
    }

    suspend fun sendHello(targetPeerMtId: String? = null) {
        val now = System.currentTimeMillis()
        val sig = try {
            val dataToSign = "$myMeshId|$myNickname|$myPublicKeyBase64|$now".toByteArray(Charsets.UTF_8)
            CryptoEngine.signData(keyPair.private, dataToSign)
        } catch (_: Exception) {
            ""
        }
        val hello = HelloPayload(
            meshId = myMeshId,
            displayName = myNickname,
            publicKeyBase64 = myPublicKeyBase64,
            timestamp = now,
            signature = sig
        )
        val helloBytes = WireFrame(v = 1, type = FrameType.HELLO, body = hello.toByteArray()).toByteArray()
        val targetEndpoint = if (targetPeerMtId != null) nearbyManager.mtIdToEndpoint[targetPeerMtId] else null
        if (targetEndpoint != null) {
            nearbyManager.sendPayload(targetEndpoint, helloBytes)
        } else {
            nearbyManager.broadcastPayload(helloBytes)
        }
    }

    private fun startPeriodicOutboxRetryLoop() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                try {
                    retryOutboxPending()
                } catch (e: Exception) {
                    Log.w("MeshRouter", "Outbox retry loop error", e)
                }
            }
        }
    }

    private fun startPeriodicExpiryAndCleanupLoop() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000L)
                try {
                    val now = System.currentTimeMillis()
                    messageDao.markExpiredMessagesFailed(now)
                    relayPacketDao?.deleteExpired(now)

                    val pct = getBatteryPercentage()
                    val charging = isDeviceCharging()
                    if (pct < 15 && !charging) {
                        _batteryNoticeFlow.value = "Battery under 15%. Dropped to Low power mode to save battery."
                    } else {
                        _batteryNoticeFlow.value = null
                    }
                } catch (e: Exception) {
                    Log.w("MeshRouter", "Cleanup loop error", e)
                }
            }
        }
    }

    suspend fun flushQueuedMessages() {
        retryOutboxPending()
    }

    suspend fun retryOutboxPending() {
        val now = System.currentTimeMillis()
        messageDao.markExpiredMessagesFailed(now)

        val pendingList = messageDao.getPendingOutboxMessages(now)
        val connectedPeers = nearbyManager.connectedPeers.value

        for (msg in pendingList) {
            if (isSenderBlocked(msg.peerId)) {
                messageDao.cancelPendingOutboxForPeer(msg.peerId)
                continue
            }

            if (msg.attemptCount >= 10) {
                messageDao.updateMessageStatus(msg.messageId, MessageStatus.FAILED)
                continue
            }

            val backoffMs = (1L shl msg.attemptCount.coerceAtMost(6)) * 1000L
            if (now - msg.lastAttemptAt < backoffMs) continue

            val queued = queuedMessageDao.getAllQueued().firstOrNull { it.messageId == msg.messageId }
            if (queued != null && connectedPeers.isNotEmpty()) {
                val nextAttempts = msg.attemptCount + 1
                messageDao.updateDeliveryAttempt(msg.messageId, MessageStatus.SENDING, nextAttempts, now)
                val targetEndpoint = nearbyManager.mtIdToEndpoint[msg.peerId]
                if (targetEndpoint != null) {
                    nearbyManager.sendPayload(targetEndpoint, queued.packetBytes, messageId = msg.messageId)
                } else {
                    nearbyManager.broadcastPayload(queued.packetBytes, messageId = msg.messageId)
                }
            }
        }
    }

    private suspend fun forwardStoredRelayPackets() {
        if (relayPacketDao == null || !relayForOthers) return
        if (relayOnlyWhileCharging && !isDeviceCharging()) return

        val stored = relayPacketDao.getActiveRelayPackets()
        for (item in stored) {
            nearbyManager.broadcastPayload(item.packetBytes)
        }
    }

    private suspend fun isSenderBlocked(senderId: String): Boolean {
        if (blockedUserDao != null && blockedUserDao.isBlocked(senderId)) return true
        if (contactDao != null) {
            val contact = contactDao.getContact(senderId) ?: contactDao.getContactByMeshId(senderId)
            if (contact?.isBlocked == true) return true
        }
        return false
    }

    private suspend fun isSenderMuted(senderId: String): Boolean {
        if (contactDao != null) {
            val contact = contactDao.getContact(senderId) ?: contactDao.getContactByMeshId(senderId)
            return contact?.isMuted == true
        }
        return false
    }

    suspend fun retryMessage(messageId: String): Boolean {
        val queued = queuedMessageDao.getAllQueued().firstOrNull { it.messageId == messageId } ?: return false
        val now = System.currentTimeMillis()
        messageDao.updateDeliveryAttempt(messageId, MessageStatus.SENDING, 1, now)
        nearbyManager.broadcastPayload(queued.packetBytes)
        return true
    }

    private suspend fun handleIncomingBytes(sourceEndpointId: String, bytes: ByteArray) {
        // HELLO and CONN_REQUEST/ACCEPT/DECLINE/CANCEL are handled by WireFrame + ContactSignaling.
        // This method only handles raw MeshPacket data (MSG frames).

        // Try to parse as HELLO (legacy fallback for non-WireFrame peers)
        val hello = HelloPayload.fromByteArray(bytes)
        if (hello != null) {
            Log.i("MeshRouter", "Received raw HELLO from ${hello.meshId} (${hello.displayName})")
            nearbyManager.endpointToMtId[sourceEndpointId] = hello.meshId
            nearbyManager.mtIdToEndpoint[hello.meshId] = sourceEndpointId

            // Upsert contact's public key if existing contact or pending request
            if (contactDao != null) {
                val existing = contactDao.getContactByMeshId(hello.meshId)
                    ?: contactDao.getContact(hello.meshId)
                if (existing != null) {
                    val updated = existing.copy(
                        meshTalkId = hello.meshId,
                        nickname = hello.displayName.ifBlank { existing.nickname },
                        publicKeyBase64 = hello.publicKeyBase64.ifBlank { existing.publicKeyBase64 },
                        lastSeen = System.currentTimeMillis()
                    )
                    contactDao.upsertContact(updated)
                }
            }
            if (connectionRequestDao != null) {
                val req = connectionRequestDao.getPendingRequestBetween(hello.meshId, myMeshId)
                if (req != null && req.fromPublicKey.isBlank()) {
                    connectionRequestDao.insertRequest(req.copy(fromPublicKey = hello.publicKeyBase64, fromNickname = hello.displayName))
                }
            }
            return
        }

        // Normal MeshPacket
        val packet = MeshPacket.fromByteArray(bytes) ?: return

        val now = System.currentTimeMillis()
        val skewTolerance = 10 * 60 * 1000L // +/- 10 minutes tolerance

        if (packet.expiresAt <= (now - skewTolerance) || packet.timestamp < (now - 7 * 24 * 60 * 60 * 1000L) || packet.timestamp > (now + skewTolerance + 3600_000L)) {
            Log.w("MeshRouter", "Dropped expired or timestamp-invalid packet: ${packet.messageId}, timestamp=${packet.timestamp}, expiresAt=${packet.expiresAt}, localNow=$now")
            return
        }

        if (seenPacketDao.hasSeen(packet.messageId)) return
        seenPacketDao.markSeen(SeenPacketEntity(packet.messageId))

        val isForMe = packet.recipientId == myMeshId
        val isBroadcast = packet.recipientId == MeshPacket.DESTINATION_BROADCAST

        if (isForMe && contactDao != null && !packet.isAck) {
            val isContact = contactDao.isAcceptedContact(packet.senderId) || contactDao.isAcceptedContactByDeviceId(packet.senderId)
            if (!isContact) {
                Log.w("MeshRouter", "Dropped MSG packet from non-contact: ${packet.senderId}")
                return
            }
        }

        Log.i(
            "MeshRouter",
            "Packet ${packet.messageId} sender=${packet.senderId} recipient=${packet.recipientId} " +
                    "action=${if (isForMe) "ACCEPTED_FOR_ME" else if (isBroadcast) "ACCEPTED_BROADCAST" else "RELAY_OR_DROP"} " +
                    "reason=${if (isForMe || isBroadcast) "Matched recipient" else "Recipient is another node"}"
        )

        if ((isForMe || isBroadcast) && isSenderBlocked(packet.senderId)) {
            Log.d("MeshRouter", "Silently dropping packet ${packet.messageId} from blocked user ${packet.senderId}")
            return
        }

        if (packet.isAck) {
            handleAck(packet)
            if (packet.hopCount + 1 < packet.hopLimit && packet.expiresAt > now) {
                val relayedAck = packet.copy(hopCount = packet.hopCount + 1)
                nearbyManager.broadcastPayload(relayedAck.toByteArray(), excludeEndpointId = sourceEndpointId)
            }
            return
        }

        if (isForMe || isBroadcast) {
            val decryptedContent = if (isBroadcast) {
                packet.ciphertext
            } else {
                try {
                    if (packet.senderPublicKey.isNotBlank() && packet.signature.isNotBlank()) {
                        val senderPubBytes = CryptoEngine.base64ToBytes(packet.senderPublicKey)
                        val signedData = "${packet.messageId}:${packet.senderId}:${packet.recipientId}:${packet.ciphertext}".toByteArray(Charsets.UTF_8)
                        val valid = CryptoEngine.verifySignature(senderPubBytes, signedData, packet.signature)
                        if (!valid) {
                            Log.w("MeshRouter", "Envelope signature verification failed for ${packet.messageId}")
                            return
                        }
                    }

                    CryptoEngine.decryptFromPeer(
                        myPrivateKey = keyPair.private,
                        ephemeralPublicKeyBase64 = packet.ephemeralPubKey,
                        nonceBase64 = packet.nonce,
                        ciphertextBase64 = packet.ciphertext
                    )
                } catch (e: Exception) {
                    Log.w("MeshRouter", "Decryption failed for packet ${packet.messageId}", e)
                    "[Encrypted message could not be decrypted]"
                }
            }

            val contact = contactDao?.getContact(packet.senderId) ?: contactDao?.getContactByMeshId(packet.senderId)
            val expiresAt = if (contact != null && contact.disappearingMessagesDurationSec > 0) {
                packet.timestamp + (contact.disappearingMessagesDurationSec * 1000L)
            } else {
                packet.timestamp + 7 * 24 * 60 * 60 * 1000L
            }

            val entity = MessageEntity(
                messageId = packet.messageId,
                peerId = if (isBroadcast) MeshPacket.DESTINATION_BROADCAST else packet.senderId,
                senderId = packet.senderId,
                senderName = packet.senderName,
                content = decryptedContent,
                timestamp = packet.timestamp,
                isOutgoing = false,
                hopCount = packet.hopCount,
                status = MessageStatus.DELIVERED,
                expiresAt = expiresAt
            )
            messageDao.insertMessage(entity)

            if (!isSenderMuted(packet.senderId)) {
                _incomingMessages.emit(entity)
            }

            if (isForMe) {
                sendAck(originalSenderId = packet.senderId, originalMessageId = packet.messageId)
            }
        }

        val shouldRelay = !isForMe && (packet.hopCount + 1 < packet.hopLimit) && (packet.expiresAt > now)
        if (shouldRelay && relayForOthers) {
            if (relayOnlyWhileCharging && !isDeviceCharging()) return

            Log.d("MeshRouter", "Relaying packet msgId=${packet.messageId} hop=${packet.hopCount + 1}/${packet.hopLimit}")
            val relayedPacket = packet.copy(hopCount = packet.hopCount + 1)
            val relayedBytes = relayedPacket.toByteArray()

            if (relayPacketDao != null) {
                try {
                    val count = relayPacketDao.getRelayCount()
                    val totalBytes = relayPacketDao.getTotalSizeBytes()
                    if (count >= 500 || totalBytes >= 50 * 1024 * 1024) {
                        relayPacketDao.evictOldest(50)
                    }
                    relayPacketDao.insertRelay(
                        RelayPacketEntity(
                            messageId = relayedPacket.messageId,
                            packetBytes = relayedBytes,
                            sizeBytes = relayedBytes.size,
                            expiresAt = relayedPacket.expiresAt
                        )
                    )
                } catch (e: Exception) {
                    Log.w("MeshRouter", "Relay store error", e)
                }
            }

            nearbyManager.broadcastPayload(relayedBytes, excludeEndpointId = sourceEndpointId)
        }
    }

    private suspend fun handleAck(ackPacket: MeshPacket) {
        if (ackPacket.recipientId == myMeshId) {
            Log.i("MeshRouter", "ACK received for message ${ackPacket.ciphertext}")
            messageDao.updateMessageStatus(ackPacket.ciphertext, MessageStatus.DELIVERED)
            queuedMessageDao.deleteQueued(ackPacket.ciphertext)
            relayPacketDao?.deleteRelay(ackPacket.ciphertext)
        }
    }

    private suspend fun sendAck(originalSenderId: String, originalMessageId: String) {
        val ackPacket = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            senderId = myMeshId,
            recipientId = originalSenderId,
            senderName = myNickname,
            ciphertext = originalMessageId,
            hopLimit = MeshPacket.DEFAULT_MAX_HOPS,
            hopCount = 0,
            expiresAt = System.currentTimeMillis() + MeshPacket.RELAY_EXPIRY_MS,
            isAck = true
        )
        seenPacketDao.markSeen(SeenPacketEntity(ackPacket.messageId))
        val targetEndpoint = nearbyManager.mtIdToEndpoint[originalSenderId]
        if (targetEndpoint != null) {
            nearbyManager.sendPayload(targetEndpoint, ackPacket.toByteArray())
        } else {
            nearbyManager.broadcastPayload(ackPacket.toByteArray())
        }
    }

    suspend fun sendMessage(
        recipientId: String,
        recipientPublicKey: String? = null,
        content: String,
        hopLimit: Int = MeshPacket.DEFAULT_MAX_HOPS
    ): MeshPacket {
        val isBroadcast = recipientId == MeshPacket.DESTINATION_BROADCAST
        if (!isBroadcast && contactDao != null) {
            val isAccepted = contactDao.isAcceptedContact(recipientId) || contactDao.isAcceptedContactByDeviceId(recipientId)
            if (!isAccepted) {
                throw IllegalStateException("Cannot send message to non-contact: $recipientId. Connection request must be accepted first.")
            }
        }

        val (ciphertext, nonce, ephemKey) = if (isBroadcast || recipientPublicKey.isNullOrBlank()) {
            Triple(content, "", "")
        } else {
            val enc = CryptoEngine.encryptForPeer(recipientPublicKey, content)
            Triple(enc.ciphertext, enc.nonce, enc.ephemeralPublicKey)
        }

        val messageId = UUID.randomUUID().toString()

        val signature = try {
            val signedData = "$messageId:$myMeshId:$recipientId:$ciphertext".toByteArray(Charsets.UTF_8)
            CryptoEngine.signData(keyPair.private, signedData)
        } catch (_: Exception) {
            ""
        }

        val now = System.currentTimeMillis()
        val packet = MeshPacket(
            messageId = messageId,
            senderId = myMeshId,
            recipientId = recipientId,
            senderName = myNickname,
            senderPublicKey = myPublicKeyBase64,
            ephemeralPubKey = ephemKey,
            nonce = nonce,
            ciphertext = ciphertext,
            signature = signature,
            hopLimit = hopLimit,
            hopCount = 0,
            timestamp = now,
            expiresAt = now + MeshPacket.DIRECT_EXPIRY_MS,
            isAck = false
        )

        seenPacketDao.markSeen(SeenPacketEntity(messageId))

        val connectedCount = nearbyManager.connectedPeers.value.size
        val initialStatus = if (connectedCount > 0) MessageStatus.SENDING else MessageStatus.PENDING

        val entity = MessageEntity(
            messageId = messageId,
            peerId = recipientId,
            senderId = myMeshId,
            senderName = myNickname,
            content = content,
            timestamp = packet.timestamp,
            isOutgoing = true,
            hopCount = 0,
            status = initialStatus,
            attemptCount = if (connectedCount > 0) 1 else 0,
            lastAttemptAt = if (connectedCount > 0) now else 0L,
            expiresAt = now + MeshPacket.DIRECT_EXPIRY_MS
        )
        messageDao.insertMessage(entity)

        val rawPacketBytes = packet.toByteArray()
        val wireFrame = WireFrame(v = 1, type = FrameType.MSG, body = rawPacketBytes)
        val packetBytes = wireFrame.toByteArray()
        queuedMessageDao.insertQueued(QueuedMessageEntity(messageId = messageId, packetBytes = packetBytes))

        if (connectedCount > 0) {
            val targetEndpoint = nearbyManager.mtIdToEndpoint[recipientId]
            if (targetEndpoint != null) {
                nearbyManager.sendPayload(targetEndpoint, packetBytes, messageId = messageId)
            } else {
                nearbyManager.broadcastPayload(packetBytes, messageId = messageId)
            }
        }

        return packet
    }

    suspend fun broadcastConnectionRequest(request: ConnectionRequestEntity) {
        val payload = ConnRequestPayload(
            requestId = request.requestId,
            fromMeshId = request.fromMeshId,
            toMeshId = request.toMeshId,
            fromName = request.fromNickname,
            fromPublicKey = request.fromPublicKey,
            timestamp = request.timestamp,
            signature = request.signature
        )
        val signedPayload = if (payload.signature.isBlank()) {
            val sig = try {
                CryptoEngine.signData(keyPair.private, payload.canonicalString().toByteArray(Charsets.UTF_8))
            } catch (_: Exception) { "" }
            payload.copy(signature = sig)
        } else payload
        val frame = WireFrame(v = 1, type = FrameType.CONN_REQUEST, body = signedPayload.toByteArray())
        val bytes = frame.toByteArray()
        val targetEndpoint = nearbyManager.mtIdToEndpoint[request.toMeshId]
        if (targetEndpoint != null) {
            nearbyManager.sendPayload(targetEndpoint, bytes)
        } else {
            nearbyManager.broadcastPayload(bytes)
        }
    }
}
