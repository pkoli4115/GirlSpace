package com.girlspace.app.data.chat

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val mediaUrl: String? = null,         // image/video/audio/file
    val mediaType: String? = null,       // "image", "video", "audio", "file"
    val mediaThumbnail: String? = null,  // generated preview for image/video
    val audioDuration: Long? = null,     // for voice messages in ms
    val replyTo: String? = null,         // messageId being replied to
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Timestamp = Timestamp.now(),
    val readBy: List<String> = emptyList()
)
