package com.girlspace.app.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.chat.ChatMessage
import com.girlspace.app.data.chat.ChatRepository
import com.girlspace.app.data.chat.ChatThread
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repo = ChatRepository(
        auth = FirebaseAuth.getInstance(),
        firestore = FirebaseFirestore.getInstance()
    )

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads

    private val _selectedThread = MutableStateFlow<ChatThread?>(null)
    val selectedThread: StateFlow<ChatThread?> = _selectedThread

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isStartingChat = MutableStateFlow(false)
    val isStartingChat: StateFlow<Boolean> = _isStartingChat

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private var threadsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    init {
        observeThreads()
    }

    private fun observeThreads() {
        _isLoading.value = true
        threadsListener = repo.observeThreads { list ->
            _threads.value = list
            _isLoading.value = false

            // If there is no selected thread but we have threads, pick the first one.
            if (_selectedThread.value == null && list.isNotEmpty()) {
                selectThreadInternal(list.first())
            }
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun startChatWithEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Enter a valid email to start a chat"
            return
        }

        viewModelScope.launch {
            try {
                _isStartingChat.value = true
                _errorMessage.value = null

                val thread = repo.startOrGetThreadByEmail(trimmed)
                // Once started, ensure it’s in the list + selected
                selectThreadInternal(thread)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "startChatWithEmail failed", e)
                _errorMessage.value = e.message ?: "Failed to start chat"
            } finally {
                _isStartingChat.value = false
            }
        }
    }

    fun selectThread(thread: ChatThread) {
        selectThreadInternal(thread)
    }

    private fun selectThreadInternal(thread: ChatThread) {
        _selectedThread.value = thread
        messagesListener?.remove()
        messagesListener = repo.observeMessages(thread.id) { list ->
            _messages.value = list
        }
    }

    fun sendMessage() {
        val thread = _selectedThread.value ?: run {
            _errorMessage.value = "Select a chat first"
            return
        }
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repo.sendMessage(thread.id, text)
                _inputText.value = ""
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage failed", e)
                _errorMessage.value = e.message ?: "Failed to send message"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        threadsListener?.remove()
        messagesListener?.remove()
    }
}
