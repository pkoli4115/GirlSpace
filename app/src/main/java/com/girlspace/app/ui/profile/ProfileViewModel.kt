package com.girlspace.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the GirlSpace ProfileScreen.
 * - Keeps UI state in a single StateFlow
 * - Fetches data in parallel for better performance
 * - Emits navigation / one-off events via SharedFlow
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.LoadProfile -> loadProfile(action.userId, isRefresh = false)
            ProfileAction.Refresh -> {
                val id = _uiState.value.profileUserId ?: return
                loadProfile(id, isRefresh = true)
            }

            is ProfileAction.OnTabSelected -> selectTab(action.tab)

            ProfileAction.OnFollowClicked -> onFollowClicked()
            ProfileAction.OnMessageClicked -> onMessageClicked()
            ProfileAction.OnAddToGroupClicked -> onAddToGroupClicked()

            ProfileAction.OnEditProfileClicked -> onEditProfileClicked()
            ProfileAction.OnSettingsClicked -> onSettingsClicked()
            ProfileAction.OnShareProfileClicked -> onShareProfileClicked()

            ProfileAction.OnReportClicked -> onReportClicked()
            is ProfileAction.SubmitReport -> submitReport(action.reason, action.details)
            ProfileAction.OnBlockClicked -> showBlockConfirm()
            ProfileAction.ConfirmBlockUser -> confirmBlock()
            ProfileAction.DismissBlockConfirm -> hideBlockConfirm()

            ProfileAction.ClearMessage -> clearMessages()
        }
    }

    /**
     * Load profile data.
     * If isRefresh = true → uses isRefreshing flag instead of isLoading.
     */
    private fun loadProfile(userId: String, isRefresh: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isLoading = if (!isRefresh) true else current.isLoading,
                isRefreshing = isRefresh,
                errorMessage = null,
                profileUserId = userId
            )

            try {
                coroutineScope {
                    val currentUserIdDeferred = async { repository.getCurrentUserId() }

                    // Parallel profile + stats + content fetches
                    val profileDeferred = async { repository.loadUserProfile(userId) }
                    val statsDeferred = async { repository.loadProfileStats(userId) }

                    val currentUserId = currentUserIdDeferred.await()
                    val mode = if (currentUserId != null && currentUserId == userId) {
                        ProfileMode.SELF
                    } else {
                        ProfileMode.OTHER
                    }

                    val relationshipDeferred = async {
                        if (currentUserId != null && mode == ProfileMode.OTHER) {
                            repository.loadRelationshipStatus(currentUserId, userId)
                        } else {
                            RelationshipStatus.MUTUALS // self or not applicable
                        }
                    }

                    val pinnedDeferred = async { repository.loadPinnedPosts(userId) }
                    val postsDeferred = async { repository.loadPosts(userId) }
                    val reelsDeferred = async { repository.loadReels(userId) }
                    val photosDeferred = async { repository.loadPhotos(userId) }

                    val sharedMediaDeferred = async {
                        if (mode == ProfileMode.OTHER && currentUserId != null) {
                            repository.loadSharedMediaIds(currentUserId, userId)
                        } else emptyList()
                    }

                    val sharedGroupsDeferred = async {
                        if (mode == ProfileMode.OTHER && currentUserId != null) {
                            repository.loadSharedGroupsPreview(currentUserId, userId)
                        } else emptyList()
                    }

                    val profile = profileDeferred.await()
                    val stats = statsDeferred.await()
                    val relationship = relationshipDeferred.await()
                    val pinned = pinnedDeferred.await()
                    val posts = postsDeferred.await()
                    val reels = reelsDeferred.await()
                    val photos = photosDeferred.await()
                    val sharedMedia = sharedMediaDeferred.await()
                    val sharedGroups = sharedGroupsDeferred.await()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        mode = mode,
                        currentUserId = currentUserId,
                        userProfile = profile,
                        profileStats = stats,
                        relationshipStatus = relationship,
                        pinnedPosts = pinned,
                        postIds = posts,
                        reelIds = reels,
                        photoMediaIds = photos,
                        sharedMediaIds = sharedMedia,
                        sharedGroupsPreview = sharedGroups
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.message ?: "Something went wrong loading profile"
                )
            }
        }
    }

    private fun selectTab(tab: ProfileTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    private fun onFollowClicked() {
        val state = _uiState.value
        val currentUserId = state.currentUserId ?: return
        val targetUserId = state.profileUserId ?: return
        if (state.mode != ProfileMode.OTHER) return

        viewModelScope.launch {
            try {
                val newStatus = repository.toggleFollow(currentUserId, targetUserId)
                val message = when (newStatus) {
                    RelationshipStatus.FOLLOWING,
                    RelationshipStatus.MUTUALS -> "You’re now following this user"
                    else -> "You unfollowed this user"
                }
                _uiState.value = _uiState.value.copy(
                    relationshipStatus = newStatus,
                    infoMessage = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Could not update follow status"
                )
            }
        }
    }

    private fun onMessageClicked() {
        val targetUserId = _uiState.value.profileUserId ?: return
        viewModelScope.launch {
            _events.emit(ProfileEvent.NavigateToChat(targetUserId))
        }
    }

    private fun onAddToGroupClicked() {
        val targetUserId = _uiState.value.profileUserId ?: return
        viewModelScope.launch {
            _events.emit(ProfileEvent.OpenAddToGroupPicker(targetUserId))
        }
    }

    private fun onEditProfileClicked() {
        viewModelScope.launch {
            _events.emit(ProfileEvent.NavigateToEditProfile)
        }
    }

    private fun onSettingsClicked() {
        viewModelScope.launch {
            _events.emit(ProfileEvent.NavigateToSettings)
        }
    }

    private fun onShareProfileClicked() {
        val targetUserId = _uiState.value.profileUserId ?: return
        viewModelScope.launch {
            _events.emit(ProfileEvent.ShareProfile(targetUserId))
        }
    }

    private fun onReportClicked() {
        _uiState.value = _uiState.value.copy(showReportSheet = true)
    }

    private fun submitReport(reason: String, details: String?) {
        val reporterId = _uiState.value.currentUserId ?: return
        val targetId = _uiState.value.profileUserId ?: return

        viewModelScope.launch {
            try {
                repository.reportUser(reporterId, targetId, reason, details)
                _uiState.value = _uiState.value.copy(
                    showReportSheet = false,
                    infoMessage = "Report submitted"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Could not submit report"
                )
            }
        }
    }

    private fun showBlockConfirm() {
        _uiState.value = _uiState.value.copy(showBlockConfirmDialog = true)
    }

    private fun hideBlockConfirm() {
        _uiState.value = _uiState.value.copy(showBlockConfirmDialog = false)
    }

    private fun confirmBlock() {
        val currentUserId = _uiState.value.currentUserId ?: return
        val targetUserId = _uiState.value.profileUserId ?: return

        viewModelScope.launch {
            try {
                repository.blockUser(currentUserId, targetUserId)
                _uiState.value = _uiState.value.copy(
                    relationshipStatus = RelationshipStatus.BLOCKED,
                    showBlockConfirmDialog = false,
                    infoMessage = "User blocked"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Could not block user"
                )
            }
        }
    }

    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            infoMessage = null
        )
    }
}

/**
 * One-off events for navigation / share / dialogs that should NOT survive process death.
 */
sealed interface ProfileEvent {
    data class NavigateToChat(val userId: String) : ProfileEvent
    data class OpenAddToGroupPicker(val targetUserId: String) : ProfileEvent
    object NavigateToEditProfile : ProfileEvent
    object NavigateToSettings : ProfileEvent
    data class ShareProfile(val userId: String) : ProfileEvent
}
