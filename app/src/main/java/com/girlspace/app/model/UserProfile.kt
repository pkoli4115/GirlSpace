package com.girlspace.app.model

/**
 * Core user profile model for GirlSpace.
 * This should be mapped from Firestore / backend user document.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val username: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val location: String? = null,
    val isOnline: Boolean = false,
    val lastSeenTimestamp: Long? = null,      // millis; null if hidden
    val memberSinceTimestamp: Long = 0L,      // millis since epoch
    val interests: List<String> = emptyList(),
    val groupsJoinedCount: Int = 0,
    val eventsCount: Int = 0,
    val skills: List<String> = emptyList(),
    val privacyLocationVisible: Boolean = true,
    val privacyLastSeenVisible: Boolean = true,
    val badges: UserBadges = UserBadges()
)

/**
 * Badges that decorate the user in UI (premium / moderator / contributor).
 */
data class UserBadges(
    val isPremium: Boolean = false,
    val isModerator: Boolean = false,
    val isTopContributor: Boolean = false
)
