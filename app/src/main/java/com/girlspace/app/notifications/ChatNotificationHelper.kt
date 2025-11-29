package com.girlspace.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.girlspace.app.MainActivity
import com.girlspace.app.R

/**
 * v1.0 – Chat notification helper for DM + Group chat
 *
 * Shows a heads-up style notification with sound whenever we call
 * [showNewMessage].
 *
 * NOTE:
 * - Make sure you request POST_NOTIFICATIONS permission on Android 13+
 * - Uses app icon as small icon (R.mipmap.ic_launcher)
 */
object ChatNotificationHelper {

    private const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "Chat messages"
    private const val CHANNEL_DESC = "Notifications for new chat messages"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Show a new-message notification with default notification sound.
     */
    fun showNewMessage(
        context: Context,
        title: String,
        message: String
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            flags
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // fallback to app icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .build()

        NotificationManagerCompat.from(context)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
