package com.girlspace.app.data.profile
import com.google.firebase.firestore.FieldValue
import com.girlspace.app.model.ProfileStats
import com.girlspace.app.model.UserBadges
import com.girlspace.app.model.UserProfile
import com.girlspace.app.ui.profile.RelationshipStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-ready ProfileRepository implementation backed by FirebaseAuth + Firestore.
 *
 * NOTE:
 * - Uses "users" collection for profiles.
 * - Uses "posts" collection for user posts.
 * - Uses subcollections "following", "followers", "blockedUsers" for relationships.
 * - Uses "userReports" collection for reports.
 *
 * If your Firestore names differ, we can adjust later without touching ViewModel/UI.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor() : ProfileRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override suspend fun getCurrentUserId(): String? = auth.currentUser?.uid

    override suspend fun loadUserProfile(userId: String): UserProfile {
        val doc = firestore
            .collection("users")
            .document(userId)
            .get()
            .await()

        if (!doc.exists()) {
            throw IllegalStateException("User profile not found")
        }

        val name = doc.getString("name") ?: ""
        val username = doc.getString("username") ?: userId
        val bio = doc.getString("bio")
        val avatarUrl = doc.getString("photoUrl")
        val coverPhotoUrl = doc.getString("coverPhotoUrl")
        val location = doc.getString("location")

        val isOnline = doc.getBoolean("isOnline") ?: false
        val lastSeen = (doc.getLong("lastSeenAt") ?: 0L).takeIf { it > 0L }
        val memberSince = doc.getLong("createdAt") ?: 0L

        val interests = (doc.get("interests") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()
        val groupsJoinedCount = (doc.getLong("groupsJoinedCount") ?: 0L).toInt()
        val eventsCount = (doc.getLong("eventsCount") ?: 0L).toInt()
        val skills = (doc.get("skills") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()

        val privacyLocationVisible = doc.getBoolean("privacyLocationVisible") ?: true
        val privacyLastSeenVisible = doc.getBoolean("privacyLastSeenVisible") ?: true

        val isPremium = doc.getBoolean("isPremium") ?: false
        val isModerator = doc.getBoolean("isModerator") ?: false
        val isTopContributor = doc.getBoolean("isTopContributor") ?: false

        val badges = UserBadges(
            isPremium = isPremium,
            isModerator = isModerator,
            isTopContributor = isTopContributor
        )

        return UserProfile(
            id = userId,
            displayName = name.ifBlank { username },
            username = username,
            bio = bio,
            avatarUrl = avatarUrl,
            coverPhotoUrl = coverPhotoUrl,
            location = location,
            isOnline = isOnline,
            lastSeenTimestamp = lastSeen,
            memberSinceTimestamp = memberSince,
            interests = interests,
            groupsJoinedCount = groupsJoinedCount,
            eventsCount = eventsCount,
            skills = skills,
            privacyLocationVisible = privacyLocationVisible,
            privacyLastSeenVisible = privacyLastSeenVisible,
            badges = badges
        )
    }
    override suspend fun loadProfileStats(userId: String): ProfileStats {
        // Base user doc
        val userSnap = firestore.collection("users")
            .document(userId)
            .get()
            .await()

        if (!userSnap.exists()) {
            // No such user → empty stats
            return ProfileStats()
        }

        // FOLLOWING: from my "following" array
        val followingList = (userSnap.get("following") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()
        val followingCount = followingList.size

        // FOLLOWERS: users whose "following" array contains this userId
        val followersSnap = firestore.collection("users")
            .whereArrayContains("following", userId)
            .get()
            .await()
        val followersCount = followersSnap.size()

        // FRIENDS: from /friends/{uid}/list
        val friendsSnap = firestore.collection("friends")
            .document(userId)
            .collection("list")
            .get()
            .await()
        val friendsCount = friendsSnap.size()

        // Optional: content counters if you’re storing them on the user doc
        val postsCount = (userSnap.getLong("postsCount") ?: 0L).toInt()
        val reelsCount = (userSnap.getLong("reelsCount") ?: 0L).toInt()
        val photosCount = (userSnap.getLong("photosCount") ?: 0L).toInt()

        // Simple mutual / follower preview – top 3 follower names
        val mutualPreview = followersSnap.documents
            .take(3)
            .mapNotNull { doc ->
                doc.getString("name")
                    ?: doc.getString("fullName")
            }

        return ProfileStats(
            followersCount = followersCount,
            followingCount = followingCount,
            friendsCount = friendsCount,
            postsCount = postsCount,
            reelsCount = reelsCount,
            photosCount = photosCount,
            mutualConnectionsPreview = mutualPreview,
            activityStreakDays = 0,
            profileViewsThisWeek = 0
        )

    }
    override suspend fun loadRelationshipStatus(
        currentUserId: String,
        targetUserId: String
    ): RelationshipStatus {
        if (currentUserId == targetUserId) {
            // Self-view; treat as "fully connected" for UI purposes
            return RelationshipStatus.MUTUALS
        }

        // --- 1) Block checks (align with FriendRepository.blockUser) ---
        // FriendRepository writes to: blocked_users/{uid}/{uid}/{targetUid}
        val blockedByCurrent = firestore
            .collection("blocked_users")
            .document(currentUserId)
            .collection(currentUserId)
            .document(targetUserId)
            .get()
            .await()
            .exists()

        if (blockedByCurrent) {
            return RelationshipStatus.BLOCKED
        }

        val blockedByOther = firestore
            .collection("blocked_users")
            .document(targetUserId)
            .collection(targetUserId)
            .document(currentUserId)
            .get()
            .await()
            .exists()

        if (blockedByOther) {
            return RelationshipStatus.BLOCKED_BY_OTHER
        }

        // --- 2) Follow relationship using "following" array on users doc ---
        val currentSnap = firestore
            .collection("users")
            .document(currentUserId)
            .get()
            .await()

        val targetSnap = firestore
            .collection("users")
            .document(targetUserId)
            .get()
            .await()

        if (!currentSnap.exists() || !targetSnap.exists()) {
            return RelationshipStatus.NONE
        }

        fun followingSet(doc: com.google.firebase.firestore.DocumentSnapshot): Set<String> {
            val raw = doc.get("following") as? List<*>
            return raw
                ?.filterIsInstance<String>()
                ?.toSet()
                ?: emptySet()
        }

        val currentFollowing = followingSet(currentSnap)
        val targetFollowing = followingSet(targetSnap)

        val iFollowHer = targetUserId in currentFollowing
        val sheFollowsMe = currentUserId in targetFollowing

        return when {
            iFollowHer && sheFollowsMe -> RelationshipStatus.MUTUALS
            iFollowHer -> RelationshipStatus.FOLLOWING
            sheFollowsMe -> RelationshipStatus.FOLLOWED_BY
            else -> RelationshipStatus.NONE
        }
    }

    override suspend fun toggleFollow(
        currentUserId: String,
        targetUserId: String
    ): RelationshipStatus {
        if (currentUserId == targetUserId) {
            // You can't follow yourself; keep as "mutual" self
            return RelationshipStatus.MUTUALS
        }

        val userRef = firestore
            .collection("users")
            .document(currentUserId)

        // Read current following array once
        val snap = userRef.get().await()
        val currentFollowing = (snap.get("following") as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()

        val isCurrentlyFollowing = targetUserId in currentFollowing

        // Toggle using the same field that FriendRepository uses
        if (isCurrentlyFollowing) {
            // Unfollow → remove from array
            userRef
                .update("following", FieldValue.arrayRemove(targetUserId))
                .await()
        } else {
            // Follow → add to array
            userRef
                .update("following", FieldValue.arrayUnion(targetUserId))
                .await()
        }

        // Return fresh relationship status based on updated arrays
        return loadRelationshipStatus(currentUserId, targetUserId)
    }

    override suspend fun blockUser(
        currentUserId: String,
        targetUserId: String
    ) {
        val blockRef = firestore
            .collection("users")
            .document(currentUserId)
            .collection("blockedUsers")
            .document(targetUserId)

        blockRef.set(
            mapOf(
                "blockedAt" to System.currentTimeMillis()
            )
        ).await()

        // Optional: also remove from following/followers relationships
        firestore
            .collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .delete()
            .await()

        firestore
            .collection("users")
            .document(currentUserId)
            .collection("followers")
            .document(targetUserId)
            .delete()
            .await()
    }

    override suspend fun reportUser(
        reporterUserId: String,
        targetUserId: String,
        reason: String,
        details: String?
    ) {
        firestore
            .collection("userReports")
            .add(
                mapOf(
                    "reporterId" to reporterUserId,
                    "targetUserId" to targetUserId,
                    "reason" to reason,
                    "details" to (details ?: ""),
                    "createdAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun loadPinnedPosts(userId: String): List<String> {
        return try {
            firestore
                .collection("posts")
                .whereEqualTo("authorId", userId)
                .whereEqualTo("pinned", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .await()
                .documents
                .map { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun loadPosts(userId: String): List<String> {
        return try {
            firestore
                .collection("posts")
                .whereEqualTo("authorId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .documents
                .map { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun loadReels(userId: String): List<String> {
        return try {
            firestore
                .collection("posts")
                .whereEqualTo("authorId", userId)
                .whereEqualTo("type", "reel")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .documents
                .map { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun loadPhotos(userId: String): List<String> {
        return try {
            firestore
                .collection("posts")
                .whereEqualTo("authorId", userId)
                .whereEqualTo("hasMedia", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .documents
                .map { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun loadSharedMediaIds(
        currentUserId: String,
        targetUserId: String
    ): List<String> {
        // This can be implemented later to read from chat messages / shared media collections.
        // For now, return empty list to keep UI safe.
        return emptyList()
    }

    override suspend fun loadSharedGroupsPreview(
        currentUserId: String,
        targetUserId: String
    ): List<String> {
        // Example idea (later): query "groups" where both are members.
        // For now, return empty list – UI will simply hide this section.
        return emptyList()
    }
}
