package com.girlspace.app.ui.chat
import  com.google.firebase.firestore.FieldValue
import java.io.File
import android.provider.OpenableColumns

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.chat.ChatMessage
import com.girlspace.app.data.chat.ChatRepository
import com.girlspace.app.data.chat.ChatThread
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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
    private var typingListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null

    // Threads
    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads

    private val _filteredThreads = MutableStateFlow<List<ChatThread>>(emptyList())
    val filteredThreads: StateFlow<List<ChatThread>> = _filteredThreads

    private val _selectedThread = MutableStateFlow<ChatThread?>(null)
    val selectedThread: StateFlow<ChatThread?> = _selectedThread

    // Messages (with simple pagination)
    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private var loadedMessageCount: Int = 30
    private val pageSize: Int = 30

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _isStartingChat = MutableStateFlow(false)
    val isStartingChat: StateFlow<Boolean> = _isStartingChat

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _selectedMessageForReaction = MutableStateFlow<String?>(null)
    val selectedMessageForReaction: StateFlow<String?> = _selectedMessageForReaction

    private val _currentPlan = MutableStateFlow("free")
    val currentPlan: StateFlow<String> = _currentPlan

    private val _otherUserName = MutableStateFlow<String?>(null)
    val otherUserName: StateFlow<String?> = _otherUserName

    private val _isOtherOnline = MutableStateFlow(false)
    val isOtherOnline: StateFlow<Boolean> = _isOtherOnline

    // pretty text like "20 min ago", "3 hours ago"
    private val _lastSeenText = MutableStateFlow<String?>(null)
    val lastSeenText: StateFlow<String?> = _lastSeenText

    private var lastTypingSent: Boolean = false

    // Friends + Suggestions
    private val _friends = MutableStateFlow<List<ChatUserSummary>>(emptyList())
    val friends: StateFlow<List<ChatUserSummary>> = _friends

    private val _suggestions = MutableStateFlow<List<ChatUserSummary>>(emptyList())
    val suggestions: StateFlow<List<ChatUserSummary>> = _suggestions

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery

    private val _contactSearchResults =
        MutableStateFlow<List<ChatUserSummary>>(emptyList())
    val contactSearchResults: StateFlow<List<ChatUserSummary>> = _contactSearchResults

    // For avatar tap → open chat
    private val _lastStartedThread = MutableStateFlow<ChatThread?>(null)
    val lastStartedThread: StateFlow<ChatThread?> = _lastStartedThread

    init {
        listenThreads()
        loadUserPlan()
        loadFriendsAndSuggestions()

        viewModelScope.launch {
            markUserActive()
        }
    }

    // -------------------------------------------------------------
    // PLAN
    // -------------------------------------------------------------
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
                val resolved = (planRaw ?: if (isPremiumFlag) "premium" else "free")
                    .lowercase()

                _currentPlan.value = resolved
                Log.d("ChatViewModel", "Loaded user plan: $resolved")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "loadUserPlan failed", e)
            }
        }
    }

    // -------------------------------------------------------------
    // FRIENDS + SUGGESTIONS (with presence)
    // -------------------------------------------------------------
    private fun loadFriendsAndSuggestions() {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val meDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                val followingArray = meDoc.get("following") as? List<*>
                val followingIds = followingArray
                    ?.filterIsInstance<String>()
                    ?.toSet()
                    ?: emptySet()

                // Friends row
                val friendSummaries = mutableListOf<ChatUserSummary>()
                for (fid in followingIds) {
                    val userDoc = firestore.collection("users")
                        .document(fid)
                        .get()
                        .await()

                    if (!userDoc.exists()) continue

                    val displayName =
                        userDoc.getString("displayName")
                            ?: userDoc.getString("name")
                            ?: userDoc.getString("fullName")
                            ?: userDoc.getString("username")
                            ?: "GirlSpace user"

                    val avatar = userDoc.getString("photoUrl")
                        ?: userDoc.getString("avatarUrl")

                    // presence for this friend
                    val statusDoc = firestore.collection("user_status")
                        .document(fid)
                        .get()
                        .await()

                    val lastActive = statusDoc.getLong("lastActiveAt") ?: 0L
                    val now = System.currentTimeMillis()
                    val onlineThreshold = 90_000L
                    val isOnline = lastActive > 0L && (now - lastActive) <= onlineThreshold

                    friendSummaries.add(
                        ChatUserSummary(
                            uid = fid,
                            displayName = displayName,
                            avatarUrl = avatar,
                            mutualCount = 0,
                            isOnline = isOnline,
                            lastActiveAt = lastActive
                        )
                    )
                }
                _friends.value = friendSummaries

                // Suggestions: friends-of-friends
                val suggestionIds = mutableMapOf<String, Int>()

                for (fid in followingIds) {
                    val friendDoc = firestore.collection("users")
                        .document(fid)
                        .get()
                        .await()

                    val friendFollowingArray = friendDoc.get("following") as? List<*>
                    val friendFollowingIds = friendFollowingArray
                        ?.filterIsInstance<String>()
                        ?: emptyList()

                    friendFollowingIds.forEach { otherId ->
                        if (otherId == uid) return@forEach
                        if (followingIds.contains(otherId)) return@forEach
                        val current = suggestionIds[otherId] ?: 0
                        suggestionIds[otherId] = current + 1
                    }
                }

                val suggestionsList = mutableListOf<ChatUserSummary>()
                suggestionIds.entries
                    .sortedByDescending { it.value }
                    .take(20)
                    .forEach { (otherId, mutualCount) ->
                        val userDoc = firestore.collection("users")
                            .document(otherId)
                            .get()
                            .await()

                        if (!userDoc.exists()) return@forEach

                        val displayName =
                            userDoc.getString("displayName")
                                ?: userDoc.getString("name")
                                ?: userDoc.getString("fullName")
                                ?: userDoc.getString("username")
                                ?: "GirlSpace user"

                        val avatar = userDoc.getString("photoUrl")
                            ?: userDoc.getString("avatarUrl")

                        val statusDoc = firestore.collection("user_status")
                            .document(otherId)
                            .get()
                            .await()

                        val lastActive = statusDoc.getLong("lastActiveAt") ?: 0L
                        val now = System.currentTimeMillis()
                        val onlineThreshold = 90_000L
                        val isOnline = lastActive > 0L && (now - lastActive) <= onlineThreshold

                        suggestionsList.add(
                            ChatUserSummary(
                                uid = otherId,
                                displayName = displayName,
                                avatarUrl = avatar,
                                mutualCount = mutualCount,
                                isOnline = isOnline,
                                lastActiveAt = lastActive
                            )
                        )
                    }

                _suggestions.value = suggestionsList
            } catch (e: Exception) {
                Log.e("ChatViewModel", "loadFriendsAndSuggestions failed", e)
            }
        }
    }

    // -------------------------------------------------------------
    // THREADS + FILTER
    // -------------------------------------------------------------
    private fun listenThreads() {
        threadsListener = repo.observeThreads { list ->
            _threads.value = list
            applyThreadFilter()
            if (_selectedThread.value == null && list.isNotEmpty()) {
                selectThread(list.first())
            }
        }
    }

    private fun applyThreadFilter() {
        val q = _searchQuery.value.trim().lowercase()
        val myId = auth.currentUser?.uid
        val base = _threads.value

        if (q.isBlank() || myId == null) {
            _filteredThreads.value = base
            return
        }

        _filteredThreads.value = base.filter { thread ->
            val otherName = thread.otherUserName(myId)
            otherName.lowercase().contains(q) ||
                    thread.lastMessage.lowercase().contains(q)
        }
    }

    fun updateSearchQuery(newQuery: String) {
        _searchQuery.value = newQuery
        applyThreadFilter()
    }

    // -------------------------------------------------------------
    // CONTACT SEARCH
    // -------------------------------------------------------------
    fun onContactSearchQueryChange(newValue: String) {
        _contactSearchQuery.value = newValue

        val trimmed = newValue.trim()
        if (trimmed.length < 3) {
            _contactSearchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            performContactSearch(trimmed)
        }
    }

    private suspend fun performContactSearch(query: String) {
        val currentUser = auth.currentUser ?: return

        try {
            val lower = query.lowercase()

            val snap = firestore.collection("users")
                .limit(200)
                .get()
                .await()

            val results = snap.documents.mapNotNull { doc ->
                val uid = doc.id
                if (uid == currentUser.uid) return@mapNotNull null

                val name = doc.getString("name")
                    ?: doc.getString("fullName")
                    ?: doc.getString("displayName")
                    ?: ""
                val email = doc.getString("email") ?: ""
                val phone = doc.getString("phone") ?: ""

                val matches = name.contains(lower, ignoreCase = true) ||
                        email.contains(lower, ignoreCase = true) ||
                        phone.contains(lower, ignoreCase = true)

                if (!matches) return@mapNotNull null

                val displayName = when {
                    name.isNotBlank() -> name
                    email.isNotBlank() -> email
                    phone.isNotBlank() -> phone
                    else -> "GirlSpace user"
                }

                val avatar = doc.getString("photoUrl")
                    ?: doc.getString("avatarUrl")

                ChatUserSummary(
                    uid = uid,
                    displayName = displayName,
                    avatarUrl = avatar,
                    mutualCount = 0
                )
            }

            _contactSearchResults.value = results
        } catch (e: Exception) {
            Log.e("ChatViewModel", "performContactSearch failed", e)
            _errorMessage.value = e.message ?: "Search failed"
        }
    }

    // -------------------------------------------------------------
    // START CHAT
    // -------------------------------------------------------------
    fun startChatWithEmail(email: String) {
        viewModelScope.launch {
            try {
                _isStartingChat.value = true
                val thread = repo.startOrGetThreadByEmail(email)
                selectThread(thread)
                _lastStartedThread.value = thread
                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isStartingChat.value = false
            }
        }
    }

    fun startChatWithUser(otherUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (otherUid == myUid) return

        viewModelScope.launch {
            try {
                _isStartingChat.value = true

                // Use same "chatThreads" collection as repository
                val threadsRef = firestore.collection("chatThreads")

                val existingAB = threadsRef
                    .whereEqualTo("userA", myUid)
                    .whereEqualTo("userB", otherUid)
                    .get()
                    .await()

                val existingBA = threadsRef
                    .whereEqualTo("userA", otherUid)
                    .whereEqualTo("userB", myUid)
                    .get()
                    .await()

                val existingDoc = (existingAB.documents + existingBA.documents)
                    .firstOrNull()

                val thread: ChatThread = if (existingDoc != null) {
                    val t = existingDoc.toObject(ChatThread::class.java)
                    (t ?: ChatThread()).copy(id = existingDoc.id)
                } else {
                    val meDoc = firestore.collection("users")
                        .document(myUid)
                        .get()
                        .await()
                    val otherDoc = firestore.collection("users")
                        .document(otherUid)
                        .get()
                        .await()

                    val myName =
                        meDoc.getString("displayName")
                            ?: meDoc.getString("name")
                            ?: "You"

                    val otherName =
                        otherDoc.getString("displayName")
                            ?: otherDoc.getString("name")
                            ?: "GirlSpace user"

                    val newDoc = threadsRef.document()
                    val now = System.currentTimeMillis()

                    val data = mapOf(
                        "userA" to myUid,
                        "userB" to otherUid,
                        "userAName" to myName,
                        "userBName" to otherName,
                        "participants" to listOf(myUid, otherUid),
                        "lastMessage" to "",
                        "lastTimestamp" to now,
                        "unread_$myUid" to 0L,
                        "unread_$otherUid" to 0L
                    )

                    newDoc.set(data).await()

                    ChatThread(
                        id = newDoc.id,
                        userA = myUid,
                        userB = otherUid,
                        userAName = myName,
                        userBName = otherName,
                        lastMessage = "",
                        lastTimestamp = now,
                        unreadCount = 0
                    )
                }

                selectThread(thread)
                _lastStartedThread.value = thread
                markUserActive()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "startChatWithUser failed", e)
                _errorMessage.value = e.message
            } finally {
                _isStartingChat.value = false
            }
        }
    }

    fun consumeLastStartedThread() {
        _lastStartedThread.value = null
    }

    // Ensure correct thread selected when entering via route "chat/{threadId}"
    fun ensureThreadSelected(threadId: String) {
        val current = _selectedThread.value
        if (current?.id == threadId) return

        val fromList = _threads.value.firstOrNull { it.id == threadId }
        if (fromList != null) {
            selectThread(fromList)
        } else {
            viewModelScope.launch {
                try {
                    val snap = firestore.collection("chatThreads")
                        .document(threadId)
                        .get()
                        .await()
                    if (snap.exists()) {
                        val t = snap.toObject(ChatThread::class.java)
                        t?.let { selectThread(it.copy(id = threadId)) }
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "ensureThreadSelected failed", e)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // SELECT THREAD + LISTEN
    // -------------------------------------------------------------
    fun selectThread(thread: ChatThread) {
        _selectedThread.value = thread

        msgListener?.remove()
        msgListener = repo.observeMessages(thread.id) { msgs ->
            _allMessages.value = msgs

            // pagination: keep last [loadedMessageCount]
            val slice = msgs.takeLast(loadedMessageCount)
            _messages.value = slice

            markMessagesAsReadForCurrentUser(slice)
        }

        _isTyping.value = false
        lastTypingSent = false

        val myUid = auth.currentUser?.uid
        val otherUid = when (myUid) {
            null -> null
            thread.userA -> thread.userB
            thread.userB -> thread.userA
            else -> null
        }

        val otherName = when (myUid) {
            thread.userA -> thread.userBName
            thread.userB -> thread.userAName
            else -> thread.userAName
        }
        _otherUserName.value = otherName

        // typing listener
        typingListener?.remove()
        if (otherUid != null) {
            typingListener = firestore.collection("typing_status")
                .document(thread.id)
                .collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChatViewModel", "observeTypingUsers error", error)
                        _isTyping.value = false
                        return@addSnapshotListener
                    }

                    val now = System.currentTimeMillis()
                    val cutoff = now - 8_000L

                    val otherTyping = snapshot?.documents
                        ?.firstOrNull { it.id == otherUid }
                        ?.let { doc ->
                            val ts = doc.getLong("updatedAt") ?: 0L
                            val flag = doc.getBoolean("isTyping") ?: false
                            flag && ts >= cutoff
                        } ?: false

                    _isTyping.value = otherTyping
                }
        } else {
            typingListener = null
        }

        // presence listener
        presenceListener?.remove()
        if (otherUid != null) {
            presenceListener = firestore.collection("user_status")
                .document(otherUid)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e("ChatViewModel", "observeUserPresence error", error)
                        _isOtherOnline.value = false
                        _lastSeenText.value = null
                        return@addSnapshotListener
                    }

                    val lastActive = snap?.getLong("lastActiveAt") ?: 0L
                    if (lastActive == 0L) {
                        _isOtherOnline.value = false
                        _lastSeenText.value = null
                        return@addSnapshotListener
                    }

                    val now = System.currentTimeMillis()
                    val onlineThreshold = 90_000L

                    val online = now - lastActive <= onlineThreshold
                    _isOtherOnline.value = online

                    if (!online) {
                        val diffMinutes =
                            ((now - lastActive) / 60_000L).coerceAtLeast(1L)

                        val pretty = when {
                            diffMinutes < 60L -> "${diffMinutes} min ago"
                            diffMinutes < 60L * 24L -> {
                                val hours = diffMinutes / 60L
                                "$hours hour" + if (hours == 1L) "" else "s ago"
                            }
                            else -> {
                                val days = diffMinutes / (60L * 24L)
                                "$days day" + if (days == 1L) "" else "s ago"
                            }
                        }

                        _lastSeenText.value = pretty
                    } else {
                        _lastSeenText.value = null
                    }
                }
        } else {
            presenceListener = null
        }

        viewModelScope.launch {
            markUserActive()
        }
    }

    // Pagination: load older messages
    fun loadMoreMessages() {
        val all = _allMessages.value
        val current = _messages.value
        if (all.size <= current.size) return

        loadedMessageCount = (loadedMessageCount + pageSize)
            .coerceAtMost(all.size)

        _messages.value = all.takeLast(loadedMessageCount)
    }

    // Mark messages as read for ticks
    private fun markMessagesAsReadForCurrentUser(msgs: List<ChatMessage>) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                msgs
                    .filter { it.senderId != uid && !it.readBy.contains(uid) }
                    .forEach { msg ->
                        // Prefer msg.threadId, fall back to selected thread
                        val threadId = if (msg.threadId.isNotBlank()) {
                            msg.threadId
                        } else {
                            _selectedThread.value?.id ?: return@forEach
                        }

                        firestore.collection("chatThreads")
                            .document(threadId)
                            .collection("messages")
                            .document(msg.id)
                            .update("readBy", FieldValue.arrayUnion(uid))
                            .await()
                    }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "markMessagesAsReadForCurrentUser failed", e)
            }
        }
    }


    // -------------------------------------------------------------
    // TYPING STATE
    // -------------------------------------------------------------
    fun setInputText(text: String) {
        _inputText.value = text
        updateTypingState()
    }

    private fun updateTypingState() {
        val thread = _selectedThread.value ?: return
        val uid = auth.currentUser?.uid ?: return

        val typingNow = _inputText.value.isNotBlank()
        if (typingNow == lastTypingSent) return
        lastTypingSent = typingNow

        viewModelScope.launch {
            try {
                val docRef = firestore.collection("typing_status")
                    .document(thread.id)
                    .collection("users")
                    .document(uid)

                if (typingNow) {
                    val now = System.currentTimeMillis()
                    docRef.set(
                        mapOf(
                            "isTyping" to true,
                            "updatedAt" to now
                        )
                    ).await()
                } else {
                    docRef.delete().await()
                }

                markUserActive()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "updateTypingState failed", e)
            }
        }
    }

    // -------------------------------------------------------------
    // PRESENCE
    // -------------------------------------------------------------
    private suspend fun markUserActive() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val now = System.currentTimeMillis()
            firestore.collection("user_status")
                .document(uid)
                .set(
                    mapOf("lastActiveAt" to now),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "markUserActive failed", e)
        }
    }

    // -------------------------------------------------------------
    // SEND MESSAGE
    // -------------------------------------------------------------
    fun sendMessage() {
        val thread = _selectedThread.value ?: return
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        if (_isSending.value) return

        viewModelScope.launch {
            try {
                _isSending.value = true

                repo.sendMessage(
                    threadId = thread.id,
                    text = text
                )

                _inputText.value = ""
                updateTypingState()
                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("Chat", "sendMessage failed", e)
            } finally {
                _isSending.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // MEDIA SEND (gallery / file picker / voice notes)
    // ------------------------------------------------------------
    // -------------------------------------------------------------
    // VOICE NOTE SEND (file path from ChatScreen)
    // -------------------------------------------------------------
    fun sendVoiceNote(filePath: String) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _errorMessage.value = "Voice note file not found"
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    file.readBytes()
                }

                val sizeMb = bytes.size / (1024f * 1024f)

                val plan = _currentPlan.value
                val maxSize = when (plan) {
                    "premium" -> 5f
                    "basic" -> 2f
                    else -> 2f
                }

                if (sizeMb > maxSize) {
                    _errorMessage.value =
                        "Voice note too large for your plan. Max allowed: ${maxSize}MB"
                    return@launch
                }

                val ref = storage.reference.child(
                    "chat_media/${System.currentTimeMillis()}_voice.m4a"
                )
                ref.putBytes(bytes).await()
                val url = ref.downloadUrl.await().toString()

                repo.sendMessage(
                    threadId = thread.id,
                    text = "",
                    mediaUrl = url,
                    mediaType = "audio"
                )

                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send voice note"
            }
        }
    }

    // -------------------------------------------------------------
// MEDIA SEND (gallery / file picker / voice notes)
// -------------------------------------------------------------
// -------------------------------------------------------------
// MEDIA SEND (gallery / file picker / voice notes)
// -------------------------------------------------------------
    fun sendMedia(context: Context, uri: Uri) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val mime = context.contentResolver.getType(uri)
                val pathLower = uri.toString().lowercase()

                // Detect type
                val isVideo = mime?.startsWith("video/") == true ||
                        pathLower.endsWith(".mp4") ||
                        pathLower.endsWith(".mov") ||
                        pathLower.contains("video")

                val isAudio = mime?.startsWith("audio/") == true ||
                        pathLower.endsWith(".m4a") ||
                        pathLower.endsWith(".aac") ||
                        pathLower.endsWith(".mp3") ||
                        pathLower.contains("audio")

                val isImage = mime?.startsWith("image/") == true ||
                        pathLower.endsWith(".jpg") ||
                        pathLower.endsWith(".jpeg") ||
                        pathLower.endsWith(".png") ||
                        pathLower.endsWith(".webp")

                // ⭐ Everything else is a generic file (PDF, Word, etc.)
                val mediaType = when {
                    isVideo -> "video"
                    isAudio -> "audio"
                    isImage -> "image"
                    else    -> "file"
                }

                // 👉 Get original display name (for files we show this)
                val displayName = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            cursor.getString(nameIndex)
                        } else null
                    }
                }

                val fileBytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                } ?: throw Exception("Unable to read file")

                val sizeMb = fileBytes.size / (1024f * 1024f)

                val plan = _currentPlan.value
                val maxSize = when (plan) {
                    "premium" -> 5f
                    "basic" -> 2f
                    else -> 2f
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

                val extension = when {
                    mime != null && mime.contains("/") -> mime.substringAfter("/")
                    pathLower.endsWith(".pdf") -> "pdf"
                    pathLower.endsWith(".docx") -> "docx"
                    pathLower.endsWith(".doc") -> "doc"
                    pathLower.endsWith(".pptx") -> "pptx"
                    pathLower.endsWith(".ppt") -> "ppt"
                    pathLower.endsWith(".xlsx") -> "xlsx"
                    pathLower.endsWith(".xls") -> "xls"
                    isVideo -> "mp4"
                    isAudio -> "m4a"
                    isImage -> "jpg"
                    else -> "bin"
                }

                val ref = storage.reference.child(
                    "chat_media/${System.currentTimeMillis()}_${mediaType}.$extension"
                )
                ref.putBytes(fileBytes).await()
                val url = ref.downloadUrl.await().toString()

                // 👉 For files, we store the *real* filename in text.
                val messageText = if (mediaType == "file") {
                    displayName ?: "File"
                } else {
                    ""
                }

                repo.sendMessage(
                    threadId = thread.id,
                    text = messageText,
                    mediaUrl = url,
                    mediaType = mediaType
                )

                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send media"
                Log.e("Chat", "Media send failed", e)
            }
        }
    }


    // -------------------------------------------------------------
    // MEDIA SEND (camera capture → still image)
    // Used by CameraCaptureScreen: vm.sendCapturedImage(context, uri, threadId)
    // -------------------------------------------------------------
    fun sendCapturedImage(context: Context, uri: Uri, threadId: String) {
        // Safety: make sure we are on the correct thread
        if (_selectedThread.value?.id != threadId) {
            // If needed you could call ensureThreadSelected(threadId) here
            // but usually the user is already in that thread.
        }

        viewModelScope.launch {
            val thread = _selectedThread.value
            if (thread == null || thread.id != threadId) {
                Log.w("ChatViewModel", "sendCapturedImage: no matching thread selected")
                return@launch
            }

            try {
                val file = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                } ?: throw Exception("Unable to read captured image")

                val sizeMb = file.size / (1024f * 1024f)

                val plan = _currentPlan.value
                val maxSize = when (plan) {
                    "premium" -> 5f
                    "basic" -> 2f
                    else -> 2f
                }

                if (sizeMb > maxSize) {
                    _errorMessage.value =
                        "Image too large for your plan. Max allowed: ${maxSize}MB"
                    return@launch
                }

                val ref = storage.reference.child(
                    "chat_media/${System.currentTimeMillis()}_camera.jpg"
                )
                ref.putBytes(file).await()
                val url = ref.downloadUrl.await().toString()

                repo.sendMessage(
                    threadId = thread.id,
                    text = "",
                    mediaUrl = url,
                    mediaType = "image"
                )

                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send captured image"
                Log.e("Chat", "sendCapturedImage failed", e)
            }
        }
    }

    // -------------------------------------------------------------
    // REACTIONS
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // DELETE / UNSEND
    // -------------------------------------------------------------
    fun deleteMessageForMe(messageId: String) {
        val threadId = _selectedThread.value?.id ?: return

        viewModelScope.launch {
            try {
                // For now: hard delete from Firestore
                firestore.collection("chatThreads")
                    .document(threadId)
                    .collection("messages")
                    .document(messageId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("ChatViewModel", "deleteMessageForMe failed", e)
            }
        }
    }

    fun unsendMessage(messageId: String) {
        val threadId = _selectedThread.value?.id ?: return

        viewModelScope.launch {
            try {
                // For now: also hard delete (we can later change this to placeholder text)
                firestore.collection("chatThreads")
                    .document(threadId)
                    .collection("messages")
                    .document(messageId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("ChatViewModel", "unsendMessage failed", e)
            }
        }
    }

    // -------------------------------------------------------------
    // SPAM / REPORT (chat-level)
    // -------------------------------------------------------------
    fun reportChat(reason: String?) {
        val thread = _selectedThread.value ?: return
        val reporterId = auth.currentUser?.uid ?: return

        val otherId = when (reporterId) {
            thread.userA -> thread.userB
            thread.userB -> thread.userA
            else -> thread.userB
        }

        viewModelScope.launch {
            try {
                val data = mapOf(
                    "threadId" to thread.id,
                    "reporterId" to reporterId,
                    "reportedUserId" to otherId,
                    "reason" to (reason ?: ""),
                    "createdAt" to Timestamp.now()
                )
                firestore.collection("chat_reports")
                    .add(data)
                    .await()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "reportChat failed", e)
                _errorMessage.value = "Failed to submit report"
            }
        }
    }

    // -------------------------------------------------------------
    // ERROR
    // -------------------------------------------------------------
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        threadsListener?.remove()
        msgListener?.remove()
        typingListener?.remove()
        presenceListener?.remove()
    }
}
