package com.campus.meshtalk.data.repository

import com.campus.meshtalk.crypto.CryptoEngine
import com.campus.meshtalk.data.local.dao.ConnectionRequestDao
import com.campus.meshtalk.data.local.dao.ContactDao
import com.campus.meshtalk.data.local.entity.ConnectionRequestEntity
import com.campus.meshtalk.data.local.entity.ContactEntity
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.local.entity.RequestStatus
import kotlinx.coroutines.flow.Flow
import java.security.KeyPair
import java.util.UUID

sealed class SendRequestResult {
    data object Success : SendRequestResult()
    data class Error(val message: String) : SendRequestResult()
    data object OwnId : SendRequestResult()
    data object AlreadyContact : SendRequestResult()
    data object AlreadyPending : SendRequestResult()
    data object AutoAccepted : SendRequestResult()
}

class ContactRepository(
    private val contactDao: ContactDao,
    private val connectionRequestDao: ConnectionRequestDao,
    private val myMeshId: String,
    private val myDeviceId: String,
    private val myNickname: String,
    private val myAvatarUri: String?,
    private val keyPair: KeyPair,
    var contactSignaling: com.campus.meshtalk.mesh.ContactSignaling? = null
) {

    // --- Contacts ---

    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    suspend fun getContact(deviceId: String): ContactEntity? = contactDao.getContact(deviceId)

    suspend fun getContactByMeshId(meshId: String): ContactEntity? =
        contactDao.getContactByMeshId(meshId)

    suspend fun isAcceptedContact(meshId: String): Boolean =
        contactDao.isAcceptedContact(meshId)

    suspend fun deleteContact(deviceId: String) = contactDao.deleteContact(deviceId)

    // --- Connection Requests ---

    fun getIncomingPendingRequests(): Flow<List<ConnectionRequestEntity>> =
        connectionRequestDao.getIncomingPendingRequests()

    fun getOutgoingPendingRequests(): Flow<List<ConnectionRequestEntity>> =
        connectionRequestDao.getOutgoingPendingRequests()

    fun getAllRequests(): Flow<List<ConnectionRequestEntity>> =
        connectionRequestDao.getAllRequests()

    fun getIncomingPendingCount(): Flow<Int> =
        connectionRequestDao.getIncomingPendingCount()

    /**
     * Send a connection request to a target mesh ID.
     * Validates: not own ID, not already a contact, no duplicate pending request.
     * If the target already has a pending incoming request to us, auto-accept both.
     */
    suspend fun sendConnectionRequest(targetMeshId: String, meshRouter: com.campus.meshtalk.mesh.MeshRouter? = null): SendRequestResult {
        val normalizedId = targetMeshId.uppercase().trim()

        // Validate format
        if (!CryptoEngine.isValidMeshTalkId(normalizedId)) {
            return SendRequestResult.Error("Invalid MeshTalk ID format. Use MT-XXXX-XXXX")
        }

        // Block adding own ID
        if (normalizedId == myMeshId.uppercase()) {
            return SendRequestResult.OwnId
        }

        // Already a contact?
        val existingContact = contactDao.getContactByMeshId(normalizedId)
        if (existingContact != null && !existingContact.isBlocked) {
            return SendRequestResult.AlreadyContact
        }

        // Check 24-hour decline cooldown
        val latestReq = connectionRequestDao.getLatestRequestBetween(myMeshId, normalizedId)
        if (latestReq != null && latestReq.status == RequestStatus.DECLINED) {
            val elapsed = System.currentTimeMillis() - latestReq.timestamp
            if (elapsed < 24 * 60 * 60 * 1000L) {
                val remainingHours = ((24 * 60 * 60 * 1000L - elapsed) / (3600 * 1000L)).coerceAtLeast(1)
                return SendRequestResult.Error("Request was declined. You can re-request in $remainingHours hour(s).")
            }
        }

        // Already has a pending request?
        val existingOutgoing = connectionRequestDao.getPendingRequestBetween(myMeshId, normalizedId)
        if (existingOutgoing != null) {
            return SendRequestResult.AlreadyPending
        }

        // Check if the other side already sent us a request (simultaneous/mutual request)
        val reverseRequest = connectionRequestDao.getPendingRequestBetween(normalizedId, myMeshId)
        if (reverseRequest != null) {
            // Auto-accept: both sides want to connect
            acceptRequest(reverseRequest.requestId)
            return SendRequestResult.AutoAccepted
        }

        val requestId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val expiresAt = timestamp + 7 * 24 * 60 * 60 * 1000L
        val hopLimit = 4
        val myPublicKeyBase64 = CryptoEngine.keyToBase64(keyPair.public.encoded)

        val canonicalData = "$requestId|$myMeshId|$normalizedId|$myNickname|$myPublicKeyBase64|$timestamp|$expiresAt|$hopLimit"
        val signature = try {
            val signData = canonicalData.toByteArray(Charsets.UTF_8)
            CryptoEngine.signData(keyPair.private, signData)
        } catch (_: Exception) { "" }

        val request = ConnectionRequestEntity(
            requestId = requestId,
            fromDeviceId = myDeviceId,
            fromMeshId = myMeshId,
            fromNickname = myNickname,
            fromPublicKey = myPublicKeyBase64,
            fromAvatarUri = myAvatarUri,
            toMeshId = normalizedId,
            timestamp = timestamp,
            expiresAt = expiresAt,
            status = RequestStatus.PENDING,
            direction = RequestDirection.OUTGOING,
            signature = signature
        )
        connectionRequestDao.insertRequest(request)
        // Hand to ContactSignaling
        contactSignaling?.sendConnRequest(normalizedId, request.requestId, myNickname)
        meshRouter?.broadcastConnectionRequest(request)
        return SendRequestResult.Success
    }

    /**
     * Handle an incoming connection request received from the mesh.
     * If we already sent them a request, auto-accept.
     */
    suspend fun handleIncomingRequest(request: ConnectionRequestEntity): Boolean {
        // Check if we already sent them a request
        val ourOutgoing = connectionRequestDao.getPendingRequestBetween(myMeshId, request.fromMeshId)
        if (ourOutgoing != null) {
            // Auto-accept both
            connectionRequestDao.updateStatus(ourOutgoing.requestId, RequestStatus.ACCEPTED)
            addContactFromRequest(request)
            return true // auto-accepted
        }

        // Store as incoming
        val incomingRequest = request.copy(direction = RequestDirection.INCOMING)
        connectionRequestDao.insertRequest(incomingRequest)
        return false
    }

    /**
     * Accept an incoming connection request.
     * Creates a contact entry and marks the request as accepted.
     */
    suspend fun acceptRequest(requestId: String): Boolean {
        val request = connectionRequestDao.getRequest(requestId) ?: return false
        if (request.status != RequestStatus.PENDING) return false

        connectionRequestDao.updateStatus(requestId, RequestStatus.ACCEPTED)
        addContactFromRequest(request)
        contactSignaling?.sendConnAccept(requestId, request.fromMeshId)
        return true
    }

    suspend fun declineRequest(requestId: String): Boolean {
        val request = connectionRequestDao.getRequest(requestId) ?: return false
        connectionRequestDao.updateStatus(requestId, RequestStatus.DECLINED)
        contactSignaling?.sendConnDecline(requestId, request.fromMeshId)
        return true
    }

    /**
     * Delete/decline an incoming request silently.
     * The sender is NOT notified. They can request again later.
     */
    suspend fun deleteRequest(requestId: String) {
        connectionRequestDao.deleteRequest(requestId)
    }

    /**
     * Cancel an outgoing request.
     */
    suspend fun cancelRequest(requestId: String) {
        val request = connectionRequestDao.getRequest(requestId)
        connectionRequestDao.updateStatus(requestId, RequestStatus.CANCELLED)
        if (request != null) {
            contactSignaling?.sendConnCancel(requestId, request.toMeshId)
        }
    }

    private suspend fun addContactFromRequest(request: ConnectionRequestEntity) {
        val contact = ContactEntity(
            deviceId = request.fromDeviceId,
            meshTalkId = request.fromMeshId,
            nickname = request.fromNickname.ifBlank { "Mesh Peer" },
            publicKeyBase64 = request.fromPublicKey,
            avatarInitial = request.fromNickname.firstOrNull()?.uppercase() ?: "M",
            status = "",
            lastSeen = System.currentTimeMillis(),
            addedAt = System.currentTimeMillis()
        )
        contactDao.upsertContact(contact)
    }

    /**
     * Expire old pending requests.
     */
    suspend fun expireOldRequests() {
        connectionRequestDao.expireOldRequests()
    }
}
