package com.girlspace.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import androidx.core.app.NotificationCompat
import com.girlspace.app.MainActivity
import com.girlspace.app.R

object ChatNotificationHelper {

    private const val CHANNEL_ID = "chat_messages"

    fun playIncomingSound(context: Context) {
        try {
            val player = MediaPlayer.create(context, R.raw.reaction_bee)
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (_: Exception) { }
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        threadId: String? = null
    ) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!threadId.isNullOrBlank()) {
                putExtra("open_chat_thread_id", threadId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (threadId ?: System.currentTimeMillis().toString()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
