package com.girlspace.app.data.chat

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val mediaUrl: String? = null,         // image/video/audio/file
    val mediaType: String? = null,        // "image", "video", "audio", "file", "location", "live_location", "contact"
    val mediaThumbnail: String? = null,   // generated preview for image/video
    val audioDuration: Long? = null,      // for voice messages in ms
    val replyTo: String? = null,          // messageId being replied to
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Timestamp = Timestamp.now(),
    val readBy: List<String> = emptyList(),

    // 🔹 EXTRA payload from Firestore: location, contact, etc.
    val extra: Map<String, Any>? = null
) {
    // ───────────────────────── LOCATION HELPERS ─────────────────────────

    val locationLat: Double?
        get() = (extra?.get("lat") as? Number)?.toDouble()

    val locationLng: Double?
        get() = (extra?.get("lng") as? Number)?.toDouble()

    val locationAddress: String?
        get() = extra?.get("address")?.toString()

    val isLiveLocation: Boolean
        get() = (extra?.get("isLive") as? Boolean) == true ||
                (extra?.get("is_live") as? Boolean) == true

    // ───────────────────────── CONTACT HELPERS ─────────────────────────

    val contactName: String?
        get() = extra?.get("name")?.toString()

    val contactPhones: List<String>
        @Suppress("UNCHECKED_CAST")
        get() = when (val p = extra?.get("phones")) {
            is List<*> -> p.filterIsInstance<String>()
            is String -> listOf(p)
            else -> emptyList()
        }

    val contactPrimaryPhone: String?
        get() = contactPhones.firstOrNull()

    val contactEmail: String?
        get() = extra?.get("email")?.toString()
}
