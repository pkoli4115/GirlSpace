package com.girlspace.app.data.chat

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val mediaUrl: String? = null,        // image / video / audio
    val mediaType: String? = null,       // "image", "video", "audio"
    val createdAt: Timestamp = Timestamp.now(),
    val readBy: List<String> = emptyList(),

    // NEW: Map<uid, emoji> for reactions
    val reactions: Map<String, String> = emptyMap()
)
