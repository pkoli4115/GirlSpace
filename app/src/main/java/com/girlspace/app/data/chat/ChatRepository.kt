package com.girlspace.app.data.chat

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.Date

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // IMPORTANT: match your Firestore collection names
    private val threadsRef = firestore.collection("chatThreads")     // <- SAME as your existing
    private val messagesRef = firestore.collection("chat_messages")  // <- SAME as your existing

    private fun currentUid(): String = auth.currentUser?.uid ?: ""

    private suspend fun currentName(): String {
        val uid = currentUid()
        val authName = auth.currentUser?.displayName
        if (!authName.isNullOrBlank()) return authName

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.getString("displayName")
                ?: doc.getString("name")
                ?: doc.getString("fullName")
                ?: doc.getString("username")
                ?: doc.getString("userName")
                ?: "Someone"
        } catch (_: Exception) {
            "Someone"
        }
    }

    // ------------------------------------------
    // 🔹 Safe timestamp reader (Long OR Timestamp)
    // ------------------------------------------
    private fun Any?.toMillisSafe(): Long {
        return when (this) {
            is Long -> this
            is Int -> this.toLong()
            is Double -> this.toLong()
            is Float -> this.toLong()
            is Timestamp -> this.toDate().time
            is Date -> this.time
            else -> 0L
        }
    }

    private fun DocumentSnapshot.getMillis(field: String): Long {
        val v = get(field)
        return when (v) {
            is Long -> v
            is Timestamp -> v.toDate().time
            is Date -> v.time
            else -> 0L
        }
    }

    // -------------------------------------------------------------------------
    // THREADS
    // -------------------------------------------------------------------------
    fun observeThreads(onUpdate: (List<ChatThread>) -> Unit): ListenerRegistration {
        val uid = currentUid()
        if (uid.isEmpty()) {
            onUpdate(emptyList())
            // Return a dummy listener if not logged in
            return firestore.collection("dummy").addSnapshotListener { _, _ -> }
        }

        return threadsRef
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("ChatRepository", "observeThreads error", e)
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val threadsWithPin = snap?.documents?.mapNotNull { doc ->

                    // 🚫 Skip deleted threads for this user
                    val deletedFor =
                        (doc.get("deletedFor") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (deletedFor.contains(uid)) return@mapNotNull null

                    val lastTsMillis =
                        (doc.get("lastTimestamp") ?: doc.get("lastTimestampMillis")).toMillisSafe()

                    // 📌 Read per-user pinned time
                    val pinnedAtMillis =
                        (doc.get("pinnedAt_$uid") ?: 0L).toMillisSafe()

                    ChatThread(
                        id = doc.id,
                        userA = doc.getString("userA") ?: "",
                        userB = doc.getString("userB") ?: "",
                        userAName = doc.getString("userAName") ?: "",
                        userBName = doc.getString("userBName") ?: "",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastTimestamp = lastTsMillis,
                        unreadCount = (doc.get("unread_$uid") as? Long)?.toInt() ?: 0
                    ) to pinnedAtMillis

                } ?: emptyList()

// ✅ Sort: pinned first → newest pinned → newest normal
                val sorted = threadsWithPin
                    .sortedWith(
                        compareByDescending<Pair<ChatThread, Long>> { it.second > 0L }
                            .thenByDescending { it.second }
                            .thenByDescending { it.first.lastTimestamp }
                    )
                    .map { it.first }

                onUpdate(sorted)
            }
    }

    /**
     * ✅ Mark a thread as read for current user (WhatsApp style):
     * sets chatThreads/{threadId}.unread_{uid} = 0
     */
    suspend fun markThreadRead(threadId: String) {
        val uid = currentUid()
        if (uid.isBlank()) return

        try {
            threadsRef.document(threadId)
                .update("unread_$uid", 0L)
                .await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "markThreadRead failed", e)
        }
    }

    /**
     * 📌 Pin/unpin for current user only.
     * Stores a per-user field: pinnedAt_<uid> = millis (or 0).
     */
    suspend fun setThreadPinned(threadId: String, pinned: Boolean) {
        val uid = currentUid()
        if (uid.isBlank()) return

        val field = "pinnedAt_$uid"
        val value = if (pinned) System.currentTimeMillis() else 0L

        try {
            threadsRef.document(threadId)
                .update(field, value)
                .await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "setThreadPinned failed", e)
        }
    }

    /**
     * 🗑 Delete thread for current user only.
     * Adds uid to chatThreads/{threadId}.deletedFor array
     */
    suspend fun deleteThreadForMe(threadId: String) {
        val uid = currentUid()
        if (uid.isBlank()) return

        try {
            firestore.runTransaction { tx ->
                val ref = threadsRef.document(threadId)
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction

                val current = (snap.get("deletedFor") as? List<String>) ?: emptyList()
                if (current.contains(uid)) return@runTransaction

                tx.update(ref, "deletedFor", current + uid)
                tx.update(ref, "unread_$uid", 0L) // optional: clear badge
            }.await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "deleteThreadForMe failed", e)
        }
    }

    /**
     * Start or get an existing thread between current user and user with [email].
     * Requires that /users/{uid}.email == [email].
     */
    suspend fun startOrGetThreadByEmail(email: String): ChatThread {
        val myId = currentUid()
        val myName = currentName()

        if (myId.isEmpty()) throw IllegalStateException("Not logged in")

        // 1) Find target user by email
        val userSnap = firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .await()

        if (userSnap.isEmpty) {
            throw IllegalStateException("No user found with email $email")
        }

        val otherDoc = userSnap.documents.first()
        val otherId = otherDoc.id
        val otherName = otherDoc.getString("name")
            ?: otherDoc.getString("email")
            ?: "GirlSpace user"

        // 2) Check if thread already exists (A-B or B-A)
        val forward = threadsRef
            .whereEqualTo("userA", myId)
            .whereEqualTo("userB", otherId)
            .get()
            .await()

        if (!forward.isEmpty) {
            val doc = forward.documents.first()
            return doc.toChatThread()
        }

        val reverse = threadsRef
            .whereEqualTo("userA", otherId)
            .whereEqualTo("userB", myId)
            .get()
            .await()

        if (!reverse.isEmpty) {
            val doc = reverse.documents.first()
            return doc.toChatThread()
        }

        // 3) Create new thread
        val now = System.currentTimeMillis()
        val newDoc = threadsRef.document()

        val data = mapOf(
            "userA" to myId,
            "userB" to otherId,
            "userAName" to myName,
            "userBName" to otherName,
            "participants" to listOf(myId, otherId),
            "lastMessage" to "",
            "lastTimestamp" to now, // keep Long for fast sort (your UI supports Long)
            "unread_$myId" to 0L,
            "unread_$otherId" to 0L
        )

        newDoc.set(data).await()

        return ChatThread(
            id = newDoc.id,
            userA = myId,
            userB = otherId,
            userAName = myName,
            userBName = otherName,
            lastMessage = "",
            lastTimestamp = now,
            unreadCount = 0
        )
    }

    // -------------------------------------------------------------------------
    // MESSAGES
    // -------------------------------------------------------------------------

    fun observeMessages(
        threadId: String,
        onUpdate: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {

        return messagesRef
            .whereEqualTo("threadId", threadId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    // Log the error but DO NOT clear UI to empty
                    Log.e("ChatRepository", "observeMessages error", e)
                    return@addSnapshotListener
                }

                val list = snap
                    ?.documents
                    ?.mapNotNull { it.toChatMessage() }
                    // ✅ sort by millis (works even if createdAt was Long/Date/Timestamp)
                    ?.sortedBy { it.createdAt.toDate().time }
                    ?: emptyList()

                onUpdate(list)
            }
    }

    /**
     * Send a message in a thread.
     */
    suspend fun sendMessage(
        threadId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        replyTo: String? = null,
        audioDuration: Long? = null,
        mediaThumbnail: String? = null,
        extra: Map<String, Any>? = null
    ) {
        val uid = currentUid()
        val name = currentName() // suspend-safe + real name
        if (uid.isEmpty()) throw IllegalStateException("Not logged in")

        val msgDoc = messagesRef.document()
        val now = Timestamp.now()

        val msgData = mutableMapOf<String, Any?>(
            "id" to msgDoc.id,
            "threadId" to threadId,
            "senderId" to uid,
            "senderName" to name,
            "text" to text,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to now,
            "readBy" to listOf(uid),
            "reactions" to emptyMap<String, String>(),
            "replyTo" to replyTo,
            "audioDuration" to audioDuration,
            "mediaThumbnail" to mediaThumbnail
        )

        if (extra != null) {
            msgData["extra"] = extra
        }

        val cleaned = msgData.filterValues { it != null }
        msgDoc.set(cleaned).await()

        val preview: String = when (mediaType) {
            "image" -> "[Image]"
            "video" -> "[Video]"
            "audio" -> "[Voice]"
            "location" -> extra?.get("address")?.toString() ?: "[Location]"
            "live_location" -> "Live Location"
            "contact" -> extra?.get("name")?.toString() ?: "[Contact]"
            "file" -> if (text.isNotBlank()) text else "[File]"
            else -> if (text.isNotBlank()) text else "[Message]"
        }

        // Keep your original long timestamp update (Cloud Function will also set serverTimestamp)
        threadsRef.document(threadId).update(
            mapOf(
                "lastMessage" to preview,
                "lastTimestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    // -------------------------------------------------------------------------
    // REACTIONS
    // -------------------------------------------------------------------------

    suspend fun addReaction(
        messageId: String,
        userId: String,
        emoji: String
    ) {
        val msgRef = messagesRef.document(messageId)

        firestore.runTransaction { tx ->
            val snap = tx.get(msgRef)
            if (!snap.exists()) return@runTransaction

            @Suppress("UNCHECKED_CAST")
            val current = snap.get("reactions") as? Map<String, String> ?: emptyMap()
            val updated = current.toMutableMap()

            if (updated[userId] == emoji) {
                updated.remove(userId)
            } else {
                updated[userId] = emoji
            }

            tx.update(msgRef, "reactions", updated as Map<String, Any>)
        }.await()
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private fun DocumentSnapshot.toChatThread(): ChatThread {
        return ChatThread(
            id = id,
            userA = getString("userA") ?: "",
            userB = getString("userB") ?: "",
            userAName = getString("userAName") ?: "",
            userBName = getString("userBName") ?: "",
            lastMessage = getString("lastMessage") ?: "",
            lastTimestamp = (get("lastTimestamp") ?: get("lastTimestampMillis")).toMillisSafe(),
            unreadCount = 0
        )
    }

    private fun DocumentSnapshot.toChatMessage(): ChatMessage? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val reactionsMap = get("reactions") as? Map<String, String> ?: emptyMap()

            val rawText = getString("text") ?: ""
            val legacyDeleted = rawText == "This message was deleted"

            val deletedForAllFlag = getBoolean("deletedForAll") ?: legacyDeleted
            val deletedForList = (get("deletedFor") as? List<String>) ?: emptyList()

            val isDeletedCompletely = deletedForAllFlag

            val finalMediaUrl = if (isDeletedCompletely) null else getString("mediaUrl")
            val finalMediaType = if (isDeletedCompletely) null else getString("mediaType")
            val finalMediaThumb = if (isDeletedCompletely) null else getString("mediaThumbnail")
            val finalAudioDuration = if (isDeletedCompletely) null else getLong("audioDuration")

            @Suppress("UNCHECKED_CAST")
            val extraPayload: Map<String, Any>? =
                if (isDeletedCompletely) null else get("extra") as? Map<String, Any>

            // ✅ robust createdAt parse: Timestamp OR Long OR Date
            val createdAtTs: Timestamp = getTimestamp("createdAt")
                ?: run {
                    val ms = get("createdAt").toMillisSafe()
                    if (ms > 0L) Timestamp(Date(ms)) else Timestamp.now()
                }

            ChatMessage(
                id = getString("id") ?: id,
                threadId = getString("threadId") ?: "",
                senderId = getString("senderId") ?: "",
                senderName = getString("senderName") ?: "",
                text = rawText,
                mediaUrl = finalMediaUrl,
                mediaType = finalMediaType,
                mediaThumbnail = finalMediaThumb,
                audioDuration = finalAudioDuration,
                replyTo = getString("replyTo"),
                createdAt = createdAtTs,
                readBy = (get("readBy") as? List<String>) ?: emptyList(),
                reactions = reactionsMap,
                extra = extraPayload,
                deletedForAll = deletedForAllFlag,
                deletedFor = deletedForList
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "toChatMessage failed", e)
            null
        }
    }
}
