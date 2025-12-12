package com.girlspace.app.moderation

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Separate log entry for abusive content that was blocked locally.
 * Different from PendingContent used by ModerationManager.
 */
data class AbusiveLogEntry(
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val kind: ContentKind = ContentKind.CHAT_MESSAGE,
    val contextId: String? = null, // chatId, postId, etc
    val abusiveTerms: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

@Singleton
class ModerationService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    /**
     * Called when a message/post is abusive (Option B: soft warning shown).
     * We only log to Firestore; we DO NOT send the message.
     */
    suspend fun logAbusiveContent(
        text: String,
        kind: ContentKind,
        contextId: String? = null,
        abusiveTerms: List<String> = emptyList()
    ) {
        val userId = auth.currentUser?.uid ?: "anonymous"
        val id = UUID.randomUUID().toString()

        val entry = AbusiveLogEntry(
            id = id,
            userId = userId,
            text = text,
            kind = kind,
            contextId = contextId,
            abusiveTerms = abusiveTerms,
            createdAt = Timestamp.now()
        )

        firestore.collection("abusive_logs") // separate collection from pending_content
            .document(id)
            .set(entry)
            .await()
    }
}
