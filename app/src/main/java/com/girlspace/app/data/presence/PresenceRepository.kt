package com.girlspace.app.data.presence

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserPresence(
    val lastActiveAt: Long = 0L
) {
    /**
     * Decide online/offline on client by comparing with "now".
     * Example usage:
     *   val isOnline = presence.isOnline(System.currentTimeMillis(), 90_000L)
     */
    fun isOnline(nowMillis: Long, thresholdMillis: Long = 90_000L): Boolean {
        if (lastActiveAt == 0L) return false
        return nowMillis - lastActiveAt <= thresholdMillis
    }
}

/**
 * Handles:
 *  - user presence (lastActiveAt)
 *  - typing indicators per threadId (DMs or groups)
 */
@ViewModelScoped
class PresenceRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    // ---- helpers ----

    private fun currentUid(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in" }
        return uid
    }

    // ---- presence ----

    /**
     * Mark current user as active "now".
     * Call this when:
     *  - app opens
     *  - user opens chat screen
     *  - user sends a message
     */
    suspend fun markUserActive() {
        val uid = currentUid()
        val now = System.currentTimeMillis()

        db.collection("user_status")
            .document(uid)
            .set(
                mapOf(
                    "lastActiveAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    /**
     * Observe another user's presence.
     * You decide online/offline in UI using UserPresence.isOnline().
     */
    fun observeUserPresence(userId: String): Flow<UserPresence> = callbackFlow {
        val reg = db.collection("user_status")
            .document(userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(UserPresence()).isSuccess
                    return@addSnapshotListener
                }

                val lastActiveAt = snap?.getLong("lastActiveAt") ?: 0L
                trySend(UserPresence(lastActiveAt = lastActiveAt)).isSuccess
            }

        awaitClose { reg.remove() }
    }

    // ---- typing indicators ----

    /**
     * Update typing state for the current user in a given thread.
     *
     * @param threadId your existing chat threadId or groupId
     * @param isTyping true when user has started typing, false when stopped
     */
    suspend fun setTyping(threadId: String, isTyping: Boolean) {
        val uid = currentUid()
        val docRef = db.collection("typing_status")
            .document(threadId)
            .collection("users")
            .document(uid)

        if (isTyping) {
            val now = System.currentTimeMillis()
            docRef.set(
                mapOf(
                    "isTyping" to true,
                    "updatedAt" to now
                )
            ).await()
        } else {
            // You can either delete or just mark isTyping=false.
            // Deleting keeps collection small.
            docRef.delete().await()
        }
    }

    /**
     * Observe which users are currently typing in threadId.
     * Returns a set of userIds (excluding the current user).
     */
    fun observeTypingUsers(threadId: String): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid

        val reg = db.collection("typing_status")
            .document(threadId)
            .collection("users")
            .addSnapshotListener { snapshot: QuerySnapshot?, error ->
                if (error != null) {
                    trySend(emptySet()).isSuccess
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val cutoff = now - 8_000L // 8 seconds window

                val typingIds = snapshot?.documents
                    ?.filter { doc ->
                        val ts = doc.getLong("updatedAt") ?: 0L
                        val typingFlag = doc.getBoolean("isTyping") ?: false
                        typingFlag && ts >= cutoff
                    }
                    ?.map { it.id }
                    ?.filter { it != null && it != uid }
                    ?.toSet()
                    ?: emptySet()

                trySend(typingIds).isSuccess
            }

        awaitClose { reg.remove() }
    }
}
