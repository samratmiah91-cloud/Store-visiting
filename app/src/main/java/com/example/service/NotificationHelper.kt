package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

class NotificationHelper(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = context.getString(R.string.notification_channel_id)
            val name = context.getString(R.string.notification_channel_name)
            val desc = context.getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showSystemNotification(title: String, body: String, notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = context.getString(R.string.notification_channel_id)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
