package com.girlspace.app.data.chat

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // IMPORTANT: match your Firestore collection names
    private val threadsRef = firestore.collection("chatThreads")     // <- SAME as your existing
    private val messagesRef = firestore.collection("chat_messages")  // <- SAME as your existing

    private fun currentUid(): String = auth.currentUser?.uid ?: ""
    private fun currentName(): String = auth.currentUser?.displayName ?: "GirlSpace user"

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

                val list = snap?.documents?.map { doc ->
                    ChatThread(
                        id = doc.id,
                        userA = doc.getString("userA") ?: "",
                        userB = doc.getString("userB") ?: "",
                        userAName = doc.getString("userAName") ?: "",
                        userBName = doc.getString("userBName") ?: "",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastTimestamp = doc.getLong("lastTimestamp") ?: 0L,
                        unreadCount = (doc.get("unread_$uid") as? Long)?.toInt() ?: 0
                    )
                } ?: emptyList()

                // Newest chats first
                onUpdate(list.sortedByDescending { it.lastTimestamp })
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
            "lastTimestamp" to now,
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
                    // Sort on client to keep them in chronological order
                    ?.sortedBy { it.createdAt.seconds }
                    ?: emptyList()

                onUpdate(list)
            }
    }

    /**
     * Send a message in a thread.
     *
     * - text: regular text content
     * - mediaUrl/mediaType: for images, videos, audio, files
     * - replyTo: messageId being replied to (for reply UI)
     * - audioDuration: for voice messages (ms)
     * - mediaThumbnail: preview image URL for video (optional)
     */
    suspend fun sendMessage(
        threadId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        replyTo: String? = null,
        audioDuration: Long? = null,
        mediaThumbnail: String? = null
    ) {
        val uid = currentUid()
        val name = currentName()
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

        // Firestore ignores nulls if we filter them out
        val cleaned = msgData.filterValues { it != null }

        msgDoc.set(cleaned).await()

        // Update thread summary
        threadsRef.document(threadId).update(
            mapOf(
                "lastMessage" to when {
                    text.isNotBlank() -> text
                    mediaType != null -> "[${mediaType}]"
                    else -> "[Message]"
                },
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
                updated.remove(userId) // tap same emoji → remove
            } else {
                updated[userId] = emoji
            }

            tx.update(msgRef, "reactions", updated as Map<String, Any>)
        }.await()
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatThread(): ChatThread {
        return ChatThread(
            id = id,
            userA = getString("userA") ?: "",
            userB = getString("userB") ?: "",
            userAName = getString("userAName") ?: "",
            userBName = getString("userBName") ?: "",
            lastMessage = getString("lastMessage") ?: "",
            lastTimestamp = getLong("lastTimestamp") ?: 0L,
            unreadCount = 0 // per-user unread can be computed client-side later
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatMessage(): ChatMessage? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val reactionsMap = get("reactions") as? Map<String, String> ?: emptyMap()

            // --- Soft-delete normalization (critical for media delete) ---
            val rawText = getString("text") ?: ""
            val deleted = rawText == "This message was deleted"

// If deleted → null out media fields client-side, even if old DB had stale values
            val finalMediaUrl = if (deleted) null else getString("mediaUrl")
            val finalMediaType = if (deleted) null else getString("mediaType")

            ChatMessage(
                id = getString("id") ?: "",
                threadId = getString("threadId") ?: "",
                senderId = getString("senderId") ?: "",
                senderName = getString("senderName") ?: "",
                text = rawText,
                mediaUrl = finalMediaUrl,
                mediaType = finalMediaType,
                mediaThumbnail = if (deleted) null else getString("mediaThumbnail"),
                audioDuration = if (deleted) null else getLong("audioDuration"),
                replyTo = getString("replyTo"),
                createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
                readBy = (get("readBy") as? List<String>) ?: emptyList(),
                reactions = reactionsMap
            )

        } catch (e: Exception) {
            Log.e("ChatRepository", "toChatMessage failed", e)
            null
        }
    }
}
