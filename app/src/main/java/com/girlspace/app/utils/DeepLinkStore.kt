package com.girlspace.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple in-memory bus for deep links + share entry points.
 * (Matches your existing usage in GirlSpaceApp)
 */
object DeepLinkStore {

    // -------------------------
    // Existing: Chat deep link
    // -------------------------
    private val _openChatThreadId = MutableStateFlow<String?>(null)
    val openChatThreadId: StateFlow<String?> = _openChatThreadId

    fun openChat(threadId: String) {
        _openChatThreadId.value = threadId
    }

    fun clearChat() {
        _openChatThreadId.value = null
    }

    // -------------------------
    // NEW: Share into Reels
    // -------------------------
    private val _sharedText = MutableStateFlow<String?>(null)        // URL or caption text
    val sharedText: StateFlow<String?> = _sharedText

    private val _sharedVideoUri = MutableStateFlow<String?>(null)    // content://... as String
    val sharedVideoUri: StateFlow<String?> = _sharedVideoUri

    fun setSharedText(text: String) {
        _sharedText.value = text
    }

    fun clearSharedText() {
        _sharedText.value = null
    }

    fun setSharedVideoUri(uriString: String) {
        _sharedVideoUri.value = uriString
    }

    fun clearSharedVideoUri() {
        _sharedVideoUri.value = null
    }
}
