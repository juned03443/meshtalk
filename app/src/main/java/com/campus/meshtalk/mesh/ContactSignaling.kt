package com.campus.meshtalk.mesh

import android.content.Context
import android.util.Log
import com.campus.meshtalk.crypto.CryptoEngine
import com.campus.meshtalk.data.local.dao.BlockedUserDao
import com.campus.meshtalk.data.local.dao.ConnectionRequestDao
import com.campus.meshtalk.data.local.dao.ContactDao
import com.campus.meshtalk.data.local.dao.SignalingOutboxDao
import com.campus.meshtalk.data.local.entity.*
import com.campus.meshtalk.data.preferences.MeshBatteryPreferencesRepository
import com.campus.meshtalk.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap

class ContactSignaling(
    private val context: Context,
    val myMeshId: String,
    val myNickname: String,
    private val keyPair: KeyPair,
    val nearbyManager: NearbyMeshManager,
    private val contactDao: ContactDao,
    private val connectionRequestDao: ConnectionRequestDao,
    private val blockedUserDao: BlockedUserDao,
    private val signalingOutboxDao: SignalingOutboxDao,
    private val batteryPrefsRepo: MeshBatteryPreferencesRepository? = null,
    private val scope: CoroutineScope
) {
    companion object {
        const val TAG = "MeshContacts"
        const val CLOCK_SKEW_TOLERANCE_MS = 10 * 60 * 1000L
        const val DECLINE_COOLDOWN_MS = 24 * 60 * 60 * 1000L
        const val MAX_REQUESTS_PER_SENDER_24H = 5
        const val MAX_INCOMING_PENDING = 50
    }

    private val seenRequests = ConcurrentHashMap.newKeySet<String>()
    private var relayForOthers = true
    private var outboxRetryJob: Job? = null

    init {
        observeBatteryPrefs()
        startPeriodicOutboxRetryLoop()
    }

    private fun observeBatteryPrefs() {
        if (batteryPrefsRepo != null) {
            scope.launch(Dispatchers.IO) {
                batteryPrefsRepo.relayForOthersFlow.collectLatest { relayForOthers = it }
            }
        }
    }

    private fun startPeriodicOutboxRetryLoop() {
        outboxRetryJob?.cancel()
        outboxRetryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                try {
                    retrySignalingOutbox()
                    connectionRequestDao.expireOldRequests()
                    signalingOutboxDao.pruneExpired()
                } catch (e: Exception) {
                    Log.w(TAG, "Outbox retry loop error", e)
                }
            }
        }
    }

    fun onPeerConnected(remoteMtId: String) {
        scope.launch(Dispatchers.IO) {
            sendHello(remoteMtId)
            retrySignalingOutbox()
        }
    }

    suspend fun sendHello(targetPeerMtId: String? = null) {
        val now = System.currentTimeMillis()
        val pubKey = CryptoEngine.keyToBase64(keyPair.public.encoded)
        val sig = try {
            val data = "$myMeshId|$myNickname|$pubKey|$now".toByteArray(Charsets.UTF_8)
            CryptoEngine.signData(keyPair.private, data)
        } catch (e: Exception) {
            ""
        }
        val hello = HelloPayload(
            meshId = myMeshId,
            displayName = myNickname,
            publicKeyBase64 = pubKey,
            timestamp = now,
            signature = sig
        )
        val frame = WireFrame(v = 1, type = FrameType.HELLO, body = hello.toByteArray())
        sendWireFrame(frame, targetPeerMtId, floodFallback = false)
        Log.i(TAG, "Sent HELLO from $myMeshId to ${targetPeerMtId ?: "ALL"}")
    }

    fun sendWireFrame(frame: WireFrame, targetMeshId: String?, floodFallback: Boolean = true) {
        val bytes = frame.toByteArray()
        val targetEndpoint = if (targetMeshId != null) nearbyManager.mtIdToEndpoint[targetMeshId] else null
        if (targetEndpoint != null) {
            nearbyManager.sendPayload(targetEndpoint, bytes)
        } else if (floodFallback) {
            nearbyManager.broadcastPayload(bytes)
        }
    }

    suspend fun retrySignalingOutbox() {
        val now = System.currentTimeMillis()
        val pending = signalingOutboxDao.getPendingOutbox(now)
        val connectedPeers = nearbyManager.connectedPeers.value

        for (item in pending) {
            if (now > item.expiresAt || item.attempts >= 15) {
                signalingOutboxDao.deleteById(item.id)
                continue
            }
            if (connectedPeers.isNotEmpty()) {
                val frame = WireFrame.decode(item.payloadBytes)
                if (frame != null) {
                    sendWireFrame(frame, item.targetMeshId, floodFallback = true)
                }
                val nextDelay = (1L shl item.attempts.coerceAtMost(6)) * 1000L
                signalingOutboxDao.updateAttempt(item.id, item.attempts + 1, now + nextDelay)
                connectionRequestDao.updateSendAttempt(item.requestId, now, item.attempts + 1)
            }
        }
    }

    suspend fun handleFrame(sourceEndpointId: String, frame: WireFrame) {
        when (frame.type) {
            FrameType.HELLO -> handleHello(sourceEndpointId, frame.body)
            FrameType.CONN_REQUEST -> handleConnRequest(sourceEndpointId, frame.body)
            FrameType.CONN_ACCEPT -> handleConnAccept(frame.body)
            FrameType.CONN_DECLINE -> handleConnDecline(frame.body)
            FrameType.CONN_CANCEL -> handleConnCancel(frame.body)
            FrameType.MSG -> {
                Log.w(TAG, "Unexpected MSG frame routed to ContactSignaling")
            }
        }
    }

    private suspend fun handleHello(sourceEndpointId: String, body: ByteArray) {
        val hello = HelloPayload.fromByteArray(body) ?: return
        val now = System.currentTimeMillis()

        if (kotlin.math.abs(now - hello.timestamp) > CLOCK_SKEW_TOLERANCE_MS) {
            Log.w(TAG, "HELLO dropped due to clock skew: ${hello.timestamp} vs local $now")
            return
        }

        if (hello.signature.isNotBlank()) {
            val pubBytes = CryptoEngine.base64ToBytes(hello.publicKeyBase64)
            val data = hello.canonicalString().toByteArray(Charsets.UTF_8)
            if (!CryptoEngine.verifySignature(pubBytes, data, hello.signature)) {
                Log.w(TAG, "HELLO signature verification failed for ${hello.meshId}")
                return
            }
        }

        nearbyManager.endpointToMtId[sourceEndpointId] = hello.meshId
        nearbyManager.mtIdToEndpoint[hello.meshId] = sourceEndpointId

        val existingContact = contactDao.getContactByMeshId(hello.meshId)
        if (existingContact != null) {
            if (existingContact.publicKeyBase64.isNotBlank() && existingContact.publicKeyBase64 != hello.publicKeyBase64) {
                Log.w(TAG, "TOFU Key mismatch for contact ${hello.meshId}! Marking keyChanged.")
                contactDao.upsertContact(existingContact.copy(keyChanged = true, lastSeen = now))
            } else {
                val updated = existingContact.copy(
                    nickname = hello.displayName.ifBlank { existingContact.nickname },
                    publicKeyBase64 = hello.publicKeyBase64.ifBlank { existingContact.publicKeyBase64 },
                    lastSeen = now
                )
                contactDao.upsertContact(updated)
            }
        }
        Log.i(TAG, "Processed HELLO from ${hello.meshId} (${hello.displayName})")
    }

    private suspend fun handleConnRequest(sourceEndpointId: String, body: ByteArray) {
        val req = ConnRequestPayload.fromByteArray(body) ?: run {
            Log.w(TAG, "CONN_REQUEST parse failed")
            return
        }

        if (!seenRequests.add(req.requestId)) {
            Log.d(TAG, "Duplicate CONN_REQUEST ${req.requestId} ignored")
            return
        }

        val now = System.currentTimeMillis()
        if (req.expiresAt <= now || (req.timestamp - now) > CLOCK_SKEW_TOLERANCE_MS) {
            Log.w(TAG, "CONN_REQUEST expired or future timestamp: ts=${req.timestamp}, exp=${req.expiresAt}, now=$now")
            return
        }

        if (!CryptoEngine.isValidMeshTalkId(req.fromMeshId) || !CryptoEngine.isValidMeshTalkId(req.toMeshId)) {
            Log.w(TAG, "Invalid MeshTalk ID in request: from=${req.fromMeshId}, to=${req.toMeshId}")
            return
        }

        if (req.fromMeshId == req.toMeshId) {
            Log.w(TAG, "Self-request rejected")
            return
        }

        if (req.toMeshId != myMeshId) {
            if (req.hopCount + 1 < req.hopLimit && relayForOthers) {
                val relayed = req.copy(hopCount = req.hopCount + 1)
                val frame = WireFrame(v = 1, type = FrameType.CONN_REQUEST, body = relayed.toByteArray())
                nearbyManager.broadcastPayload(frame.toByteArray(), excludeEndpointId = sourceEndpointId)
                Log.d(TAG, "Relayed CONN_REQUEST ${req.requestId} hop=${relayed.hopCount}/${relayed.hopLimit}")
            }
            return
        }

        Log.i(TAG, "Received CONN_REQUEST ${req.requestId} from ${req.fromMeshId} to $myMeshId")

        val pubBytes = try { CryptoEngine.base64ToBytes(req.fromPublicKey) } catch (_: Exception) { null }
        if (pubBytes == null || !CryptoEngine.verifySignature(pubBytes, req.canonicalString().toByteArray(Charsets.UTF_8), req.signature)) {
            Log.w(TAG, "CONN_REQUEST signature verification failed for ${req.requestId}")
            return
        }

        if (blockedUserDao.isBlocked(req.fromMeshId)) {
            Log.d(TAG, "Silently dropping request from blocked sender ${req.fromMeshId}")
            return
        }

        val requests24h = connectionRequestDao.countRequestsFromSenderSince(req.fromMeshId, now - 24 * 60 * 60 * 1000L)
        if (requests24h >= MAX_REQUESTS_PER_SENDER_24H) {
            Log.w(TAG, "Rate limit exceeded for sender ${req.fromMeshId} ($requests24h requests in 24h)")
            return
        }
        val incomingPending = connectionRequestDao.countIncomingPending()
        if (incomingPending >= MAX_INCOMING_PENDING) {
            Log.w(TAG, "Maximum incoming pending requests reached ($incomingPending)")
            return
        }

        val ourOutgoing = connectionRequestDao.getPendingRequestBetween(myMeshId, req.fromMeshId)
        if (ourOutgoing != null) {
            Log.i(TAG, "Mutual request detected with ${req.fromMeshId}! Auto-accepting both.")
            connectionRequestDao.updateStatus(ourOutgoing.requestId, RequestStatus.ACCEPTED)
            connectionRequestDao.insertRequest(
                ConnectionRequestEntity(
                    requestId = req.requestId,
                    fromDeviceId = req.fromMeshId,
                    fromMeshId = req.fromMeshId,
                    fromNickname = req.fromName,
                    fromPublicKey = req.fromPublicKey,
                    toMeshId = myMeshId,
                    status = RequestStatus.ACCEPTED,
                    direction = RequestDirection.INCOMING,
                    signature = req.signature
                )
            )
            val contact = ContactEntity(
                deviceId = req.fromMeshId,
                meshTalkId = req.fromMeshId,
                nickname = req.fromName.ifBlank { "Mesh Peer" },
                publicKeyBase64 = req.fromPublicKey,
                avatarInitial = req.fromName.firstOrNull()?.uppercase() ?: "M",
                lastSeen = now
            )
            contactDao.upsertContact(contact)
            sendConnAccept(req.requestId, req.fromMeshId)
            return
        }

        if (contactDao.isAcceptedContact(req.fromMeshId)) {
            Log.i(TAG, "Sender ${req.fromMeshId} is already a contact. Replying CONN_ACCEPT.")
            sendConnAccept(req.requestId, req.fromMeshId)
            return
        }

        val reqEntity = ConnectionRequestEntity(
            requestId = req.requestId,
            fromDeviceId = req.fromMeshId,
            fromMeshId = req.fromMeshId,
            fromNickname = req.fromName,
            fromPublicKey = req.fromPublicKey,
            toMeshId = myMeshId,
            timestamp = req.timestamp,
            expiresAt = req.expiresAt,
            status = RequestStatus.PENDING,
            direction = RequestDirection.INCOMING,
            signature = req.signature
        )
        connectionRequestDao.insertRequest(reqEntity)

        NotificationHelper.showConnectionRequestNotification(
            context = context,
            fromMeshId = req.fromMeshId,
            fromNickname = req.fromName,
            requestId = req.requestId
        )
    }

    private suspend fun handleConnAccept(body: ByteArray) {
        val resp = ConnResponsePayload.fromByteArray(body) ?: return
        val now = System.currentTimeMillis()

        if (resp.toMeshId != myMeshId) return

        Log.i(TAG, "Received CONN_ACCEPT for request ${resp.requestId} from ${resp.fromMeshId}")

        val pubBytes = try { CryptoEngine.base64ToBytes(resp.responderPublicKey) } catch (_: Exception) { null }
        if (pubBytes == null || !CryptoEngine.verifySignature(pubBytes, resp.canonicalString().toByteArray(Charsets.UTF_8), resp.signature)) {
            Log.w(TAG, "CONN_ACCEPT signature verification failed for ${resp.requestId}")
            return
        }

        connectionRequestDao.updateStatus(resp.requestId, RequestStatus.ACCEPTED)
        signalingOutboxDao.deleteByRequestId(resp.requestId)

        val existing = contactDao.getContactByMeshId(resp.fromMeshId)
        if (existing != null) {
            if (existing.publicKeyBase64.isNotBlank() && existing.publicKeyBase64 != resp.responderPublicKey) {
                Log.w(TAG, "TOFU Key mismatch on accept for ${resp.fromMeshId}! Marking keyChanged.")
                contactDao.upsertContact(existing.copy(keyChanged = true, lastSeen = now))
            } else {
                contactDao.upsertContact(existing.copy(publicKeyBase64 = resp.responderPublicKey, nickname = resp.responderName, lastSeen = now))
            }
        } else {
            val contact = ContactEntity(
                deviceId = resp.fromMeshId,
                meshTalkId = resp.fromMeshId,
                nickname = resp.responderName.ifBlank { "Mesh Peer" },
                publicKeyBase64 = resp.responderPublicKey,
                avatarInitial = resp.responderName.firstOrNull()?.uppercase() ?: "M",
                lastSeen = now
            )
            contactDao.upsertContact(contact)
        }

        NotificationHelper.showMessageNotification(
            context = context,
            senderPeerId = resp.fromMeshId,
            senderName = resp.responderName,
            messageText = "${resp.responderName} accepted your connection request"
        )
    }

    private suspend fun handleConnDecline(body: ByteArray) {
        val resp = ConnResponsePayload.fromByteArray(body) ?: return
        if (resp.toMeshId != myMeshId) return

        Log.i(TAG, "Received CONN_DECLINE for request ${resp.requestId} from ${resp.fromMeshId}")
        connectionRequestDao.updateStatus(resp.requestId, RequestStatus.DECLINED)
        signalingOutboxDao.deleteByRequestId(resp.requestId)

        NotificationHelper.showMessageNotification(
            context = context,
            senderPeerId = resp.fromMeshId,
            senderName = resp.responderName,
            messageText = "${resp.responderName} declined your connection request"
        )
    }

    private suspend fun handleConnCancel(body: ByteArray) {
        val cancel = ConnCancelPayload.fromByteArray(body) ?: return
        if (cancel.toMeshId != myMeshId) return

        Log.i(TAG, "Received CONN_CANCEL for request ${cancel.requestId} from ${cancel.fromMeshId}")
        connectionRequestDao.updateStatus(cancel.requestId, RequestStatus.CANCELLED)
        signalingOutboxDao.deleteByRequestId(cancel.requestId)
    }

    suspend fun sendConnRequest(targetMeshId: String, requestId: String, fromName: String): Boolean {
        val now = System.currentTimeMillis()
        val pubKey = CryptoEngine.keyToBase64(keyPair.public.encoded)
        val payload = ConnRequestPayload(
            requestId = requestId,
            fromMeshId = myMeshId,
            toMeshId = targetMeshId,
            fromName = fromName,
            fromPublicKey = pubKey,
            timestamp = now
        )
        val sig = CryptoEngine.signData(keyPair.private, payload.canonicalString().toByteArray(Charsets.UTF_8))
        val signedPayload = payload.copy(signature = sig)
        val frame = WireFrame(v = 1, type = FrameType.CONN_REQUEST, body = signedPayload.toByteArray())

        val outboxItem = SignalingOutboxEntity(
            id = "$requestId:CONN_REQUEST",
            requestId = requestId,
            type = "CONN_REQUEST",
            targetMeshId = targetMeshId,
            payloadBytes = frame.toByteArray()
        )
        signalingOutboxDao.insertOutbox(outboxItem)

        sendWireFrame(frame, targetMeshId, floodFallback = true)
        Log.i(TAG, "Sent CONN_REQUEST $requestId to $targetMeshId")
        return true
    }

    suspend fun sendConnAccept(requestId: String, targetMeshId: String) {
        val now = System.currentTimeMillis()
        val pubKey = CryptoEngine.keyToBase64(keyPair.public.encoded)
        val payload = ConnResponsePayload(
            requestId = requestId,
            fromMeshId = myMeshId,
            toMeshId = targetMeshId,
            responderName = myNickname,
            responderPublicKey = pubKey,
            timestamp = now
        )
        val sig = CryptoEngine.signData(keyPair.private, payload.canonicalString().toByteArray(Charsets.UTF_8))
        val signedPayload = payload.copy(signature = sig)
        val frame = WireFrame(v = 1, type = FrameType.CONN_ACCEPT, body = signedPayload.toByteArray())

        val outboxItem = SignalingOutboxEntity(
            id = "$requestId:CONN_ACCEPT",
            requestId = requestId,
            type = "CONN_ACCEPT",
            targetMeshId = targetMeshId,
            payloadBytes = frame.toByteArray()
        )
        signalingOutboxDao.insertOutbox(outboxItem)

        sendWireFrame(frame, targetMeshId, floodFallback = true)
        Log.i(TAG, "Sent CONN_ACCEPT for $requestId to $targetMeshId")
    }

    suspend fun sendConnDecline(requestId: String, targetMeshId: String) {
        val now = System.currentTimeMillis()
        val payload = ConnResponsePayload(
            requestId = requestId,
            fromMeshId = myMeshId,
            toMeshId = targetMeshId,
            responderName = myNickname,
            responderPublicKey = "",
            timestamp = now
        )
        val sig = CryptoEngine.signData(keyPair.private, payload.canonicalString().toByteArray(Charsets.UTF_8))
        val signedPayload = payload.copy(signature = sig)
        val frame = WireFrame(v = 1, type = FrameType.CONN_DECLINE, body = signedPayload.toByteArray())

        val outboxItem = SignalingOutboxEntity(
            id = "$requestId:CONN_DECLINE",
            requestId = requestId,
            type = "CONN_DECLINE",
            targetMeshId = targetMeshId,
            payloadBytes = frame.toByteArray()
        )
        signalingOutboxDao.insertOutbox(outboxItem)

        sendWireFrame(frame, targetMeshId, floodFallback = true)
        Log.i(TAG, "Sent CONN_DECLINE for $requestId to $targetMeshId")
    }

    suspend fun sendConnCancel(requestId: String, targetMeshId: String) {
        val now = System.currentTimeMillis()
        val payload = ConnCancelPayload(
            requestId = requestId,
            fromMeshId = myMeshId,
            toMeshId = targetMeshId,
            timestamp = now
        )
        val sig = CryptoEngine.signData(keyPair.private, payload.canonicalString().toByteArray(Charsets.UTF_8))
        val signedPayload = payload.copy(signature = sig)
        val frame = WireFrame(v = 1, type = FrameType.CONN_CANCEL, body = signedPayload.toByteArray())

        val outboxItem = SignalingOutboxEntity(
            id = "$requestId:CONN_CANCEL",
            requestId = requestId,
            type = "CONN_CANCEL",
            targetMeshId = targetMeshId,
            payloadBytes = frame.toByteArray()
        )
        signalingOutboxDao.insertOutbox(outboxItem)

        sendWireFrame(frame, targetMeshId, floodFallback = true)
        Log.i(TAG, "Sent CONN_CANCEL for $requestId to $targetMeshId")
    }
}
