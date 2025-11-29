package com.girlspace.app.ui.friends

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

    // Search
    val searchQuery: String = "",
    val searchResults: List<SearchResultUser> = emptyList(),
    val isSearchLoading: Boolean = false
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeFriends()
        observeIncoming()
        observeOutgoing()
        loadSuggestions()
    }

    // region Observers

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { list ->
                _uiState.update { it.copy(friends = list) }
            }
        }
    }

    private fun observeIncoming() {
        viewModelScope.launch {
            friendRepository.observeIncomingRequests().collect { list ->
                _uiState.update { it.copy(incomingRequests = list) }
            }
        }
    }

    private fun observeOutgoing() {
        viewModelScope.launch {
            friendRepository.observeOutgoingRequests().collect { ids ->
                _uiState.update { it.copy(outgoingRequestIds = ids) }
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
     */
    fun loadSuggestions() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Current friends
                val friendIds = _uiState.value.friends.map { it.uid }.toSet()

                // Blocked users
                val blockedSnap = db.collection("blocked_users")
                    .document(currentUser.uid)
                    .collection(currentUser.uid)
                    .get()
                    .await()
                val blockedIds = blockedSnap.documents.map { it.id }.toSet()

                // All users (small limit is fine for V1)
                val usersSnap = db.collection("users")
                    .limit(200)
                    .get()
                    .await()

                val suggestions = usersSnap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUser.uid) return@mapNotNull null
                    if (uid in friendIds) return@mapNotNull null
                    if (uid in blockedIds) return@mapNotNull null

                    FriendUserSummary(
                        uid = uid,
                        fullName = doc.getString("name")
                            ?: doc.getString("fullName")
                            ?: "",
                        photoUrl = doc.getString("photoUrl")
                    )
                }

                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
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
                friendRepository.sendFriendRequest(toUid)
                // Once request is sent, hide from suggestions list
                hideSuggestion(toUid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send request") }
            }
        }
    }

    fun cancelFriendRequest(toUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.cancelFriendRequest(toUid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to cancel request") }
            }
        }
    }

    fun acceptFriendRequest(fromUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.acceptFriendRequest(fromUid)
                // After accepting, reload suggestions (friend set changed)
                loadSuggestions()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to accept request") }
            }
        }
    }

    fun rejectFriendRequest(fromUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.rejectFriendRequest(fromUid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to reject request") }
            }
        }
    }

    fun unfriend(friendUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.unfriend(friendUid)
                loadSuggestions()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to unfriend") }
            }
        }
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.blockUser(targetUid)
                // Remove from friends + suggestions instantly
                hideSuggestion(targetUid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to block user") }
            }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                friendRepository.unblockUser(targetUid)
                loadSuggestions()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to unblock user") }
            }
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

            // Small scan of users collection; client-side filter on name/email/phone
            val snap = db.collection("users")
                .limit(200)
                .get()
                .await()

            val results = snap.documents.mapNotNull { doc ->
                val uid = doc.id
                if (uid == currentUser.uid) return@mapNotNull null

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

    // endregion
}
