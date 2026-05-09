package com.example.sevasetu.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object NotificationSupport {
    const val ISSUE_CHANNEL_ID = "issue_updates"
    const val REPORT_CHANNEL_ID = "monthly_reports"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val issueChannel = NotificationChannel(
            ISSUE_CHANNEL_ID,
            "Issue Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Status and progress updates on your reported issues"
        }

        val reportChannel = NotificationChannel(
            REPORT_CHANNEL_ID,
            "Monthly Reports",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Monthly report summaries"
        }

        manager.createNotificationChannel(issueChannel)
        manager.createNotificationChannel(reportChannel)
    }

    fun notificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
