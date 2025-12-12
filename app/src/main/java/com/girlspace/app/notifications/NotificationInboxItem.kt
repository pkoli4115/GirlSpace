package com.girlspace.app.data.notifications

import com.google.firebase.Timestamp

enum class NotificationCategory {
    CHAT,
    SOCIAL,
    INSPIRATION,
    FESTIVAL,
    SYSTEM,
    BIRTHDAY
}

enum class NotificationImportance {
    CRITICAL,
    NORMAL,
    LOW
}

/**
 * This is what powers the future 🔔 inbox.
 * We will always write these items even if we don't push.
 */
data class NotificationInboxItem(
    val id: String = "",
    val category: NotificationCategory = NotificationCategory.SYSTEM,
    val importance: NotificationImportance = NotificationImportance.NORMAL,

    val type: String = "",          // e.g. "chat_message", "post_comment", "friend_request"
    val title: String = "",         // user-visible
    val body: String = "",          // user-visible

    val deepLink: String = "",      // e.g. "togetherly://chat/{threadId}"
    val entityId: String = "",      // threadId / postId / userId

    val read: Boolean = false,
    val createdAt: Timestamp? = null
)
