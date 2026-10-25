package com.campus.meshtalk.data.repository

import com.campus.meshtalk.data.local.dao.BlockedUserDao
import com.campus.meshtalk.data.local.dao.ConnectionRequestDao
import com.campus.meshtalk.data.local.dao.ContactDao
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.local.entity.RequestStatus
import com.campus.meshtalk.mesh.ContactSignaling
import com.campus.meshtalk.mesh.DiscoveredPeerInfo
import com.campus.meshtalk.mesh.NearbyMeshManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

enum class PersonRelation {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    CONTACT,
    BLOCKED
}

data class NearbyPerson(
    val meshId: String,
    val displayName: String,
    val endpointId: String,
    val isConnected: Boolean,
    val relation: PersonRelation,
    val lastSeenAt: Long = System.currentTimeMillis()
)

class NearbyPeopleRepository(
    private val myMeshId: String,
    private val nearbyManager: NearbyMeshManager,
    private val contactDao: ContactDao,
    private val connectionRequestDao: ConnectionRequestDao,
    private val blockedUserDao: BlockedUserDao,
    private val contactSignaling: ContactSignaling?,
    private val contactRepository: ContactRepository,
    private val meshRouter: com.campus.meshtalk.mesh.MeshRouter? = null,
    private val scope: CoroutineScope
) {
    private val _nearbyPeople = MutableStateFlow<List<NearbyPerson>>(emptyList())
    val nearbyPeople: StateFlow<List<NearbyPerson>> = _nearbyPeople.asStateFlow()

    // 15s grace period tracking: meshId -> Job
    private val removalGraceJobs = ConcurrentHashMap<String, Job>()
    private val cachedDiscovered = ConcurrentHashMap<String, DiscoveredPeerInfo>()

    init {
        observeNearbyAndDb()
    }

    private fun observeNearbyAndDb() {
        scope.launch(Dispatchers.IO) {
            combine(
                nearbyManager.discoveredEndpoints,
                nearbyManager.connectedPeers,
                contactDao.getAllContactsIncludingBlocked(),
                connectionRequestDao.getAllRequests()
            ) { discoveredMap, connectedMap, contacts, requests ->
                recomputePeopleList(discoveredMap, connectedMap, contacts, requests)
            }.collectLatest { people ->
                _nearbyPeople.value = people
            }
        }
    }

    private suspend fun recomputePeopleList(
        discoveredMap: Map<String, DiscoveredPeerInfo>,
        connectedMap: Map<String, com.campus.meshtalk.mesh.MeshPeer>,
        contacts: List<com.campus.meshtalk.data.local.entity.ContactEntity>,
        requests: List<com.campus.meshtalk.data.local.entity.ConnectionRequestEntity>
    ): List<NearbyPerson> {
        val now = System.currentTimeMillis()
        val allMeshIds = mutableSetOf<String>()

        // Update cache from discovered endpoints
        discoveredMap.values.forEach { info ->
            if (info.meshId != myMeshId) {
                cachedDiscovered[info.meshId] = info
                allMeshIds.add(info.meshId)
                removalGraceJobs.remove(info.meshId)?.cancel()
            }
        }

        // Add connected peers
        connectedMap.values.forEach { peer ->
            if (peer.meshTalkId != myMeshId) {
                allMeshIds.add(peer.meshTalkId)
                removalGraceJobs.remove(peer.meshTalkId)?.cancel()
            }
        }

        // Check for disappeared nodes that need grace period
        val currentVisible = discoveredMap.values.map { it.meshId }.toSet() + connectedMap.values.map { it.meshTalkId }.toSet()
        for ((meshId, _) in cachedDiscovered) {
            if (!currentVisible.contains(meshId) && !removalGraceJobs.containsKey(meshId)) {
                removalGraceJobs[meshId] = scope.launch {
                    delay(15_000L) // 15 seconds grace period
                    cachedDiscovered.remove(meshId)
                    removalGraceJobs.remove(meshId)
                }
            }
        }

        val result = mutableListOf<NearbyPerson>()

        for (meshId in allMeshIds) {
            if (meshId == myMeshId) continue

            // Blocked?
            val isBlocked = blockedUserDao.isBlocked(meshId) || contacts.any { it.meshTalkId == meshId && it.isBlocked }
            if (isBlocked) continue

            val isContact = contacts.any { it.meshTalkId == meshId && !it.isBlocked }
            val pendingOutgoing = requests.any { it.fromMeshId == myMeshId && it.toMeshId == meshId && it.status == RequestStatus.PENDING }
            val pendingIncoming = requests.any { it.fromMeshId == meshId && it.toMeshId == myMeshId && it.status == RequestStatus.PENDING }

            val relation = when {
                isContact -> PersonRelation.CONTACT
                pendingOutgoing -> PersonRelation.REQUEST_SENT
                pendingIncoming -> PersonRelation.REQUEST_RECEIVED
                else -> PersonRelation.NONE
            }

            val connectedPeer = connectedMap[meshId]
            val isConnected = connectedPeer != null

            val discoveredInfo = cachedDiscovered[meshId]
            val endpointId = connectedPeer?.endpointId ?: discoveredInfo?.endpointId ?: ""
            val displayName = discoveredInfo?.displayName ?: connectedPeer?.deviceName ?: meshId

            result.add(
                NearbyPerson(
                    meshId = meshId,
                    displayName = displayName,
                    endpointId = endpointId,
                    isConnected = isConnected,
                    relation = relation,
                    lastSeenAt = now
                )
            )
        }

        return result.sortedWith(compareByDescending<NearbyPerson> { it.isConnected }.thenBy { it.displayName })
    }

    suspend fun sendRequestToPerson(person: NearbyPerson): SendRequestResult {
        // If not connected, connect first
        if (!person.isConnected && person.endpointId.isNotBlank()) {
            if (NearbyMeshManager.shouldInitiateConnection(myMeshId, person.meshId)) {
                nearbyManager.initiateConnection(person.endpointId, person.meshId)
            }
            // Wait up to 15s for connection or queue anyway
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 15_000L) {
                if (nearbyManager.connectedPeers.value.containsKey(person.meshId)) break
                delay(500L)
            }
        }
        return contactRepository.sendConnectionRequest(person.meshId, meshRouter)
    }
}
