package com.campus.meshtalk.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.campus.meshtalk.MainActivity
import com.campus.meshtalk.R
import java.util.concurrent.ConcurrentHashMap

object NotificationHelper {

    const val CHANNEL_MESSAGES = "meshtalk_messages_channel"
    const val CHANNEL_REQUESTS = "meshtalk_requests_channel"
    const val CHANNEL_MESH_STATUS = "meshtalk_mesh_status_channel"

    private val conversationCounts = ConcurrentHashMap<String, Int>()

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming offline mesh messages"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val requestsChannel = NotificationChannel(
                CHANNEL_REQUESTS,
                "Connection Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peer connection requests"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val statusChannel = NotificationChannel(
                CHANNEL_MESH_STATUS,
                "Mesh Network Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent indicator for active mesh background services"
                setShowBadge(false)
            }

            nm.createNotificationChannels(listOf(messagesChannel, requestsChannel, statusChannel))
        }
    }

    fun showMessageNotification(
        context: Context,
        senderPeerId: String,
        senderName: String,
        messageText: String
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val currentCount = (conversationCounts[senderPeerId] ?: 0) + 1
        conversationCounts[senderPeerId] = currentCount

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_PEER_ID", senderPeerId)
            putExtra("OPEN_PEER_NAME", senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            senderPeerId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Public lockscreen notification: generic text, hides private content
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MeshTalk")
            .setContentText("New mesh message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(if (currentCount > 1) "($currentCount messages) $messageText" else messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        val notificationId = 10000 + (senderPeerId.hashCode() and 0x7FFFFFFF % 10000)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS runtime permission revoked
        }
    }

    fun showConnectionRequestNotification(
        context: Context,
        requestId: String,
        fromNickname: String,
        fromMeshId: String
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_REQUESTS", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MeshTalk")
            .setContentText("New connection request")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Connection Request")
            .setContentText("$fromNickname ($fromMeshId) wants to connect")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        val notificationId = 20000 + (requestId.hashCode() and 0x7FFFFFFF % 10000)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS runtime permission revoked
        }
    }

    fun clearConversationCount(peerId: String) {
        conversationCounts.remove(peerId)
    }
}
