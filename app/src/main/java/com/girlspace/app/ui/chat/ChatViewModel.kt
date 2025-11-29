package com.girlspace.app.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.chat.ChatMessage
import com.girlspace.app.data.chat.ChatRepository
import com.girlspace.app.data.chat.ChatThread
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val repo = ChatRepository(
        auth = auth,
        firestore = firestore
    )

    private var threadsListener: ListenerRegistration? = null
    private var msgListener: ListenerRegistration? = null

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads

    private val _selectedThread = MutableStateFlow<ChatThread?>(null)
    val selectedThread: StateFlow<ChatThread?> = _selectedThread

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // prevent double-send taps
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _isStartingChat = MutableStateFlow(false)
    val isStartingChat: StateFlow<Boolean> = _isStartingChat

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // currently selected messageId for inline reaction bar
    private val _selectedMessageForReaction = MutableStateFlow<String?>(null)
    val selectedMessageForReaction: StateFlow<String?> = _selectedMessageForReaction

    // 🔹 Current plan: "free" / "basic" / "premium"
    private val _currentPlan = MutableStateFlow("free")
    val currentPlan: StateFlow<String> = _currentPlan

    init {
        listenThreads()
        loadUserPlan()
    }

    /* -------------------------------------------------------------------
       LOAD USER PLAN FROM FIRESTORE
    ------------------------------------------------------------------- */
    private fun loadUserPlan() {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val snap = firestore
                    .collection("users")
                    .document(uid)
                    .get()
                    .await()

                val planRaw = snap.getString("plan")
                val isPremiumFlag = snap.getBoolean("isPremium") ?: false

                // If older docs only have isPremium, map that → "premium"
                val resolved = (planRaw ?: if (isPremiumFlag) "premium" else "free")
                    .lowercase()

                _currentPlan.value = resolved
                Log.d("ChatViewModel", "Loaded user plan: $resolved")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "loadUserPlan failed", e)
                // Fallback stays "free"
            }
        }
    }

    /* -------------------------------------------------------------------
       THREAD LISTENER
    ------------------------------------------------------------------- */
    private fun listenThreads() {
        threadsListener = repo.observeThreads { list ->
            _threads.value = list
            if (_selectedThread.value == null && list.isNotEmpty()) {
                selectThread(list.first())
            }
        }
    }

    /* -------------------------------------------------------------------
       START CHAT BY EMAIL
    ------------------------------------------------------------------- */
    fun startChatWithEmail(email: String) {
        viewModelScope.launch {
            try {
                _isStartingChat.value = true
                val thread = repo.startOrGetThreadByEmail(email)
                selectThread(thread)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isStartingChat.value = false
            }
        }
    }

    /* -------------------------------------------------------------------
       SELECT THREAD + LISTEN MESSAGES
    ------------------------------------------------------------------- */
    fun selectThread(thread: ChatThread) {
        _selectedThread.value = thread

        msgListener?.remove()
        msgListener = repo.observeMessages(thread.id) { msgs ->
            _messages.value = msgs
        }
    }

    /* -------------------------------------------------------------------
       TEXT INPUT
    ------------------------------------------------------------------- */
    fun setInputText(text: String) {
        _inputText.value = text
        _isTyping.value = text.isNotBlank()
    }

    /* -------------------------------------------------------------------
       SEND MESSAGE (TEXT)
    ------------------------------------------------------------------- */
    fun sendMessage() {
        val thread = _selectedThread.value ?: return
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        // Ignore extra taps while sending
        if (_isSending.value) return

        viewModelScope.launch {
            try {
                _isSending.value = true

                repo.sendMessage(
                    threadId = thread.id,
                    text = text
                )

                _inputText.value = ""
                _isTyping.value = false
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("Chat", "sendMessage failed", e)
            } finally {
                _isSending.value = false
            }
        }
    }

    /* -------------------------------------------------------------------
       MEDIA SEND (PLAN-AWARE)
    ------------------------------------------------------------------- */
    fun sendMedia(context: Context, uri: Uri) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                } ?: throw Exception("Unable to read file")

                val sizeMb = file.size / (1024f * 1024f)

                val isVideo = uri.toString().contains(".mp4", ignoreCase = true)
                        || uri.toString().contains("video", ignoreCase = true)

                // 🔹 Use the actual plan from Firestore
                val plan = _currentPlan.value

                // Simple per-plan size limit (you can later read this from PlanLimits)
                val maxSize = when (plan) {
                    "premium" -> 5f  // 5 MB
                    "basic" -> 2f    // 2 MB
                    else -> 2f       // free
                }

                if (sizeMb > maxSize) {
                    _errorMessage.value =
                        "File too large for your plan. Max allowed: ${maxSize}MB"
                    return@launch
                }

                if (isVideo && plan != "premium") {
                    _errorMessage.value = "Only Premium users can send videos in chat."
                    return@launch
                }

                // Upload to Storage
                val extension = if (isVideo) "mp4" else "jpg"
                val ref = storage.reference.child(
                    "chat_media/${System.currentTimeMillis()}.$extension"
                )
                ref.putBytes(file).await()
                val url = ref.downloadUrl.await().toString()

                repo.sendMessage(
                    threadId = thread.id,
                    text = "",
                    mediaUrl = url,
                    mediaType = if (isVideo) "video" else "image"
                )

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send media"
                Log.e("Chat", "Media send failed", e)
            }
        }
    }

    /* -------------------------------------------------------------------
       REACTIONS
    ------------------------------------------------------------------- */
    fun openReactionPicker(messageId: String) {
        _selectedMessageForReaction.value = messageId
    }

    fun closeReactionPicker() {
        _selectedMessageForReaction.value = null
    }

    fun reactToMessage(messageId: String, emoji: String) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                repo.addReaction(
                    messageId = messageId,
                    userId = uid,
                    emoji = emoji
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    /* -------------------------------------------------------------------
       ERROR
    ------------------------------------------------------------------- */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        threadsListener?.remove()
        msgListener?.remove()
    }
}
