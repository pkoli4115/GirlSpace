package com.girlspace.app.ui.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import com.girlspace.app.data.chat.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GroupChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var listener: ListenerRegistration? = null
    private var currentGroupId: String? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    /**
     * Start listening to messages for this group.
     */
    fun start(groupId: String) {
        if (currentGroupId == groupId) return
        currentGroupId = groupId

        listener?.remove()
        listener = firestore
            .collection("groups")
            .document(groupId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("GroupChatViewModel", "listen failed", e)
                    _error.value = e.message
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                val list = docs.map { doc ->
                    ChatMessage(
                        id = doc.id,
                        threadId = groupId, // reuse field for groupId
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "GirlSpace user",
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        readBy = (doc.get("readBy") as? List<String>) ?: emptyList()
                    )
                }
                _messages.value = list
            }
    }

    fun updateInput(text: String) {
        _inputText.value = text
        _isTyping.value = text.isNotBlank()
    }

    fun sendMessage() {
        val groupId = currentGroupId ?: return
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        val user = auth.currentUser ?: run {
            _error.value = "Not logged in"
            return
        }

        _isSending.value = true

        val messagesRef = firestore
            .collection("groups")
            .document(groupId)
            .collection("messages")

        val newDoc = messagesRef.document()

        val data = hashMapOf(
            "id" to newDoc.id,
            "threadId" to groupId,
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "GirlSpace user"),
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "readBy" to listOf(user.uid)
        )

        newDoc.set(data)
            .addOnSuccessListener {
                _isSending.value = false
                _inputText.value = ""
                _isTyping.value = false
            }
            .addOnFailureListener { e ->
                Log.e("GroupChatViewModel", "sendMessage failed", e)
                _isSending.value = false
                _error.value = e.message ?: "Failed to send message"
            }
    }

    fun clearError() {
        _error.value = null
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
