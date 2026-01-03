package com.girlspace.app.ui.friends
import com.girlspace.app.data.friends.FriendScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.friends.FriendRepository
import com.girlspace.app.data.friends.FriendRequestItem
import com.girlspace.app.data.friends.FriendUserSummary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Lightweight model only for Search tab, so we can show email / phone.
 */
data class SearchResultUser(
    val uid: String,
    val fullName: String,
    val email: String?,
    val phone: String?
)

/**
 * UI state for the Friends screen.
 */
data class FriendsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,

    val friends: List<FriendUserSummary> = emptyList(),
    val incomingRequests: List<FriendRequestItem> = emptyList(),
    val suggestions: List<FriendUserSummary> = emptyList(),
    val outgoingRequestIds: Set<String> = emptySet(),

    // 🔹 mutual friend count per suggested user
    val mutualFriendsCounts: Map<String, Int> = emptyMap(),

    // 🔹 following relationship (separate from "friends")
    val followingIds: Set<String> = emptySet(),

    // Search
    val searchQuery: String = "",
    val searchResults: List<SearchResultUser> = emptyList(),
    val isSearchLoading: Boolean = false
)
enum class FriendsListMode {
    FRIENDS,
    FOLLOWERS,
    FOLLOWING
}

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()
    private var scope: FriendScope = FriendScope.PUBLIC

    private var friendsJob: Job? = null
    private var incomingJob: Job? = null
    private var outgoingJob: Job? = null

    private var searchJob: Job? = null
    private var contextUserId: String? = null
    private var contextMode: FriendsListMode = FriendsListMode.FRIENDS
    fun setScope(newScope: FriendScope) {
        if (scope == newScope) return
        scope = newScope
        restartCoreObservers()
    }

    init {
        restartCoreObservers()
        loadSuggestions()
        loadFollowing()
    }
    private fun restartCoreObservers() {
        friendsJob?.cancel()
        incomingJob?.cancel()
        outgoingJob?.cancel()

        friendsJob = viewModelScope.launch { observeFriendsInternal() }
        incomingJob = viewModelScope.launch { observeIncomingInternal() }
        outgoingJob = viewModelScope.launch { observeOutgoingInternal() }
    }

    /**
     * Configure this screen when opened from a profile.
     *
     * @param profileUserId  user whose network we are viewing (null = current user / default)
     * @param initialTabKey  "friends", "followers", or "following" from nav args
     */
    fun configureForProfile(profileUserId: String?, initialTabKey: String?) {
        val mode = when (initialTabKey?.lowercase()) {
            "followers" -> FriendsListMode.FOLLOWERS
            "following" -> FriendsListMode.FOLLOWING
            else -> FriendsListMode.FRIENDS
        }

        val currentUid = auth.currentUser?.uid

        // If we’re opening in the normal “my friends” view, just reset context and let
        // existing observers (observeFriends, observeIncoming, etc.) do their job.
        if (profileUserId.isNullOrBlank() ||
            (profileUserId == currentUid && mode == FriendsListMode.FRIENDS)
        ) {
            contextUserId = null
            contextMode = FriendsListMode.FRIENDS
            return
        }

        // Avoid duplicate re-loads if nothing changed
        if (contextUserId == profileUserId && contextMode == mode) return

        contextUserId = profileUserId
        contextMode = mode

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val targetUid = profileUserId!!

                // Load list according to mode
                val friendsList: List<FriendUserSummary> = when (mode) {
                    FriendsListMode.FRIENDS -> loadFriendsOf(targetUid)
                    FriendsListMode.FOLLOWERS -> loadFollowersOf(targetUid)
                    FriendsListMode.FOLLOWING -> loadFollowingOf(targetUid)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        friends = friendsList
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load network"
                    )
                }
            }
        }
    }

    // region Observers

    private suspend fun observeFriendsInternal() {
        val flow = if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.observeFriends(scope)
        } else {
            friendRepository.observeFriends()
        }

        flow.collect { list ->
            val currentUid = auth.currentUser?.uid
            val shouldApplyMyFriends =
                (contextUserId == null || contextUserId == currentUid) &&
                        contextMode == FriendsListMode.FRIENDS

            if (shouldApplyMyFriends) {
                _uiState.update { it.copy(friends = list) }
                loadSuggestions()
            }
        }
    }



    private suspend fun observeIncomingInternal() {
        val flow = if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.observeIncomingRequests(scope)
        } else {
            friendRepository.observeIncomingRequests()
        }

        flow.collect { list ->
            _uiState.update { it.copy(incomingRequests = list) }
        }
    }

    private suspend fun observeOutgoingInternal() {
        val flow = if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.observeOutgoingRequests(scope)
        } else {
            friendRepository.observeOutgoingRequests()
        }

        flow.collect { ids ->
            _uiState.update { it.copy(outgoingRequestIds = ids) }
        }
    }


    // endregion

    // region Following (for Follow / Unfollow menu)

    private fun loadFollowing() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            try {
                val snap = db.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val followingArray = snap.get("following") as? List<*>
                val followingIds = followingArray
                    ?.filterIsInstance<String>()
                    ?.toSet()
                    ?: emptySet()

                _uiState.update { it.copy(followingIds = followingIds) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = it.error ?: e.message ?: "Failed to load following state")
                }
            }
        }
    }

    fun followUser(targetUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.followUser(targetUid)
                _uiState.update {
                    it.copy(followingIds = it.followingIds + targetUid)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to follow user")
                }
            }
        }
    }

    fun unfollowUser(targetUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.unfollowUser(targetUid)
                _uiState.update {
                    it.copy(followingIds = it.followingIds - targetUid)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to unfollow user")
                }
            }
        }
    }

    // endregion

    // region Suggestions

    /**
     * Load "smart" suggestions:
     *  - all users
     *  - except current user
     *  - except already friends
     *  - except blocked
     *  - compute mutual friends with each candidate
     *  - rank by mutual friends desc
     */
    fun loadSuggestions() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // My friends (for mutual friend intersection)
                val myFriendIds = _uiState.value.friends.map { it.uid }.toSet()

                // Blocked users
                val blockedSnap = db.collection("blocked_users")
                    .document(currentUser.uid)
                    .collection(currentUser.uid)
                    .get()
                    .await()
                val blockedIds = blockedSnap.documents.map { it.id }.toSet()

                // All users (capped for V1)
                val usersSnap = db.collection("users")
                    .limit(200)
                    .get()
                    .await()

                val mutualCounts = mutableMapOf<String, Int>()

                val suggestionsRaw = usersSnap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUser.uid) return@mapNotNull null
                    if (uid in myFriendIds) return@mapNotNull null
                    if (uid in blockedIds) return@mapNotNull null

                    // Compute mutual friends for this candidate
                    val theirFriendsSnap = db.collection("friends")
                        .document(uid)
                        .collection("list")
                        .get()
                        .await()

                    val theirFriendIds = theirFriendsSnap.documents
                        .mapNotNull { it.getString("friendUid") }
                        .toSet()

                    val mutualCount = if (myFriendIds.isEmpty()) {
                        0
                    } else {
                        myFriendIds.intersect(theirFriendIds).size
                    }
                    mutualCounts[uid] = mutualCount

                    FriendUserSummary(
                        uid = uid,
                        fullName = doc.getString("name")
                            ?: doc.getString("fullName")
                            ?: "",
                        photoUrl = doc.getString("photoUrl")
                    )
                }

                // Rank by mutual friends desc, then by name as tiebreaker
                val sortedSuggestions = suggestionsRaw.sortedWith(
                    compareByDescending<FriendUserSummary> { mutualCounts[it.uid] ?: 0 }
                        .thenBy { it.fullName.lowercase() }
                )

                _uiState.update {
                    it.copy(
                        suggestions = sortedSuggestions,
                        mutualFriendsCounts = mutualCounts,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load suggestions"
                    )
                }
            }
        }
    }

    /**
     * Local-only: hide a suggestion after Decline.
     */
    fun hideSuggestion(uid: String) {
        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.filterNot { it.uid == uid }
            )
        }
    }

    // endregion

    // region Actions: friends / requests / block

    fun sendFriendRequest(toUid: String) {
        viewModelScope.launch {
            try {
                if (scope == FriendScope.INNER_CIRCLE) friendRepository.sendFriendRequest(toUid, scope)
                else friendRepository.sendFriendRequest(toUid)
                // Once request is sent, hide from suggestions list
                hideSuggestion(toUid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send request") }
            }
        }
    }

    fun cancelFriendRequest(toUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.cancelFriendRequest(toUid, scope)
        } else {
            friendRepository.cancelFriendRequest(toUid)
        }
    }


    fun acceptFriendRequest(fromUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.acceptFriendRequest(fromUid, scope)
        } else {
            friendRepository.acceptFriendRequest(fromUid)
        }
    }


    fun rejectFriendRequest(fromUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.rejectFriendRequest(fromUid, scope)
        } else {
            friendRepository.rejectFriendRequest(fromUid)
        }
    }


    fun unfriend(friendUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.unfriend(friendUid, scope)
        } else {
            friendRepository.unfriend(friendUid)
        }
    }


    fun blockUser(targetUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.blockUser(targetUid, scope)
        } else {
            friendRepository.blockUser(targetUid)
        }
    }


    fun unblockUser(targetUid: String) = viewModelScope.launch {
        if (scope == FriendScope.INNER_CIRCLE) {
            friendRepository.unblockUser(targetUid, scope)
        } else {
            friendRepository.unblockUser(targetUid)
        }
    }


    // endregion

    // region Search

    fun onSearchQueryChange(newValue: String) {
        _uiState.update { it.copy(searchQuery = newValue) }

        val trimmed = newValue.trim()
        if (trimmed.length < 3) {
            // Clear results until user types 3+ chars
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        // Cancel previous search, start fresh
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(trimmed)
        }
    }
    private suspend fun performSearch(query: String) {
        val currentUser = auth.currentUser ?: return
        _uiState.update { it.copy(isSearchLoading = true, error = null) }

        try {
            val lower = query.lowercase()

            // Load blocked users for current user – we will exclude them from search results
            val blockedSnap = db.collection("blocked_users")
                .document(currentUser.uid)
                .collection(currentUser.uid)
                .get()
                .await()
            val blockedIds = blockedSnap.documents.map { it.id }.toSet()

            // Small scan of users collection; client-side filter on name/email/phone
            val snap = db.collection("users")
                .limit(200)
                .get()
                .await()

            val results = snap.documents.mapNotNull { doc ->
                val uid = doc.id
                if (uid == currentUser.uid) return@mapNotNull null
                if (uid in blockedIds) return@mapNotNull null

                val name = doc.getString("name")
                    ?: doc.getString("fullName")
                    ?: ""
                val email = doc.getString("email") ?: ""
                val phone = doc.getString("phone") ?: ""

                val matches = name.contains(lower, ignoreCase = true) ||
                        email.contains(lower, ignoreCase = true) ||
                        phone.contains(lower, ignoreCase = true)

                if (!matches) return@mapNotNull null

                SearchResultUser(
                    uid = uid,
                    fullName = name.ifBlank { email.ifBlank { phone.ifBlank { uid } } },
                    email = if (email.isBlank()) null else email,
                    phone = if (phone.isBlank()) null else phone
                )
            }

            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearchLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSearchLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

// --- Helpers for profile context ------------------------------------------------------

    private suspend fun loadFriendsOf(userId: String): List<FriendUserSummary> {
        // Basic block list for current user (if logged in)
        val currentUid = auth.currentUser?.uid
        val blockedIds: Set<String> = if (currentUid != null) {
            try {
                val snap = db.collection("blocked_users")
                    .document(currentUid)
                    .collection(currentUid)
                    .get()
                    .await()
                snap.documents.map { it.id }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        val friendsSnap = db.collection("friends")
            .document(userId)
            .collection("list")
            .orderBy("lastInteractionAt")
            .get()
            .await()

        val friendIds = friendsSnap.documents
            .mapNotNull { it.getString("friendUid") }
            .filter { it !in blockedIds }

        if (friendIds.isEmpty()) return emptyList()

        val result = mutableListOf<FriendUserSummary>()
        for (friendId in friendIds) {
            try {
                val userSnap = db.collection("users")
                    .document(friendId)
                    .get()
                    .await()
                if (!userSnap.exists()) continue

                result += FriendUserSummary(
                    uid = friendId,
                    fullName = userSnap.getString("name")
                        ?: userSnap.getString("fullName")
                        ?: "",
                    photoUrl = userSnap.getString("photoUrl")
                )
            } catch (_: Exception) {
                // ignore individual failures
            }
        }
        return result
    }

    private suspend fun loadFollowersOf(userId: String): List<FriendUserSummary> {
        val currentUid = auth.currentUser?.uid
        val blockedIds: Set<String> = if (currentUid != null) {
            try {
                val snap = db.collection("blocked_users")
                    .document(currentUid)
                    .collection(currentUid)
                    .get()
                    .await()
                snap.documents.map { it.id }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        val followersSnap = db.collection("users")
            .whereArrayContains("following", userId)
            .limit(200)
            .get()
            .await()

        return followersSnap.documents
            .filter { doc ->
                val uid = doc.id
                uid != currentUid && uid !in blockedIds
            }
            .map { doc ->
                FriendUserSummary(
                    uid = doc.id,
                    fullName = doc.getString("name")
                        ?: doc.getString("fullName")
                        ?: "",
                    photoUrl = doc.getString("photoUrl")
                )
            }
    }

    private suspend fun loadFollowingOf(userId: String): List<FriendUserSummary> {
        val currentUid = auth.currentUser?.uid
        val blockedIds: Set<String> = if (currentUid != null) {
            try {
                val snap = db.collection("blocked_users")
                    .document(currentUid)
                    .collection(currentUid)
                    .get()
                    .await()
                snap.documents.map { it.id }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        val userSnap = db.collection("users")
            .document(userId)
            .get()
            .await()

        val followingArray = userSnap.get("following") as? List<*>
        val followingIds = followingArray
            ?.filterIsInstance<String>()
            ?.filter { it != currentUid && it !in blockedIds }
            ?: emptyList()

        if (followingIds.isEmpty()) return emptyList()

        val result = mutableListOf<FriendUserSummary>()
        for (followedId in followingIds) {
            try {
                val snap = db.collection("users")
                    .document(followedId)
                    .get()
                    .await()
                if (!snap.exists()) continue

                result += FriendUserSummary(
                    uid = followedId,
                    fullName = snap.getString("name")
                        ?: snap.getString("fullName")
                        ?: "",
                    photoUrl = snap.getString("photoUrl")
                )
            } catch (_: Exception) {
                // ignore individual failures
            }
        }
        return result
    }


    // endregion
}
