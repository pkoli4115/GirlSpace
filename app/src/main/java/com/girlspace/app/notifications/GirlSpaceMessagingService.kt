package com.girlspace.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.girlspace.app.data.notifications.FcmTokenCache
import com.girlspace.app.data.notifications.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GirlSpaceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid.isNullOrBlank()) {
                    FcmTokenCache.save(applicationContext, token)
                    Log.d(TAG, "Cached token (user not logged-in)")
                    return@launch
                }

                val repo = NotificationRepository()
                repo.ensureDefaultPrefs(uid)
                repo.saveToken(uid, token)
                Log.d(TAG, "Saved token to Firestore users/$uid/fcmTokens/$token")
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving token: ${e.message}", e)
                FcmTokenCache.save(applicationContext, token)
            }
        }
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        ensureChatChannel()

        val data = remoteMessage.data

        val title: String = data["title"]
            ?: remoteMessage.notification?.title
            ?: "New message"

        val body: String = data["body"]
            ?: data["message"]
            ?: remoteMessage.notification?.body
            ?: "You have a new message"

        val threadId: String? =
            data["threadId"]
                ?: data["entityId"]
                ?: data["chatThreadId"]
                ?: data["conversationId"]

        try {
            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                ChatNotificationHelper.showNotification(
                    applicationContext,
                    title,
                    body,
                    threadId = threadId
                )
                ChatNotificationHelper.playIncomingSound(applicationContext)
            } else {
                Log.w(TAG, "Notifications disabled by user/device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show FCM notification", e)
        }
    }


    private fun ensureChatChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val channelId = "chat_messages"
        val existing = nm.getNotificationChannel(channelId)
        if (existing != null) return

        val channel = NotificationChannel(
            channelId,
            "Chat messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new chat messages"
        }

        nm.createNotificationChannel(channel)
        Log.d(TAG, "Created notification channel: $channelId")
    }

    companion object {
        private const val TAG = "GirlSpaceMessagingSvc"
    }
}
