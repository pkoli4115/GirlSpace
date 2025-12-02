package com.girlspace.app.data.chat

data class ChatThread(
    val id: String = "",

    // 1-to-1 legacy fields
    val userA: String = "",
    val userB: String = "",
    val userAName: String = "",
    val userBName: String = "",

    // NEW: group participants
    val participants: List<String> = emptyList(),

    // NEW: name cache loaded from Firestore.users (optional)
    val participantNames: Map<String, String> = emptyMap(),

    // Chat previews
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,

    // unread_<uid> value mapped in ChatViewModel before entering ChatsScreen
    val unreadCount: Int = 0
) {

    /** Returns TRUE if more than 2 participants (a group). */
    fun isGroup(): Boolean {
        return participants.size > 2
    }

    /** Returns the other user ID for 1-to-1 threads. (Legacy compatibility) */
    fun otherUserId(myId: String): String {
        return if (myId == userA) userB else userA
    }

    /** Returns other user's display name for 1-to-1 chats. */
    fun otherUserName(myId: String): String {
        return if (myId == userA) userBName else userAName
    }

    /**
     * NEW:
     * Build group name in WhatsApp style:
     * "You, Alice, Bob +2"
     */
    fun groupName(myId: String): String {
        if (!isGroup()) {
            return otherUserName(myId)
        }

        // Build names from participantNames map
        val list = participants.map { uid ->
            if (uid == myId) "You" else (participantNames[uid] ?: "User")
        }

        return when {
            list.size <= 3 -> list.joinToString(", ")
            else -> {
                val base = list.take(3).joinToString(", ")
                "$base +${list.size - 3}"
            }
        }
    }
}
