package com.girlspace.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GirlSpaceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // TODO: push this token to Firestore / backend if you need device-level notifications
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data

        // Prefer data payload, then notification payload, then fallback
        val title: String = data["title"]
            ?: remoteMessage.notification?.title
            ?: "New message"

        val body: String = data["body"]
            ?: data["message"]
            ?: remoteMessage.notification?.body
            ?: "You have a new message"

        try {
            // NOTE:
            // We are not calling createNotificationChannel() here because the
            // method name/signature may be different in ChatNotificationHelper.
            // If needed, we can move channel creation inside that helper.

            // Show the notification
            // Using positional arguments so it matches any (Context, String, String) signature.
            ChatNotificationHelper.showNotification(
                applicationContext,
                title,
                body
            )

            // Play incoming sound
            ChatNotificationHelper.playIncomingSound(applicationContext)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show FCM notification", e)
        }
    }

    companion object {
        private const val TAG = "GirlSpaceMessagingSvc"
    }
}
