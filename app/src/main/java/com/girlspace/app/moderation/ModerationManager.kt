package com.girlspace.app.moderation

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Pending content stored temporarily for Cloud Function processing.
 */
data class PendingContent(
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val kind: ContentKind = ContentKind.CHAT_MESSAGE,
    val contextId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending",
    val localVerdict: String = "allowed" // allowed, flagged, blocked
)

/**
 * Result returned back to the ViewModel.
 */
data class ModerationSubmissionResult(
    val success: Boolean,
    val blockedLocally: Boolean,
    val pendingId: String? = null,
    val message: String? = null
)

/**
 * Small internal result for local-only moderation.
 */
private data class LocalModerationResult(
    val allowed: Boolean,
    val matchedBadWords: List<String>
)

/**
 * High-level moderation coordinator.
 * Handles ONLY local moderation + sending content to pending_content.
 * Cloud moderation happens in Firebase Cloud Functions.
 */
class ModerationManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    private val pendingCollection = firestore.collection("pending_content")

    /**
     * Main moderation entry point.
     * 1) Runs fast on-device moderation.
     * 2) If allowed → writes to pending_content for Cloud moderation.
     */
    suspend fun submitTextForModeration(
        rawText: String,
        kind: ContentKind,
        contextId: String? = null
    ): ModerationSubmissionResult {

        val currentUser = auth.currentUser
            ?: return ModerationSubmissionResult(
                success = false,
                blockedLocally = true,
                message = "User not logged in."
            )

        // 🔹 1. LOCAL MODERATION (simple word scan)
        val localResult = runLocalModeration(rawText)

        if (!localResult.allowed) {
            val terms = if (localResult.matchedBadWords.isNotEmpty()) {
                " (" + localResult.matchedBadWords.joinToString(", ") + ")"
            } else {
                ""
            }
            return ModerationSubmissionResult(
                success = false,
                blockedLocally = true,
                message = "Message blocked by community guidelines$terms."
            )
        }

        // 🔹 2. If allowed locally → push to pending_content for Cloud Function
        val id = UUID.randomUUID().toString()

        val pending = PendingContent(
            id = id,
            userId = currentUser.uid,
            text = rawText,
            kind = kind,
            contextId = contextId,
            localVerdict = if (localResult.matchedBadWords.isEmpty()) "allowed" else "flagged"
        )

        return try {
            pendingCollection.document(id).set(pending.toMap()).await()

            ModerationSubmissionResult(
                success = true,
                blockedLocally = false,
                pendingId = id,
                message = "Submitted for moderation."
            )

        } catch (e: Exception) {
            ModerationSubmissionResult(
                success = false,
                blockedLocally = false,
                message = "Failed to save for moderation: ${e.message}"
            )
        }
    }

    /**
     * Simple local moderation: checks text against a small abusive-word list.
     * (You can expand this list over time; this does not depend on Hilt or Context.)
     */
    private fun runLocalModeration(text: String): LocalModerationResult {
        val lower = text.lowercase()

        // Core abusive terms – you can extend this list anytime.
        val keywords = listOf(
            // English
            "fuck", "shit", "bitch", "asshole", "bastard", "slut", "whore", "dick", "cock",

            // Hindi / Hinglish (latin)
            "chutiya", "chutiy*", "madarchod", "mc ", "bhenchod", "bc ",
            "randi", "lavda", "lund", "gaand", "chut ",

            // Telugu transliterated
            "lanja", "lanjaa", "lanjakod", "puku", "pooka", "modda", "madda", "pooku"
        )

        val matched = keywords.filter { kw ->
            kw.isNotBlank() && lower.contains(kw.replace("*", ""))
        }

        return LocalModerationResult(
            allowed = matched.isEmpty(),
            matchedBadWords = matched
        )
    }

    /**
     * Convert to Firestore map.
     */
    private fun PendingContent.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "text" to text,
        "kind" to kind.name,
        "contextId" to contextId,
        "createdAt" to createdAt,
        "status" to status,
        "localVerdict" to localVerdict
    )
}
