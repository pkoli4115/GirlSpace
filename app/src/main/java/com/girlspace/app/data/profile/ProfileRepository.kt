package com.girlspace.app.data.profile

import com.girlspace.app.model.ProfileStats
import com.girlspace.app.model.UserProfile
import com.girlspace.app.ui.profile.RelationshipStatus

/**
 * Single source of truth for all profile-related data fetch & mutations.
 *
 * Implementation (ProfileRepositoryImpl) should:
 * - talk to Firestore / backend
 * - handle caching where needed
 * - keep this interface stable for UI/ViewModel.
 */
interface ProfileRepository {

    /** Currently authenticated user ID, or null if not logged in. */
    suspend fun getCurrentUserId(): String?

    /** Load a user's public profile. */
    suspend fun loadUserProfile(userId: String): UserProfile

    /** Load stats for profile header (followers, posts, etc.). */
    suspend fun loadProfileStats(userId: String): ProfileStats

    /** Relationship between currentUser and target user. */
    suspend fun loadRelationshipStatus(
        currentUserId: String,
        targetUserId: String
    ): RelationshipStatus

    /** Toggle follow/unfollow. Returns the new relationship status. */
    suspend fun toggleFollow(
        currentUserId: String,
        targetUserId: String
    ): RelationshipStatus

    /** Block target user. */
    suspend fun blockUser(
        currentUserId: String,
        targetUserId: String
    )

    /** Submit an abuse report for target user. */
    suspend fun reportUser(
        reporterUserId: String,
        targetUserId: String,
        reason: String,
        details: String?
    )

    // Content
    suspend fun loadPinnedPosts(userId: String): List<String>      // Post IDs
    suspend fun loadPosts(userId: String): List<String>            // Post IDs
    suspend fun loadReels(userId: String): List<String>            // Reel IDs
    suspend fun loadPhotos(userId: String): List<String>           // Media IDs

    // Shared info between current user & target user
    suspend fun loadSharedMediaIds(
        currentUserId: String,
        targetUserId: String
    ): List<String>

    suspend fun loadSharedGroupsPreview(
        currentUserId: String,
        targetUserId: String
    ): List<String> // group names or IDs (UI can decide how to show)
}
