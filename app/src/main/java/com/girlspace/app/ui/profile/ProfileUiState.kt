package com.girlspace.app.ui.profile

import com.girlspace.app.model.ProfileStats
import com.girlspace.app.model.UserProfile

/**
 * Mode in which the profile screen is currently operating.
 */
enum class ProfileMode {
    SELF,   // viewing own profile
    OTHER   // viewing someone else's profile
}

/**
 * Content tab currently selected in the profile.
 */
enum class ProfileTab {
    POSTS,      // main posts feed
    REELS,      // short videos
    PHOTOS      // photo grid
    // later: HIGHLIGHTS, STATS, etc.
}

/**
 * Relationship of the current user to the profile being viewed.
 */
enum class RelationshipStatus {
    NONE,              // stranger / not connected
    FOLLOWING,         // currentUser follows target user
    FOLLOWED_BY,       // target user follows currentUser
    MUTUALS,           // both follow each other
    BLOCKED,           // currentUser has blocked target
    BLOCKED_BY_OTHER   // target has blocked currentUser
}

/**
 * Single source of truth for the entire ProfileScreen UI.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,

    val mode: ProfileMode = ProfileMode.SELF,
    val profileUserId: String? = null,
    val currentUserId: String? = null,

    val userProfile: UserProfile? = null,
    val profileStats: ProfileStats = ProfileStats(),

    val relationshipStatus: RelationshipStatus = RelationshipStatus.NONE,
    val selectedTab: ProfileTab = ProfileTab.POSTS,

    // Content highlights (IDs that map to your existing Post/Media models)
    val pinnedPosts: List<String> = emptyList(),
    val postIds: List<String> = emptyList(),
    val reelIds: List<String> = emptyList(),
    val photoMediaIds: List<String> = emptyList(),

    // Extended info previews
    val groupsPreview: List<String> = emptyList(),
    val eventsPreview: List<String> = emptyList(),

    // Shared (for OTHER mode)
    val sharedMediaIds: List<String> = emptyList(),
    val sharedGroupsPreview: List<String> = emptyList(),

    // Messages for snackbars/toasts
    val errorMessage: String? = null,
    val infoMessage: String? = null,

    // Dialog / sheet flags
    val showBlockConfirmDialog: Boolean = false,
    val showReportSheet: Boolean = false
)
