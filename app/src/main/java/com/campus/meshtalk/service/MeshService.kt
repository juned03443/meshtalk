package com.campus.meshtalk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.campus.meshtalk.MainActivity
import com.campus.meshtalk.MeshTalkApp
import com.campus.meshtalk.R
import com.campus.meshtalk.data.local.entity.MessageEntity
import com.campus.meshtalk.util.NotificationHelper
import com.campus.meshtalk.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeshService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        NotificationHelper.createNotificationChannels(this)
        startMeshForeground()
        observeMeshPeers()
        observeIncomingMessagesForNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MeshTalkApp
        if (intent?.action == ACTION_STOP_MESH) {
            stopMesh()
            stopSelf()
            return START_NOT_STICKY
        }

        if (PermissionHelper.hasNearbyPermissions(this)) {
            val advertiseId = if (app.meshTalkId.isNotBlank()) app.meshTalkId else app.nickname
            app.nearbyManager.setLocalIdentity(advertiseId, app.nickname)
            app.nearbyManager.startAdvertising(advertiseId, app.nickname)
            app.nearbyManager.startDiscovery()
        }

        return START_STICKY
    }

    private fun startMeshForeground() {
        val notification = buildForegroundNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeMeshPeers() {
        val app = application as MeshTalkApp
        serviceScope.launch {
            app.nearbyManager.connectedPeers.collectLatest { peers ->
                _connectedPeersCount.value = peers.size
                updateForegroundNotification(peers.size)
            }
        }
    }

    private fun observeIncomingMessagesForNotifications() {
        val app = application as MeshTalkApp
        serviceScope.launch {
            app.meshRouter.incomingMessages.collect { msg: MessageEntity ->
                // Suppress notification if user is currently looking at this active conversation
                if (activeChatPeerId != msg.peerId) {
                    NotificationHelper.showMessageNotification(
                        context = this@MeshService,
                        senderPeerId = msg.peerId,
                        senderName = msg.senderName,
                        messageText = msg.content
                    )
                }
            }
        }
    }

    private fun buildForegroundNotification(peerCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (peerCount > 0) {
            "Connected to $peerCount nearby mesh devices"
        } else {
            "Searching for nearby mesh devices..."
        }

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MESH_STATUS)
            .setContentTitle("MeshTalk Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateForegroundNotification(peerCount: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildForegroundNotification(peerCount))
    }

    private fun stopMesh() {
        val app = application as MeshTalkApp
        app.nearbyManager.stopAll()
        _isServiceRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMesh()
        serviceScope.cancel()
        _isServiceRunning.value = false
    }

    companion object {
        const val NOTIFICATION_ID = 9001
        const val ACTION_START_MESH = "com.campus.meshtalk.action.START_MESH"
        const val ACTION_STOP_MESH = "com.campus.meshtalk.action.STOP_MESH"

        @Volatile
        var activeChatPeerId: String? = null

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _connectedPeersCount = MutableStateFlow(0)
        val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java).apply {
                action = ACTION_START_MESH
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshService::class.java).apply {
                action = ACTION_STOP_MESH
            }
            context.startService(intent)
        }
    }
}
