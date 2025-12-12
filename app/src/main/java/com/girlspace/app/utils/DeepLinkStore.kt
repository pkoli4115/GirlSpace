package com.girlspace.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory bus:
 * MainActivity writes threadId on notification tap,
 * GirlSpaceApp listens and navigates to chat/{threadId}.
 */
object DeepLinkStore {
    private val _openChatThreadId = MutableStateFlow<String?>(null)
    val openChatThreadId: StateFlow<String?> = _openChatThreadId

    fun openChat(threadId: String) {
        _openChatThreadId.value = threadId
    }

    fun clearChat() {
        _openChatThreadId.value = null
    }
}
