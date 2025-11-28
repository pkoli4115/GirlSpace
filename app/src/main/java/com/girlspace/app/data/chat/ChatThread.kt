package com.girlspace.app.data.chat

import com.google.firebase.Timestamp

/**
 * 1:1 conversation summary between current user and another user.
 *
 * Used by ChatViewModel + ChatRepository.
 */
data class ChatThread(
    val id: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserEmail: String = "",
    val lastMessage: String = "",
    val lastTimestamp: Timestamp? = null,
    val unreadCount: Int = 0,
    val members: List<String> = emptyList()
)
