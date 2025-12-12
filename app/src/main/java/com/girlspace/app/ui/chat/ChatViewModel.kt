package com.girlspace.app.ui.chat

// GirlSpace – ChatViewModel.kt
// Version: v1.3.2 – Delete-for-everyone: optimistic local soft-delete for text & media

import kotlinx.coroutines.flow.asStateFlow

import androidx.lifecycle.viewModelScope
import com.girlspace.app.moderation.ModerationService
import com.girlspace.app.moderation.ContentKind
import com.girlspace.app.moderation.ModerationManager
import com.girlspace.app.moderation.ModerationSendState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.stateIn
import java.io.File
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.SharingStarted
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val messagesRef = firestore.collection("chat_messages")
    // --- MODERATION SUPPORT ---


    private val moderationManager = ModerationManager()

    private val _sendState = MutableStateFlow<ModerationSendState>(ModerationSendState.Idle)
    val sendState: StateFlow<ModerationSendState> = _sendState

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val repo = ChatRepository(
        auth = auth,
        firestore = firestore
    )
    private fun Any?.toMillisSafe(): Long {
        return when (this) {
            is Long -> this
            is Int -> this.toLong()
            is Double -> this.toLong()
            is Float -> this.toLong()
            is Timestamp -> this.toDate().time
            is java.util.Date -> this.time
            else -> 0L
        }
    }

    private var threadsListener: ListenerRegistration? = null
    private var msgListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    // Listeners for pinned messages & blocked users
    private var pinnedListener: ListenerRegistration? = null
    private var blockedListener: ListenerRegistration? = null

    // Threads
    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads

    private val _filteredThreads = MutableStateFlow<List<ChatThread>>(emptyList())
    val filteredThreads: StateFlow<List<ChatThread>> = _filteredThreads

    private val _selectedThread = MutableStateFlow<ChatThread?>(null)
    val selectedThread: StateFlow<ChatThread?> = _selectedThread

    private val _scrollToMessageId = MutableStateFlow<String?>(null)
    val scrollToMessageId: StateFlow<String?> = _scrollToMessageId

    private val _highlightMessageId = MutableStateFlow<String?>(null)
    val highlightMessageId: StateFlow<String?> = _highlightMessageId

    // Messages (with simple pagination)
    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    // --- PINNED MESSAGES STATE ---
    private val _pinnedIds = MutableStateFlow<List<String>>(emptyList())
    val pinnedIds: StateFlow<List<String>> = _pinnedIds

    val pinnedMessages: StateFlow<List<ChatMessage>> =
        combine(
            messages,          // StateFlow<List<ChatMessage>>
            _pinnedIds         // StateFlow<List<String>>
        ) { allMessages: List<ChatMessage>, ids: List<String> ->
            allMessages.filter { msg -> ids.contains(msg.id) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // --- BLOCKED USERS STATE ---
    private val _blockedUsers = MutableStateFlow<List<String>>(emptyList())
    val blockedUsers: StateFlow<List<String>> = _blockedUsers

    // Other participant in this chat – adapt to your own source
    private val _otherUserId = MutableStateFlow<String?>(null)
    val otherUserId: StateFlow<String?> = _otherUserId

    val isBlockedThisChat: StateFlow<Boolean> = combine(
        _blockedUsers,
        _otherUserId
    ) { blocked, otherId ->
        otherId != null && blocked.contains(otherId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- EMOJI PANEL STATE FOR COMPOSER ---
    private val _showComposerEmoji = MutableStateFlow(false)
    val showComposerEmoji: StateFlow<Boolean> = _showComposerEmoji

    private var loadedMessageCount: Int = 30
    private val pageSize: Int = 30

    // locally "deleted for me" message ids (per device only)
    private val _locallyDeletedIds = MutableStateFlow<Set<String>>(emptySet())

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
        listenMutedThreads()

        viewModelScope.launch {
            markUserActive()
        }
    }
    // -------------------------------------------------------------
// UNREAD + NOTIFICATION SYNC (WhatsApp style)
// -------------------------------------------------------------
    private fun markThreadAsRead(threadId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("chatThreads")
            .document(threadId)
            .update("unread_$uid", 0L)
            .addOnFailureListener { e ->
                Log.w("ChatViewModel", "markThreadAsRead failed", e)
            }
    }

    private fun markChatNotificationsRead(threadId: String) {
        val uid = auth.currentUser?.uid ?: return
        val deepLink = "togetherly://chat/$threadId"

        firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .whereEqualTo("deepLink", deepLink)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { doc ->
                    doc.reference.update("read", true)
                }
            }
            .addOnFailureListener { e ->
                Log.w("ChatViewModel", "markChatNotificationsRead failed", e)
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

    private fun listenMutedThreads() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("mutedThreads")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w("ChatViewModel", "listenMutedThreads error", error)
                    return@addSnapshotListener
                }

                val ids = snap?.documents
                    ?.filter { it.getBoolean("muted") != false }
                    ?.map { it.id }
                    ?.toSet()
                    ?: emptySet()

                _mutedThreadIds.value = ids
            }
    }

    fun setThreadMuted(threadId: String, muted: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val ref = firestore.collection("users")
            .document(uid)
            .collection("mutedThreads")
            .document(threadId)

        if (muted) {
            ref.set(
                mapOf(
                    "muted" to true,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).addOnFailureListener { e ->
                Log.w("ChatViewModel", "setThreadMuted(true) failed", e)
            }
        } else {
            // delete doc to keep it clean
            ref.delete().addOnFailureListener { e ->
                Log.w("ChatViewModel", "setThreadMuted(false) failed", e)
            }
        }
    }
    private fun markThreadReadEverywhere(threadId: String) {
        val uid = currentUserId ?: return

        viewModelScope.launch {
            // 1) Clear WhatsApp-style unread count in chatThreads
            repo.markThreadRead(threadId)

            // 2) Mark bell CHAT notifications as read for this thread
            try {
                val notifCol = firestore.collection("users")
                    .document(uid)
                    .collection("notifications")

                // Prefer explicit fields if present
                val q1 = notifCol
                    .whereEqualTo("read", false)
                    .whereEqualTo("category", "CHAT")
                    .whereEqualTo("threadId", threadId)
                    .get()
                    .await()

                // Backward compatibility (your old docs used entityId)
                val q2 = notifCol
                    .whereEqualTo("read", false)
                    .whereEqualTo("category", "CHAT")
                    .whereEqualTo("entityId", threadId)
                    .get()
                    .await()

                val docs = (q1.documents + q2.documents)
                    .distinctBy { it.id }
                    .take(200) // safety

                if (docs.isNotEmpty()) {
                    val batch = firestore.batch()
                    docs.forEach { d ->
                        batch.update(d.reference, "read", true)
                    }
                    batch.commit().await()
                }
            } catch (_: Exception) {
                // fail-open (don’t crash UI)
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
                val users = firestore.collection("users")
                val meRef = users.document(uid)

                val meDoc = meRef.get().await()

                // 1) Old "following" based model (backwards compat)
                val followingArray = meDoc.get("following") as? List<*>
                val followingIds = followingArray
                    ?.filterIsInstance<String>()
                    ?: emptyList()

                // 2) New "friends" array, if your Friends screen uses this
                val friendsArray = meDoc.get("friends") as? List<*>
                val friendsIdsFromArray = friendsArray
                    ?.filterIsInstance<String>()
                    ?: emptyList()

                // 3) Optional: users/{uid}/friends subcollection (older model)
                val friendsSubDocs = try {
                    meRef.collection("friends").get().await()
                } catch (_: Exception) {
                    null
                }

                val friendsIdsFromSubcollection = friendsSubDocs?.documents
                    ?.mapNotNull { doc ->
                        // either store friendId field or use doc.id
                        doc.getString("friendId") ?: doc.id
                    }
                    ?: emptyList()

                // 4) ✅ NEW: friends/{uid}/list subcollection (current Friends implementation)
                val friendsRootDocs = try {
                    firestore.collection("friends")
                        .document(uid)
                        .collection("list")
                        .get()
                        .await()
                } catch (_: Exception) {
                    null
                }

                val friendsIdsFromRootList = friendsRootDocs?.documents
                    ?.mapNotNull { doc ->
                        // prefer friendUid field, fall back to uid/doc.id
                        doc.getString("friendUid")
                            ?: doc.getString("uid")
                            ?: doc.id
                    }
                    ?: emptyList()

                // 🔹 Union of all ways we know friendships
                val allFriendIds = (
                        followingIds +
                                friendsIdsFromArray +
                                friendsIdsFromSubcollection +
                                friendsIdsFromRootList
                        )
                    .filter { it.isNotBlank() && it != uid }
                    .toSet()

                // -----------------------------
                // FRIENDS ROW (horizontal chips)
                // -----------------------------
                val friendSummaries = mutableListOf<ChatUserSummary>()
                for (fid in allFriendIds) {
                    val userDoc = users.document(fid).get().await()
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

                // -----------------------------
                // SUGGESTIONS (friends-of-friends)
                // -----------------------------
                val suggestionCounts = mutableMapOf<String, Int>()

                for (fid in allFriendIds) {
                    val friendDoc = users.document(fid).get().await()

                    val friendFollowingArray = friendDoc.get("following") as? List<*>
                    val friendFollowingIds = friendFollowingArray
                        ?.filterIsInstance<String>()
                        ?: emptyList()

                    friendFollowingIds.forEach { otherId ->
                        if (otherId == uid) return@forEach
                        if (allFriendIds.contains(otherId)) return@forEach

                        val current = suggestionCounts[otherId] ?: 0
                        suggestionCounts[otherId] = current + 1
                    }
                }

                val suggestionsList = mutableListOf<ChatUserSummary>()
                suggestionCounts.entries
                    .sortedByDescending { it.value }
                    .take(20)
                    .forEach { (otherId, mutualCount) ->
                        val userDoc = users.document(otherId).get().await()
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
                    .firstOrNull { doc ->
                        val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
                        // ✅ Only treat as 1-1 if exactly 2 participants
                        participants.size == 2
                    }


                val thread: ChatThread = if (existingDoc != null) {
                    ChatThread(
                        id = existingDoc.id,
                        userA = existingDoc.getString("userA") ?: "",
                        userB = existingDoc.getString("userB") ?: "",
                        userAName = existingDoc.getString("userAName") ?: "",
                        userBName = existingDoc.getString("userBName") ?: "",
                        lastMessage = existingDoc.getString("lastMessage") ?: "",
                        lastTimestamp = existingDoc.get("lastTimestamp").toMillisSafe(),
                        unreadCount = (existingDoc.get("unread_$myUid") as? Long)?.toInt() ?: 0
                    )
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
    // Ensure correct thread selected when entering via route "chat/{threadId}"
    fun ensureThreadSelected(threadId: String) {
        val current = _selectedThread.value
        if (current?.id == threadId) return

        // 🔄 Clear old chat UI so previous conversation doesn't flash
        _allMessages.value = emptyList()
        _messages.value = emptyList()
        _errorMessage.value = null
        _inputText.value = ""
        _isTyping.value = false

        val fromList = _threads.value.firstOrNull { it.id == threadId }
        if (fromList != null) {
            // We already know this thread -> select it
            selectThread(fromList)
        } else {
            // Not in local list -> fetch from Firestore
            viewModelScope.launch {
                try {
                    val snap = firestore.collection("chatThreads")
                        .document(threadId)
                        .get()
                        .await()
                    if (snap.exists()) {
                        val thread = ChatThread(
                            id = threadId,
                            userA = snap.getString("userA") ?: "",
                            userB = snap.getString("userB") ?: "",
                            userAName = snap.getString("userAName") ?: "",
                            userBName = snap.getString("userBName") ?: "",
                            lastMessage = snap.getString("lastMessage") ?: "",
                            lastTimestamp = snap.get("lastTimestamp").toMillisSafe(),
                            unreadCount = (snap.get("unread_${auth.currentUser?.uid ?: ""}") as? Long)?.toInt() ?: 0
                        )
                        selectThread(thread)
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
        markThreadAsRead(thread.id)
        markChatNotificationsRead(thread.id)
        val myId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // 1) Reset unread counter for THIS thread for me (drives ChatsScreen badges)
                firestore.collection("chatThreads")
                    .document(thread.id)
                    .update("unread_$myId", 0L)

                // 2) Mark bell notifications for THIS thread as read
                // users/{uid}/notifications where entityId == threadId and read == false
                val snap = firestore.collection("users")
                    .document(myId)
                    .collection("notifications")
                    .whereEqualTo("entityId", thread.id)
                    .whereEqualTo("read", false)
                    .get()
                    .await()

                val batch = firestore.batch()
                snap.documents.forEach { d -> batch.update(d.reference, "read", true) }
                batch.commit().await()

            } catch (_: Exception) { }
        }

        // -----------------------------------------
        // -----------------------------------------
        // 1) LISTEN FOR MESSAGES IN THIS THREAD
        // -----------------------------------------
        msgListener?.remove()
        msgListener = repo.observeMessages(threadId = thread.id) { incoming ->
            val uid = currentUserId

            // Merge incoming snapshot with our existing local state so that
            // optimistic delete flags (deletedFor / deletedForAll) are not lost
            val localMap = _allMessages.value.associateBy { it.id }

            val merged = incoming.map { msgFromServer ->
                val local = localMap[msgFromServer.id]
                if (local != null) {
                    msgFromServer.copy(
                        // union of deletedFor lists
                        deletedFor = (msgFromServer.deletedFor + local.deletedFor).distinct(),
                        // once true, stays true
                        deletedForAll = msgFromServer.deletedForAll || local.deletedForAll
                    )
                } else {
                    msgFromServer
                }
            }

            _allMessages.value = merged

            // Recompute visible list using our central filter
            recomputeVisibleMessages()

            // Mark only the currently visible messages as read
            markMessagesAsReadForCurrentUser(msgs = _messages.value)
        }


        // Reset typing state for this thread
        _isTyping.value = false
        lastTypingSent = false

        // Figure out "other" user in a 1:1 thread
        val myUid = auth.currentUser?.uid
        val otherUid = when (myUid) {
            null -> null
            thread.userA -> thread.userB
            else -> thread.userA
        }

        // -----------------------------------------
        // 2) TYPING LISTENER
        // -----------------------------------------
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
                    val cutoff = now - 8_000L  // 8 seconds typing window

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

        // -----------------------------------------
        // 3) PRESENCE LISTENER (ONLINE / LAST SEEN)
        // -----------------------------------------
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
                    val onlineThreshold = 90_000L // 90 sec

                    val online = now - lastActive <= onlineThreshold
                    _isOtherOnline.value = online

                    if (!online) {
                        val diffMinutes = ((now - lastActive) / 60_000L).coerceAtLeast(1L)

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

        // Mark *me* active in presence collection
        viewModelScope.launch {
            markUserActive()
        }
    }

    private fun startPinnedAndBlockedListeners(threadId: String) {
        val uid = auth.currentUser?.uid ?: return

        // Listen for pinned message ids on chatThreads/{threadId}
        pinnedListener?.remove()
        pinnedListener = firestore.collection("chatThreads")
            .document(threadId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val pinned = (snapshot.get("pinnedMessageIds") as? List<String>).orEmpty()
                _pinnedIds.value = pinned
            }

        // Listen for this user's blocked list on users/{uid}
        blockedListener?.remove()
        blockedListener = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val blocked = (snapshot.get("blockedUsers") as? List<String>).orEmpty()
                _blockedUsers.value = blocked
            }
    }
    private val _mutedThreadIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedThreadIds: StateFlow<Set<String>> = _mutedThreadIds

    // Pagination: load older messages
    fun loadMoreMessages() {
        val all = _allMessages.value
        if (all.isEmpty()) return

        // Increase page size but don't exceed total
        loadedMessageCount = (loadedMessageCount + pageSize)
            .coerceAtMost(all.size)

        // Always derive visible list from the central helper
        recomputeVisibleMessages()
    }

    // Mark messages as read for ticks
    // Mark messages as read for ticks
// Mark messages as read for ticks
    private fun markMessagesAsReadForCurrentUser(msgs: List<ChatMessage>) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                msgs
                    .filter { it.senderId != uid && !it.readBy.contains(uid) }
                    .forEach { msg ->
                        // ✅ Update the top-level chat_messages doc
                        messagesRef
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
    fun sendMessageInternal() {
        val thread = _selectedThread.value ?: return
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        if (_isSending.value) return

        viewModelScope.launch {
            try {
                _isSending.value = true

                // 🔹 Run hybrid moderation: on-device + pending_content write
                val result = moderationManager.submitTextForModeration(
                    rawText = text,
                    kind = ContentKind.CHAT_MESSAGE,
                    contextId = thread.id
                )

                when {
                    // ❌ Hard-blocked locally (bad words etc.)
                    result.blockedLocally -> {
                        _errorMessage.value =
                            result.message ?: "Message blocked by community guidelines."
                        return@launch
                    }

                    // ✅ Successfully written to pending_content; CF will handle the rest
                    result.success -> {
                        // Clear input, reset typing, mark user active
                        _inputText.value = ""
                        updateTypingState()
                        markUserActive()

                        // Optional: you could set some "pending" UI flag here if you want
                    }

                    // ❌ Failed to write to pending_content (network / permissions etc.)
                    else -> {
                        _errorMessage.value =
                            result.message ?: "Failed to send message. Please try again."
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("Chat", "sendMessage failed", e)
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendMessage() = sendMessageInternal()

    // -------------------------------------------------------------
    // MEDIA SEND (gallery / file picker / voice notes)
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
    // SEND CONTACT MESSAGE
    // -------------------------------------------------------------
    fun sendContactMessage(
        name: String,
        phones: List<String>,
        email: String?
    ) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val rawMap: Map<String, Any?> = mapOf(
                    "name" to name,
                    "phones" to phones,
                    "email" to email
                )

                @Suppress("UNCHECKED_CAST")
                val contactMap = rawMap
                    .filterValues { it != null } as Map<String, Any>

                repo.sendMessage(
                    threadId = thread.id,
                    text = name,            // ✅ text now holds contact name
                    mediaType = "contact",  // ✅ special type
                    mediaUrl = null,
                    extra = contactMap
                )

                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send contact"
            }
        }
    }

    // -------------------------------------------------------------
    // SEND LOCATION MESSAGE
    // -------------------------------------------------------------
    fun sendLocationMessage(
        lat: Double,
        lng: Double,
        address: String,
        isLive: Boolean
    ) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val payload = mapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "address" to address,
                    "isLive" to isLive
                )

                repo.sendMessage(
                    threadId = thread.id,
                    text = if (isLive) "Live Location" else address,
                    mediaType = if (isLive) "live_location" else "location",
                    mediaUrl = null,
                    extra = payload
                )

                markUserActive()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to send location"
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
    // PIN / UNPIN
    // -------------------------------------------------------------
    fun pinMessage(messageId: String) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val threadRef = firestore.collection("chatThreads")
                    .document(thread.id)

                firestore.runTransaction { tx ->
                    val snap = tx.get(threadRef)
                    val current = (snap.get("pinnedMessageIds") as? List<String>)
                        .orEmpty()
                        .toMutableList()

                    if (!current.contains(messageId)) {
                        current.add(messageId)
                    }

                    tx.update(threadRef, "pinnedMessageIds", current)
                }.await()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "pinMessage failed", e)
            }
        }
    }

    fun unpinMessage(messageId: String) {
        val thread = _selectedThread.value ?: return

        viewModelScope.launch {
            try {
                val threadRef = firestore.collection("chatThreads")
                    .document(thread.id)

                firestore.runTransaction { tx ->
                    val snap = tx.get(threadRef)
                    val current = (snap.get("pinnedMessageIds") as? List<String>)
                        .orEmpty()
                        .toMutableList()

                    current.remove(messageId)

                    tx.update(threadRef, "pinnedMessageIds", current)
                }.await()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "unpinMessage failed", e)
            }
        }
    }

    // ----------------------------------------------------------------------
    // Deletion helpers
    // ----------------------------------------------------------------------

    /**
     * Recompute which messages are visible for the current user.
     *
     * Filters out:
     *  - messages deleted for everyone
     *  - messages deleted for this user (deletedFor contains uid)
     *  - any locally deleted ids (in _locallyDeletedIds)
     */
    private fun recomputeVisibleMessages() {
        val uid = currentUserId ?: return
        val deletedLocalIds = _locallyDeletedIds.value

        val visible = _allMessages.value
            .takeLast(loadedMessageCount)
            .filter { msg ->
                !msg.deletedForAll &&
                        uid !in msg.deletedFor &&
                        msg.id !in deletedLocalIds
            }

        _messages.value = visible
    }

    /**
     * Delete a message only for the current user.
     *
     * - Locally: mark in _allMessages as deletedFor += uid
     * - Remotely: add uid to deletedFor in Firestore
     * - UI is always derived from recomputeVisibleMessages()
     */
    fun deleteMessageForMe(messageId: String) {
        val uid = auth.currentUser?.uid ?: return

        // ✅ Optimistic local update: add uid to deletedFor for that message
        val updatedAll = _allMessages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    deletedFor = (msg.deletedFor + uid).distinct()
                )
            } else {
                msg
            }
        }
        _allMessages.value = updatedAll
        recomputeVisibleMessages()

        // 🔁 Persist in Firestore on chat_messages/{messageId}
        viewModelScope.launch {
            try {
                val docRef = messagesRef.document(messageId)

                val snap = docRef.get().await()
                if (!snap.exists()) {
                    Log.w(
                        "ChatViewModel",
                        "deleteMessageForMe: message already deleted in Firestore"
                    )
                    return@launch
                }

                docRef.update(
                    "deletedFor",
                    FieldValue.arrayUnion(uid)
                ).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    Log.w(
                        "ChatViewModel",
                        "deleteMessageForMe: NOT_FOUND, treating as deleted",
                        e
                    )
                } else {
                    Log.e("ChatViewModel", "deleteMessageForMe Firestore error", e)
                    _errorMessage.value = e.message
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "deleteMessageForMe failed", e)
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Delete for everyone (unsend):
     * - Soft delete in Firestore: mark deletedForAll = true, clear media fields.
     * - Try deleting the media file from Storage (if any).
     * - ALSO update local _allMessages/_messages immediately so UI changes instantly.
     */
    fun unsendMessage(messageId: String) {
        // ✅ Optimistic local update so UI hides it immediately for everyone
        val updatedAll = _allMessages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    text = "This message was deleted",
                    mediaUrl = null,
                    mediaType = null,
                    deletedForAll = true
                )
            } else msg
        }
        _allMessages.value = updatedAll
        recomputeVisibleMessages()

        // 🔁 Remote Firestore + Storage update on chat_messages/{messageId}
        viewModelScope.launch {
            try {
                val docRef = messagesRef.document(messageId)

                val snap = docRef.get().await()
                if (!snap.exists()) {
                    Log.w("ChatViewModel", "unsendMessage: message not found in Firestore")
                    return@launch
                }

                val mediaUrl = snap.getString("mediaUrl")

                // 1️⃣ Mark deleted for everyone in Firestore
                docRef.update(
                    mapOf(
                        "text" to "This message was deleted",
                        "mediaUrl" to null,
                        "mediaType" to null,
                        "deletedForAll" to true
                    )
                ).await()

                // 2️⃣ Try removing media from Storage
                if (!mediaUrl.isNullOrBlank()) {
                    try {
                        val ref = storage.getReferenceFromUrl(mediaUrl)
                        ref.delete().await()
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "Failed to delete media from storage", e)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("ChatViewModel", "unsendMessage failed", e)
            }
        }
    }

    fun requestScrollTo(messageId: String) {
        _scrollToMessageId.value = messageId
    }

    fun clearScrollRequest() {
        _scrollToMessageId.value = null
    }

    fun highlightMessage(messageId: String) {
        _highlightMessageId.value = messageId
        // auto-clear after 1.5 sec
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _highlightMessageId.value = null
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