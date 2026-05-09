package com.example.sevasetu.notifications

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sevasetu.R
import com.example.sevasetu.ui.screen.Alerts.AlertsScreen
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SevaSetuFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "SevaSetuFcmService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: ${token.take(10)}...")
        FcmTokenRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        NotificationSupport.createChannels(applicationContext)

        val type = message.data["type"]
        val channelId = if (type == "MONTHLY_REPORT") {
            NotificationSupport.REPORT_CHANNEL_ID
        } else {
            NotificationSupport.ISSUE_CHANNEL_ID
        }

        val title = message.notification?.title ?: message.data["title"] ?: "SevaSetu"
        val body = message.notification?.body ?: message.data["body"] ?: "You have a new update"

        Log.d(TAG, "Notification - Title: $title, Body: $body, Type: $type")

        val intent = Intent(this, AlertsScreen::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
            putExtra("issue_id", message.data["issueId"])
            putExtra("report_url", message.data["reportUrl"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            Log.d(TAG, "Notification displayed successfully")
        } catch (error: SecurityException) {
            Log.e(TAG, "Notification permission missing or not granted", error)
        } catch (_: Exception) {
            Log.e(TAG, "Error displaying notification")
        }
    }
}
