package com.girlspace.app.data.notifications

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun prefsDoc(uid: String) =
        firestore.collection("users").document(uid)
            .collection("notificationPrefs").document("default")

    fun tokensCollection(uid: String) =
        firestore.collection("users").document(uid)
            .collection("fcmTokens")

    fun inboxCollection(uid: String) =
        firestore.collection("users").document(uid)
            .collection("notifications")

    suspend fun ensureDefaultPrefs(uid: String) {
        val ref = prefsDoc(uid)
        val snap = ref.get().await()
        if (snap.exists()) return

        val defaults = hashMapOf(
            "chat" to NotificationLevel.ALL.name,
            "social" to NotificationLevel.IMPORTANT_ONLY.name,
            "inspiration" to NotificationLevel.OFF.name,
            "festivals" to NotificationLevel.OFF.name,
            "birthdays" to BirthdayNotifyMode.SELF_ONLY.name,
            "quietHoursEnabled" to false,
            "quietStart" to "22:00",
            "quietEnd" to "08:00",
            "timezone" to "Asia/Kolkata",
            "updatedAt" to Timestamp.now()
        )
        ref.set(defaults).await()
    }

    suspend fun saveToken(uid: String, token: String) {
        // token as docId => supports multiple devices cleanly
        val doc = tokensCollection(uid).document(token)

        val data = hashMapOf(
            "token" to token,
            "platform" to "android",
            "app" to "Togetherly",
            "lastSeenAt" to Timestamp.now(),
            // createdAt should be set once (merge preserves existing if already there)
            "createdAt" to Timestamp.now()
        )

        doc.set(data, SetOptions.merge()).await()
    }

    suspend fun touchToken(uid: String, token: String) {
        val doc = tokensCollection(uid).document(token)
        doc.set(mapOf("lastSeenAt" to Timestamp.now()), SetOptions.merge()).await()
    }

    suspend fun writeInboxItem(uid: String, item: NotificationInboxItem) {
        val docRef = if (item.id.isNotBlank()) {
            inboxCollection(uid).document(item.id)
        } else {
            inboxCollection(uid).document()
        }

        val data = hashMapOf(
            "id" to docRef.id,
            "category" to item.category.name,
            "importance" to item.importance.name,
            "type" to item.type,
            "title" to item.title,
            "body" to item.body,
            "deepLink" to item.deepLink,
            "entityId" to item.entityId,
            "read" to item.read,
            "createdAt" to (item.createdAt ?: Timestamp.now())
        )

        docRef.set(data).await()
    }
}
