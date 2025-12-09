package com.girlspace.app.model

/**
 * Aggregated profile statistics for quick display.
 * Can be hydrated from counters in Firestore / backend.
 */
data class ProfileStats(
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val postsCount: Int = 0,
    val reelsCount: Int = 0,
    val photosCount: Int = 0,
    val mutualConnectionsPreview: List<String> = emptyList(), // display names / usernames
    val activityStreakDays: Int = 0,
    val profileViewsThisWeek: Int = 0
)
