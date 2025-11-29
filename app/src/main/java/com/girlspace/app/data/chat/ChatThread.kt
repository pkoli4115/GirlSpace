package com.girlspace.app.data.chat

data class ChatThread(
    val id: String = "",
    val userA: String = "",
    val userB: String = "",
    val userAName: String = "",
    val userBName: String = "",
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0,
) {
    fun otherUserId(myId: String): String {
        return if (myId == userA) userB else userA
    }

    fun otherUserName(myId: String): String {
        return if (myId == userA) userBName else userAName
    }
}
