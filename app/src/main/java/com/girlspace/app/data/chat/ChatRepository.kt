package com.girlspace.app.data.chat
import com.girlspace.app.data.chat.*
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val threadsCollection get() = firestore.collection("chatThreads")

    /**
     * Listen for all 1:1 threads for the current user.
     */
    fun observeThreads(onUpdate: (List<ChatThread>) -> Unit): ListenerRegistration {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("User must be logged in to observe threads")

        return threadsCollection
            .whereArrayContains("userIds", currentUser.uid)
            .orderBy("lastTimestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "observeThreads failed", error)
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                val threads = docs.map { mapThreadDoc(it, currentUser.uid) }
                onUpdate(threads)
            }
    }

    /**
     * Start or get a 1:1 thread with a user identified by email.
     */
    suspend fun startOrGetThreadByEmail(otherEmail: String): ChatThread {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("User must be logged in to start a chat")

        // 1) Find the other user in /users by email
        val usersQuery = await(
            firestore.collection("users")
                .whereEqualTo("email", otherEmail)
                .limit(1)
                .get()
        )

        if (usersQuery.isEmpty) {
            throw IllegalStateException("No user found with that email")
        }

        val otherDoc = usersQuery.documents.first()
        val otherUid = otherDoc.id
        val otherName = otherDoc.getString("name") ?: otherEmail

        // 2) Compute a stable pair key so we can re-find the same thread
        val pairKey = listOf(currentUser.uid, otherUid).sorted().joinToString("_")

        // 3) Check if a thread already exists
        val existing = await(
            threadsCollection
                .whereEqualTo("userPairKey", pairKey)
                .limit(1)
                .get()
        )

        if (!existing.isEmpty) {
            return mapThreadDoc(existing.documents.first(), currentUser.uid)
        }

        // 4) Otherwise create a new thread
        val docRef = threadsCollection.document()
        val now = Timestamp.now()

        val data = hashMapOf(
            "id" to docRef.id,
            "userIds" to listOf(currentUser.uid, otherUid),
            "userEmails" to listOf(currentUser.email ?: "", otherEmail),
            "userNames" to listOf(currentUser.displayName ?: "", otherName),
            "userPairKey" to pairKey,
            "lastMessage" to "",
            "lastTimestamp" to now,
            "createdAt" to now,
            "unreadCounts" to mapOf(
                currentUser.uid to 0L,
                otherUid to 0L
            )
        )

        await(docRef.set(data))

        return ChatThread(
            id = docRef.id,
            otherUserId = otherUid,
            otherUserName = otherName,
            otherUserEmail = otherEmail,
            lastMessage = "",
            lastTimestamp = now,
            unreadCount = 0
        )
    }

    /**
     * Listen for messages in a given thread.
     */
    fun observeMessages(
        threadId: String,
        onUpdate: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        val messagesCollection = threadsCollection
            .document(threadId)
            .collection("messages")

        return messagesCollection
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "observeMessages failed", error)
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                val messages = docs.map { doc ->
                    ChatMessage(
                        id = doc.id,
                        threadId = threadId,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "GirlSpace user",
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getTimestamp("timestamp"),
                        readBy = (doc.get("readBy") as? List<String>).orEmpty()
                    )
                }
                onUpdate(messages)
            }
    }

    /**
     * Send a message in a given thread.
     */
    suspend fun sendMessage(threadId: String, text: String) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("User must be logged in to send messages")

        val messagesCollection = threadsCollection
            .document(threadId)
            .collection("messages")

        val now = Timestamp.now()

        // 1) Add message document
        val messageData = hashMapOf(
            "senderId" to currentUser.uid,
            "senderName" to (currentUser.displayName ?: "GirlSpace user"),
            "text" to text,
            "timestamp" to now,
            "readBy" to listOf(currentUser.uid)
        )

        await(messagesCollection.add(messageData))

        // 2) Update thread summary
        val threadRef = threadsCollection.document(threadId)
        val threadSnap = await(threadRef.get())
        val userIds = (threadSnap.get("userIds") as? List<String>).orEmpty()

        // Simple unread count logic: increment all except sender
        val unreadCounts = (threadSnap.get("unreadCounts") as? Map<String, Long>).orEmpty()
        val updatedUnread = unreadCounts.toMutableMap()
        userIds.forEach { uid ->
            updatedUnread[uid] = if (uid == currentUser.uid) 0L
            else (unreadCounts[uid] ?: 0L) + 1L
        }

        await(
            threadRef.update(
                mapOf(
                    "lastMessage" to text,
                    "lastTimestamp" to now,
                    "unreadCounts" to updatedUnread
                )
            )
        )
    }

    /* ---------------------------------------------------------
       Helpers
    --------------------------------------------------------- */

    private fun mapThreadDoc(doc: DocumentSnapshot, currentUid: String): ChatThread {
        val userIds = (doc.get("userIds") as? List<String>).orEmpty()
        val userNames = (doc.get("userNames") as? List<String>).orEmpty()
        val userEmails = (doc.get("userEmails") as? List<String>).orEmpty()
        val unreadCounts = (doc.get("unreadCounts") as? Map<String, Long>).orEmpty()

        val indexOfOther = userIds.indexOfFirst { it != currentUid }.takeIf { it >= 0 } ?: 0

        val otherUserId = userIds.getOrNull(indexOfOther) ?: ""
        val otherUserName = userNames.getOrNull(indexOfOther) ?: "GirlSpace user"
        val otherUserEmail = userEmails.getOrNull(indexOfOther) ?: ""

        return ChatThread(
            id = doc.getString("id") ?: doc.id,
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            otherUserEmail = otherUserEmail,
            lastMessage = doc.getString("lastMessage") ?: "",
            lastTimestamp = doc.getTimestamp("lastTimestamp"),
            unreadCount = (unreadCounts[currentUid] ?: 0L).toInt()
        )
    }

    private suspend fun <T> await(task: Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
}
