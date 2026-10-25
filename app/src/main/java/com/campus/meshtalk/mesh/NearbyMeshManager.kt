package com.campus.meshtalk.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlin.random.Random

data class MeshPeer(
    val endpointId: String,
    val deviceName: String,
    val meshTalkId: String = deviceName,
    val connectedAt: Long = System.currentTimeMillis()
)

data class DiscoveredPeerInfo(
    val endpointId: String,
    val endpointName: String,
    val meshId: String = endpointName,
    val displayName: String = endpointName,
    val discoveredAt: Long = System.currentTimeMillis()
)

class NearbyMeshManager(private val context: Context) {

    companion object {
        const val TAG = "MeshNearby"
        const val SERVICE_ID = "com.campus.meshtalk"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER

        fun shouldInitiateConnection(localId: String, remoteId: String): Boolean {
            if (localId.isBlank() || remoteId.isBlank()) return false
            return localId < remoteId
        }

        fun calculateBackoff(attempt: Int): Long {
            if (attempt <= 0) return 2000L
            val exp = 1L shl (attempt - 1).coerceAtMost(5)
            return min(30000L, 2000L * exp)
        }

        fun getExceptionStatusString(e: Throwable): String {
            return if (e is ApiException) {
                val code = e.statusCode
                "${ConnectionsStatusCodes.getStatusCodeString(code)} ($code)"
            } else {
                e.message ?: "Unknown error"
            }
        }
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedPeers = MutableStateFlow<Map<String, MeshPeer>>(emptyMap())
    val connectedPeers = _connectedPeers.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<Pair<String, ByteArray>>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<Map<String, DiscoveredPeerInfo>>(emptyMap())
    val discoveredEndpoints = _discoveredEndpoints.asStateFlow()

    private val _inFlightRequests = MutableStateFlow<Set<String>>(emptySet())
    val inFlightRequests = _inFlightRequests.asStateFlow()

    val endpointToMtId = ConcurrentHashMap<String, String>()
    val mtIdToEndpoint = ConcurrentHashMap<String, String>()

    private val endpointRetryCounts = ConcurrentHashMap<String, Int>()
    private val payloadIdToMessageId = ConcurrentHashMap<Long, String>()

    val onPeerConnectedListeners = CopyOnWriteArrayList<((String) -> Unit)>()
    val onPeerDisconnectedListeners = CopyOnWriteArrayList<((String) -> Unit)>()

    fun addOnPeerConnectedListener(listener: (String) -> Unit) {
        onPeerConnectedListeners.add(listener)
    }

    fun removeOnPeerConnectedListener(listener: (String) -> Unit) {
        onPeerConnectedListeners.remove(listener)
    }

    fun addOnPeerDisconnectedListener(listener: (String) -> Unit) {
        onPeerDisconnectedListeners.add(listener)
    }

    fun removeOnPeerDisconnectedListener(listener: (String) -> Unit) {
        onPeerDisconnectedListeners.remove(listener)
    }

    var onPayloadSuccessCallback: ((String) -> Unit)? = null
    var onPayloadFailureCallback: ((String) -> Unit)? = null

    @Volatile
    private var isDesiredRunning = false
    @Volatile
    private var localAdvertisedId: String = ""
    @Volatile
    var localMeshId: String = ""
        private set
    @Volatile
    var localDisplayName: String = ""
        private set

    private var advertiseRetryJob: Job? = null
    private var discoveryRetryJob: Job? = null
    private var watchdogJob: Job? = null

    init {
        startWatchdog()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = managerScope.launch {
            while (isActive) {
                delay(60_000L)
                if (isDesiredRunning) {
                    if (!_isAdvertising.value && localAdvertisedId.isNotBlank()) {
                        Log.i(TAG, "Watchdog: restarting advertising for $localAdvertisedId")
                        startAdvertising(localAdvertisedId)
                    }
                    if (!_isDiscovering.value) {
                        Log.i(TAG, "Watchdog: restarting discovery")
                        startDiscovery()
                    }
                }
            }
        }
    }

    fun setLocalMeshId(meshId: String) {
        localAdvertisedId = meshId
        localMeshId = meshId
    }

    fun setLocalIdentity(meshId: String, displayName: String) {
        localAdvertisedId = meshId
        localMeshId = meshId
        localDisplayName = displayName
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val remoteName = connectionInfo.endpointName
            Log.i(TAG, "onConnectionInitiated: endpointId=$endpointId, remoteName=$remoteName")
            val parsed = EndpointNameHelper.parse(remoteName)
            val remoteMtId = parsed?.meshId ?: remoteName
            if (remoteMtId.isNotBlank()) {
                endpointToMtId[endpointId] = remoteMtId
                mtIdToEndpoint[remoteMtId] = endpointId
            }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.i(TAG, "acceptConnection success for endpointId=$endpointId")
                }
                .addOnFailureListener { e ->
                    val statusStr = getExceptionStatusString(e)
                    Log.e(TAG, "acceptConnection failed for $endpointId: $statusStr", e)
                    _lastError.value = "Accept failed: $statusStr"
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val statusCode = result.status.statusCode
            val statusStr = ConnectionsStatusCodes.getStatusCodeString(statusCode)
            _inFlightRequests.value = _inFlightRequests.value - endpointId
            when (statusCode) {
                ConnectionsStatusCodes.STATUS_OK,
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.i(TAG, "Connection established with $endpointId ($statusStr)")
                    endpointRetryCounts.remove(endpointId)
                    val remoteMtId = endpointToMtId[endpointId] ?: endpointId
                    val peer = MeshPeer(
                        endpointId = endpointId,
                        deviceName = remoteMtId,
                        meshTalkId = remoteMtId
                    )
                    _connectedPeers.value = _connectedPeers.value + (remoteMtId to peer)
                    for (listener in onPeerConnectedListeners) {
                        try {
                            listener(remoteMtId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onPeerConnected listener", e)
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "onConnectionResult non-OK for $endpointId: $statusStr ($statusCode)")
                    _lastError.value = "Connection result: $statusStr"
                    val remoteMtId = endpointToMtId[endpointId] ?: endpointId
                    _connectedPeers.value = _connectedPeers.value - remoteMtId
                    scheduleConnectionRetry(endpointId, remoteMtId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "onDisconnected: endpointId=$endpointId")
            val remoteMtId = endpointToMtId.remove(endpointId) ?: endpointId
            mtIdToEndpoint.remove(remoteMtId)
            _connectedPeers.value = _connectedPeers.value - remoteMtId
            for (listener in onPeerDisconnectedListeners) {
                try {
                    listener(remoteMtId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onPeerDisconnected listener", e)
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTE_BUFFER -> {
                    val data = payload.asBytes()
                    if (data != null) {
                        _incomingPayloads.tryEmit(endpointId to data)
                    }
                }
                Payload.Type.FILE -> {
                    Log.w(TAG, "File payload received – not handled")
                }
                else -> Log.w(TAG, "Unsupported payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.i(TAG, "Payload ${update.payloadId} transfer SUCCESS to $endpointId")
                    val msgId = payloadIdToMessageId.remove(update.payloadId)
                    if (msgId != null) {
                        onPayloadSuccessCallback?.invoke(msgId)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.w(TAG, "Payload ${update.payloadId} transfer FAILURE to $endpointId")
                    val msgId = payloadIdToMessageId.remove(update.payloadId)
                    if (msgId != null) {
                        onPayloadFailureCallback?.invoke(msgId)
                    }
                }
                PayloadTransferUpdate.Status.IN_PROGRESS -> {}
                PayloadTransferUpdate.Status.CANCELED -> {
                    payloadIdToMessageId.remove(update.payloadId)
                }
            }
        }
    }

    fun startAdvertising(meshId: String, displayName: String = "") {
        isDesiredRunning = true
        localMeshId = meshId
        if (displayName.isNotBlank()) localDisplayName = displayName
        val localNickname = if (displayName.isNotBlank()) EndpointNameHelper.format(meshId, displayName) else meshId
        localAdvertisedId = localNickname

        if (_isAdvertising.value) {
            Log.d(TAG, "Already advertising, skipping redundant call")
            return
        }

        advertiseRetryJob?.cancel()

        val options = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        // Correct order: AdvertisingOptions **before** ConnectionLifecycleCallback
        connectionsClient.startAdvertising(
            localNickname,
            SERVICE_ID,
            options,
            connectionLifecycleCallback
        )

        _isAdvertising.value = true
        Log.i(TAG, "startAdvertising success for endpointName=$localNickname")

        advertiseRetryJob = managerScope.launch {
            val delayMs = Random.nextLong(6000L, 12001L)
            Log.d(TAG, "Advertising retry scheduled in ${delayMs}ms")
            delay(delayMs)
            if (isDesiredRunning && !_isAdvertising.value) {
                Log.i(TAG, "Retrying startAdvertising for $localAdvertisedId")
                startAdvertising(localAdvertisedId)
            }
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        isDesiredRunning = true
        if (_isDiscovering.value) {
            Log.d(TAG, "Already discovering, skipping redundant call")
            return
        }
        discoveryRetryJob?.cancel()
        val options = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )
        _isDiscovering.value = true
        Log.i(TAG, "startDiscovery success")
        discoveryRetryJob = managerScope.launch {
            val delayMs = Random.nextLong(6000L, 12001L)
            Log.d(TAG, "Discovery retry scheduled in ${delayMs}ms")
            delay(delayMs)
            if (isDesiredRunning && !_isDiscovering.value) {
                Log.i(TAG, "Retrying startDiscovery")
                startDiscovery()
            }
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
    }

    fun stopAll() {
        isDesiredRunning = false
        stopAdvertising()
        stopDiscovery()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val rawName = info.endpointName
            Log.i(TAG, "onEndpointFound: endpointId=$endpointId, rawName=$rawName")
            val parsed = EndpointNameHelper.parse(rawName)
            if (parsed == null) {
                Log.w(TAG, "Ignoring non-MeshTalk endpoint: $rawName")
                return
            }
            val remoteMtId = parsed.meshId
            val remoteDisplayName = parsed.displayName
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to DiscoveredPeerInfo(endpointId, rawName, remoteMtId, remoteDisplayName))
            endpointToMtId[endpointId] = remoteMtId
            mtIdToEndpoint[remoteMtId] = endpointId
            if (_connectedPeers.value.containsKey(remoteMtId)) {
                Log.d(TAG, "Already connected to MT ID $remoteMtId. Skipping requestConnection.")
                return
            }
            if (_inFlightRequests.value.contains(endpointId)) {
                Log.d(TAG, "Connection already in flight to $endpointId. Skipping.")
                return
            }
            if (shouldInitiateConnection(localMeshId, remoteMtId)) {
                Log.i(TAG, "Tie‑breaker: local mesh ID ($localMeshId) < remote ($remoteMtId). Requesting connection.")
                initiateConnection(endpointId, remoteMtId)
            } else {
                Log.i(TAG, "Tie‑breaker: local mesh ID ($localMeshId) >= remote ($remoteMtId). Waiting for incoming request.")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "onEndpointLost: endpointId=$endpointId")
            _discoveredEndpoints.value = _discoveredEndpoints.value - endpointId
            _inFlightRequests.value = _inFlightRequests.value - endpointId
            endpointRetryCounts.remove(endpointId)
            val remoteMtId = endpointToMtId.remove(endpointId)
            if (remoteMtId != null) {
                mtIdToEndpoint.remove(remoteMtId)
            }
        }
    }

    private fun initiateConnection(endpointId: String, remoteMtId: String) {
        if (!shouldInitiateConnection(localMeshId, remoteMtId)) {
            Log.d(TAG, "Skipping connection initiation to $endpointId ($remoteMtId) – tie‑breaker failed.")
            return
        }
        Log.d(TAG, "Initiating connection to $endpointId ($remoteMtId)")
        connectionsClient.requestConnection(endpointId, SERVICE_ID, connectionLifecycleCallback)
            .addOnSuccessListener {
                Log.i(TAG, "requestConnection success to $endpointId")
            }
            .addOnFailureListener { e ->
                val statusStr = getExceptionStatusString(e)
                Log.e(TAG, "requestConnection failed for $endpointId: $statusStr", e)
            }
        _inFlightRequests.value = _inFlightRequests.value + endpointId
    }

    private fun scheduleConnectionRetry(endpointId: String, remoteMtId: String) {
        val attempts = endpointRetryCounts.getOrDefault(endpointId, 0) + 1
        endpointRetryCounts[endpointId] = attempts
        val delayMs = calculateBackoff(attempts)
        Log.i(TAG, "Scheduling connection retry to $endpointId ($remoteMtId) in ${delayMs}ms")
        managerScope.launch {
            delay(delayMs)
            if (shouldInitiateConnection(localMeshId, remoteMtId)) {
                Log.i(TAG, "Retrying requestConnection to $endpointId ($remoteMtId)")
                initiateConnection(endpointId, remoteMtId)
            }
        }
    }

    fun sendPayload(targetEndpointId: String, data: ByteArray, messageId: String? = null) {
        val payload = Payload.fromBytes(data)
        connectionsClient.sendPayload(targetEndpointId, payload)
            .addOnSuccessListener {
                val msgId = messageId ?: payload.id.toString()
                payloadIdToMessageId[payload.id] = msgId
                Log.d(TAG, "sendPayload submitted to $targetEndpointId for payload ${payload.id}")
            }
            .addOnFailureListener { e ->
                val statusStr = getExceptionStatusString(e)
                Log.e(TAG, "sendPayload failed to $targetEndpointId: $statusStr", e)
            }
    }

    fun broadcastPayload(data: ByteArray, excludeEndpointId: String? = null) {
        val packet = Payload.fromBytes(data)
        val targets = _connectedPeers.value.values
            .map { it.endpointId }
            .filter { it != excludeEndpointId }
        if (targets.isNotEmpty()) {
            connectionsClient.sendPayload(targets, packet)
        }
    }
}

