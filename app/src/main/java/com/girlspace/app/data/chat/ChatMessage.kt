package com.girlspace.app.data.chat

import com.google.firebase.Timestamp

/**
 * Single message inside a chat thread.
 *
 * Used by ChatViewModel + ChatRepository.
 */
data class ChatMessage(
    val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val readBy: List<String> = emptyList()
)
