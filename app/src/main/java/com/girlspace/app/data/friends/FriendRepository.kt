package com.girlspace.app.data.friends
import com.girlspace.app.data.friends.FriendScope

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Lightweight user summary for Friends / Requests / Suggestions UI.
 * We don't depend on any existing User data class to avoid conflicts.
 */
data class FriendUserSummary(
    val uid: String = "",
    val fullName: String = "",
    val photoUrl: String? = null,
    val email: String? = null,
    val phone: String? = null
)

/**
 * Represent a pending friend request shown in Requests tab.
 */
data class FriendRequestItem(
    val fromUid: String = "",
    val toUid: String = "",
    val createdAt: Long = 0L,
    val user: FriendUserSummary = FriendUserSummary()
)

class FriendRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    // region helpers

    private fun currentUid(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in" }
        return uid
    }

    private fun usersCollection() = db.collection("users")
    private fun friendsRoot(scope: FriendScope) =
        if (scope == FriendScope.INNER_CIRCLE) "ic_friends" else "friends"

    private fun requestsRoot(scope: FriendScope) =
        if (scope == FriendScope.INNER_CIRCLE) "ic_friend_requests" else "friend_requests"

    private fun blockedRoot(scope: FriendScope) =
        if (scope == FriendScope.INNER_CIRCLE) "ic_blocked_users" else "blocked_users"

    private fun friendsCollection(
        uid: String = currentUid(),
        scope: FriendScope = FriendScope.PUBLIC
    ) = db.collection(friendsRoot(scope)).document(uid).collection("list")

    private fun incomingRequestsCollection(
        uid: String = currentUid(),
        scope: FriendScope = FriendScope.PUBLIC
    ) = db.collection(requestsRoot(scope)).document(uid).collection("incoming")

    private fun outgoingRequestsCollection(
        uid: String = currentUid(),
        scope: FriendScope = FriendScope.PUBLIC
    ) = db.collection(requestsRoot(scope)).document(uid).collection("outgoing")

    private fun blockedUsersCollection(
        uid: String = currentUid(),
        scope: FriendScope = FriendScope.PUBLIC
    ) = db.collection(blockedRoot(scope)).document(uid).collection(uid)

    // endregion

    // region core actions

    /**
     * Send a friend request from current user to [toUid].
     * If a request already exists, this is effectively a no-op (overwrites same doc).
     */
    suspend fun sendFriendRequest(toUid: String) {
        val fromUid = currentUid()
        require(fromUid != toUid) { "Cannot send friend request to yourself" }

        val now = System.currentTimeMillis()

        val incomingRef = incomingRequestsCollection(toUid).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid).document(toUid)

        val dataIncoming = mapOf(
            "fromUid" to fromUid,
            "toUid" to toUid,
            "createdAt" to now
        )
        val dataOutgoing = mapOf(
            "fromUid" to fromUid,
            "toUid" to toUid,
            "createdAt" to now
        )

        val batch = db.batch()
        batch.set(incomingRef, dataIncoming)
        batch.set(outgoingRef, dataOutgoing)
        batch.commit().await()
    }
    suspend fun sendFriendRequest(toUid: String, scope: FriendScope) {
        val fromUid = currentUid()
        require(fromUid != toUid) { "Cannot send friend request to yourself" }
        val now = System.currentTimeMillis()

        val incomingRef = incomingRequestsCollection(toUid, scope).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid, scope).document(toUid)

        val data = mapOf("fromUid" to fromUid, "toUid" to toUid, "createdAt" to now)

        val batch = db.batch()
        batch.set(incomingRef, data)
        batch.set(outgoingRef, data)
        batch.commit().await()
    }

    suspend fun cancelFriendRequest(toUid: String, scope: FriendScope) {
        val fromUid = currentUid()
        val incomingRef = incomingRequestsCollection(toUid, scope).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid, scope).document(toUid)

        val batch = db.batch()
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    suspend fun rejectFriendRequest(fromUid: String, scope: FriendScope) {
        val toUid = currentUid()

        val incomingRef = incomingRequestsCollection(toUid, scope).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid, scope).document(toUid)

        val batch = db.batch()
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    suspend fun acceptFriendRequest(fromUid: String, scope: FriendScope) {
        val toUid = currentUid()
        val now = System.currentTimeMillis()

        val incomingRef = incomingRequestsCollection(toUid, scope).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid, scope).document(toUid)

        val aFriendRef = friendsCollection(fromUid, scope).document(toUid)
        val bFriendRef = friendsCollection(toUid, scope).document(fromUid)

        val friendDataA = mapOf("friendUid" to toUid, "createdAt" to now, "lastInteractionAt" to now)
        val friendDataB = mapOf("friendUid" to fromUid, "createdAt" to now, "lastInteractionAt" to now)

        val batch = db.batch()
        batch.set(aFriendRef, friendDataA)
        batch.set(bFriendRef, friendDataB)
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    suspend fun unfriend(friendUid: String, scope: FriendScope) {
        val uid = currentUid()
        val myFriendRef = friendsCollection(uid, scope).document(friendUid)
        val theirFriendRef = friendsCollection(friendUid, scope).document(uid)

        val batch = db.batch()
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)
        batch.commit().await()
    }

    suspend fun blockUser(targetUid: String, scope: FriendScope) {
        val uid = currentUid()
        val now = System.currentTimeMillis()

        val blockRef = blockedUsersCollection(uid, scope).document(targetUid)

        val myFriendRef = friendsCollection(uid, scope).document(targetUid)
        val theirFriendRef = friendsCollection(targetUid, scope).document(uid)

        val incomingMeFromThem = incomingRequestsCollection(uid, scope).document(targetUid)
        val outgoingMeToThem = outgoingRequestsCollection(uid, scope).document(targetUid)
        val incomingThemFromMe = incomingRequestsCollection(targetUid, scope).document(uid)
        val outgoingThemToMe = outgoingRequestsCollection(targetUid, scope).document(uid)

        val meDocRef = usersCollection().document(uid)

        val batch = db.batch()
        batch.set(blockRef, mapOf("blockedUid" to targetUid, "createdAt" to now))
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)
        batch.delete(incomingMeFromThem)
        batch.delete(outgoingMeToThem)
        batch.delete(incomingThemFromMe)
        batch.delete(outgoingThemToMe)
        batch.update(meDocRef, "following", FieldValue.arrayRemove(targetUid))
        batch.commit().await()
    }

    suspend fun unblockUser(targetUid: String, scope: FriendScope) {
        val uid = currentUid()
        blockedUsersCollection(uid, scope).document(targetUid).delete().await()
    }

    /**
     * Cancel a pending friend request that current user previously sent to [toUid].
     */
    suspend fun cancelFriendRequest(toUid: String) {
        val fromUid = currentUid()

        val incomingRef = incomingRequestsCollection(toUid).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid).document(toUid)

        val batch = db.batch()
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    /**
     * Reject a friend request sent by [fromUid] to the current user.
     */
    suspend fun rejectFriendRequest(fromUid: String) {
        val toUid = currentUid()

        val incomingRef = incomingRequestsCollection(toUid).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid).document(toUid)

        val batch = db.batch()
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    /**
     * Accept a friend request from [fromUid] to the current user.
     * Creates friend docs for both users, then removes the request docs.
     */
    suspend fun acceptFriendRequest(fromUid: String) {
        val toUid = currentUid()
        val now = System.currentTimeMillis()

        val incomingRef = incomingRequestsCollection(toUid).document(fromUid)
        val outgoingRef = outgoingRequestsCollection(fromUid).document(toUid)

        val aFriendRef = friendsCollection(fromUid).document(toUid)
        val bFriendRef = friendsCollection(toUid).document(fromUid)

        val friendDataA = mapOf(
            "friendUid" to toUid,
            "createdAt" to now,
            "lastInteractionAt" to now
        )
        val friendDataB = mapOf(
            "friendUid" to fromUid,
            "createdAt" to now,
            "lastInteractionAt" to now
        )

        val batch = db.batch()
        // create bidirectional friends
        batch.set(aFriendRef, friendDataA)
        batch.set(bFriendRef, friendDataB)
        // delete pending requests
        batch.delete(incomingRef)
        batch.delete(outgoingRef)
        batch.commit().await()
    }

    /**
     * Remove friendship between current user and [friendUid] from both sides.
     */
    suspend fun unfriend(friendUid: String) {
        val uid = currentUid()

        val myFriendRef = friendsCollection(uid).document(friendUid)
        val theirFriendRef = friendsCollection(friendUid).document(uid)

        val batch = db.batch()
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)
        batch.commit().await()
    }

    /**
     * Block [targetUid].
     * Removes any friendship and pending requests both sides, then writes blocked_users entry.
     * Also removes from my "following" array, if present.
     */
    suspend fun blockUser(targetUid: String) {
        val uid = currentUid()
        val now = System.currentTimeMillis()

        val blockRef = blockedUsersCollection(uid).document(targetUid)

        val myFriendRef = friendsCollection(uid).document(targetUid)
        val theirFriendRef = friendsCollection(targetUid).document(uid)

        val incomingMeFromThem = incomingRequestsCollection(uid).document(targetUid)
        val outgoingMeToThem = outgoingRequestsCollection(uid).document(targetUid)
        val incomingThemFromMe = incomingRequestsCollection(targetUid).document(uid)
        val outgoingThemToMe = outgoingRequestsCollection(targetUid).document(uid)

        val meDocRef = usersCollection().document(uid)

        val batch = db.batch()
        batch.set(blockRef, mapOf("blockedUid" to targetUid, "createdAt" to now))
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)
        batch.delete(incomingMeFromThem)
        batch.delete(outgoingMeToThem)
        batch.delete(incomingThemFromMe)
        batch.delete(outgoingThemToMe)
        // Remove from my following list as well
        batch.update(meDocRef, "following", FieldValue.arrayRemove(targetUid))

        batch.commit().await()
    }

    /**
     * Unblock [targetUid]. Does NOT re-create friendship.
     */
    suspend fun unblockUser(targetUid: String) {
        val uid = currentUid()
        val blockRef = blockedUsersCollection(uid).document(targetUid)
        blockRef.delete().await()
    }

    /**
     * Follow another user by adding them to my "following" array.
     */
    suspend fun followUser(targetUid: String) {
        val uid = currentUid()
        if (uid == targetUid) return

        usersCollection()
            .document(uid)
            .update("following", FieldValue.arrayUnion(targetUid))
            .await()
    }

    /**
     * Unfollow another user by removing them from my "following" array.
     */
    suspend fun unfollowUser(targetUid: String) {
        val uid = currentUid()
        if (uid == targetUid) return

        usersCollection()
            .document(uid)
            .update("following", FieldValue.arrayRemove(targetUid))
            .await()
    }

    // endregion

    // region live streams

    /**
     * Live list of current user's friends, resolved to FriendUserSummary.
     * NOTE: For now this does N extra user fetches; ok for V1.
     */
    fun observeFriends(): Flow<List<FriendUserSummary>> = callbackFlow {
        val uid = currentUid()

        val registration = friendsCollection(uid)
            .orderBy("lastInteractionAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val friendIds = snapshot?.documents?.mapNotNull { doc ->
                    doc.getString("friendUid")
                } ?: emptyList()

                if (friendIds.isEmpty()) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                GlobalScope.launch {
                    val summaries = friendIds.mapNotNull { friendId ->
                        try {
                            val userSnap = usersCollection().document(friendId).get().await()
                            if (!userSnap.exists()) return@mapNotNull null
                            FriendUserSummary(
                                uid = friendId,
                                fullName = userSnap.getString("name")
                                    ?: userSnap.getString("fullName")
                                    ?: "",
                                photoUrl = userSnap.getString("photoUrl")
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trySend(summaries).isSuccess
                }
            }

        awaitClose { registration.remove() }
    }
    fun observeFriends(scope: FriendScope): Flow<List<FriendUserSummary>> = callbackFlow {
        val uid = currentUid()

        val registration = friendsCollection(uid, scope)
            .orderBy("lastInteractionAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val friendIds = snapshot?.documents?.mapNotNull { doc ->
                    doc.getString("friendUid")
                } ?: emptyList()

                if (friendIds.isEmpty()) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                GlobalScope.launch {
                    val summaries = friendIds.mapNotNull { friendId ->
                        try {
                            val userSnap = usersCollection().document(friendId).get().await()
                            if (!userSnap.exists()) return@mapNotNull null
                            FriendUserSummary(
                                uid = friendId,
                                fullName = userSnap.getString("name")
                                    ?: userSnap.getString("fullName")
                                    ?: "",
                                photoUrl = userSnap.getString("photoUrl")
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trySend(summaries).isSuccess
                }
            }

        awaitClose { registration.remove() }
    }

    fun observeIncomingRequests(scope: FriendScope): Flow<List<FriendRequestItem>> = callbackFlow {
        val uid = currentUid()

        val registration = incomingRequestsCollection(uid, scope)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents ?: emptyList()

                GlobalScope.launch {
                    val result = requests.mapNotNull { doc ->
                        val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                        val toUid = doc.getString("toUid") ?: uid
                        val createdAt = doc.getLong("createdAt") ?: 0L

                        val userSnap = try { usersCollection().document(fromUid).get().await() } catch (_: Exception) { null }
                        val userSummary = if (userSnap != null && userSnap.exists()) {
                            FriendUserSummary(
                                uid = fromUid,
                                fullName = userSnap.getString("name")
                                    ?: userSnap.getString("fullName")
                                    ?: "",
                                photoUrl = userSnap.getString("photoUrl")
                            )
                        } else FriendUserSummary(uid = fromUid)

                        FriendRequestItem(fromUid = fromUid, toUid = toUid, createdAt = createdAt, user = userSummary)
                    }

                    trySend(result).isSuccess
                }
            }

        awaitClose { registration.remove() }
    }

    fun observeOutgoingRequests(scope: FriendScope): Flow<Set<String>> = callbackFlow {
        val uid = currentUid()

        val registration = outgoingRequestsCollection(uid, scope)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet()).isSuccess
                    return@addSnapshotListener
                }

                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("toUid") }
                    ?.toSet()
                    ?: emptySet()

                trySend(ids).isSuccess
            }

        awaitClose { registration.remove() }
    }

    /**
     * Live incoming friend requests for current user.
     */
    fun observeIncomingRequests(): Flow<List<FriendRequestItem>> = callbackFlow {
        val uid = currentUid()

        val registration = incomingRequestsCollection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents ?: emptyList()

                GlobalScope.launch {
                    val result = requests.mapNotNull { doc ->
                        val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                        val toUid = doc.getString("toUid") ?: uid
                        val createdAt = doc.getLong("createdAt") ?: 0L

                        val userSnap = try {
                            usersCollection().document(fromUid).get().await()
                        } catch (_: Exception) {
                            null
                        }

                        val userSummary = if (userSnap != null && userSnap.exists()) {
                            FriendUserSummary(
                                uid = fromUid,
                                fullName = userSnap.getString("name")
                                    ?: userSnap.getString("fullName")
                                    ?: "",
                                photoUrl = userSnap.getString("photoUrl")
                            )
                        } else {
                            FriendUserSummary(uid = fromUid)
                        }

                        FriendRequestItem(
                            fromUid = fromUid,
                            toUid = toUid,
                            createdAt = createdAt,
                            user = userSummary
                        )
                    }

                    trySend(result).isSuccess
                }
            }

        awaitClose { registration.remove() }
    }

    /**
     * Live outgoing requests from current user (to show "Requested").
     */
    fun observeOutgoingRequests(): Flow<Set<String>> = callbackFlow {
        val uid = currentUid()

        val registration = outgoingRequestsCollection(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet()).isSuccess
                    return@addSnapshotListener
                }

                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("toUid") }
                    ?.toSet()
                    ?: emptySet()

                trySend(ids).isSuccess
            }

        awaitClose { registration.remove() }
    }

    /**
     * Suggestions = "everyone else" for now.
     * UI will hide:
     *  - already-friends
     *  - locally-declined
     */
    fun observeSuggestions(): Flow<List<FriendUserSummary>> = callbackFlow {
        val uid = currentUid()

        val registration = usersCollection()
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val list = snapshot?.documents
                    ?.filter { it.id != uid }
                    ?.map { doc ->
                        FriendUserSummary(
                            uid = doc.id,
                            fullName = (doc.getString("name")
                                ?: doc.getString("fullName")
                                ?: "").ifBlank { "User" },
                            photoUrl = doc.getString("photoUrl")
                        )
                    }
                    ?: emptyList()

                trySend(list).isSuccess
            }

        awaitClose { registration.remove() }
    }

    // endregion

    // region search

    /**
     * Substring search by name / email / phone.
     *
     * V1 approach (simple but OK for small user base):
     *  - Load up to 200 users from Firestore
     *  - Filter on client:
     *      * name contains query (case-insensitive)
     *      * OR email contains query (case-insensitive)
     *      * OR phone contains digits typed
     */
    suspend fun searchUsers(query: String): List<FriendUserSummary> {
        val trimmed = query.trim()
        if (trimmed.length < 3) return emptyList()   // safety

        val collection = usersCollection()
        val q: Query = when {
            trimmed.contains("@") -> {
                collection.whereEqualTo("email", trimmed.lowercase())
            }
            trimmed.startsWith("+") || trimmed.all { it.isDigit() } -> {
                // Store phone either as "phoneE164" or "phone" – we support both
                collection.whereEqualTo("phoneE164", trimmed)
            }
            else -> {
                // Basic name search – first pass: exact match on Name / fullName
                collection.whereEqualTo("Name", trimmed)
            }
        }

        val snapshot = q.limit(20).get().await()

        return snapshot.documents.map { doc ->
            FriendUserSummary(
                uid = doc.id,
                fullName = doc.getString("Name")
                    ?: doc.getString("fullName")
                    ?: "",
                photoUrl = doc.getString("photoUrl"),
                email = doc.getString("email"),
                phone = doc.getString("phone") ?: doc.getString("phoneE164")
            )
        }
    }

    // endregion
}
