package com.girlspace.app.ui.groups

import com.girlspace.app.data.groups.GroupsScope
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.ui.billing.BillingConfigRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GroupsViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // 🔹 Billing config (plan → limits)
    private val billing: BillingConfigRepository = BillingConfigRepository(firestore)

    private val _groups: MutableStateFlow<List<GroupItem>> = MutableStateFlow(emptyList())
    val groups: StateFlow<List<GroupItem>> = _groups

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var scope: GroupsScope = GroupsScope.PUBLIC

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _creationBlocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val creationBlocked: StateFlow<Boolean> = _creationBlocked

    private var listener: ListenerRegistration? = null

    init {
        observeGroups()
    }

    // -------------------------------------------------------------------------
    // LOAD & OBSERVE GROUPS
    // -------------------------------------------------------------------------
    fun setScope(newScope: GroupsScope) {
        if (scope == newScope) return
        scope = newScope
        observeGroups()
    }

    private fun observeGroups() {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _groups.value = emptyList()
            return
        }

        _isLoading.value = true

        listener?.remove()
        listener = groupsCollection()
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("GirlSpace", "Groups listen failed", e)
                    _isLoading.value = false
                    _errorMessage.value =
                        "Failed to load groups: ${e.localizedMessage ?: "Unknown error"}"
                    return@addSnapshotListener
                }

                val docs = snapshot ?: return@addSnapshotListener
                val currentUserId = uid

                val list: List<GroupItem> = docs.documents.mapNotNull { doc ->
                    val data: Map<String, Any?> = doc.data ?: emptyMap()

                    // ✅ Archive filter: missing field means false (not archived)
                    val isArchived = data["isArchived"] as? Boolean ?: false
                    if (isArchived) return@mapNotNull null

                    val createdBy: String = data["createdBy"] as? String ?: ""

                    val membersRaw: List<*> = (data["members"] as? List<*>) ?: emptyList<Any?>()
                    val members: List<String> = membersRaw.filterIsInstance<String>()

                    val memberCount: Long = (data["memberCount"] as? Long) ?: members.size.toLong()

                    GroupItem(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        iconUrl = data["iconUrl"] as? String ?: "",
                        createdBy = createdBy,
                        memberCount = memberCount,
                        isMember = members.contains(currentUserId),
                        isOwner = (currentUserId == createdBy)
                    )
                }

                _groups.value = list
                _isLoading.value = false
            }
    }

    // -------------------------------------------------------------------------
    // PUBLIC HELPERS
    // -------------------------------------------------------------------------
    fun clearError() {
        _errorMessage.value = null
    }

    fun clearCreationBlocked() {
        _creationBlocked.value = false
    }

    // -------------------------------------------------------------------------
    // CREATE GROUP (respect plan limits via BillingConfigRepository)
    // -------------------------------------------------------------------------
    fun createGroup(
        name: String,
        description: String
    ) {
        val trimmedName: String = name.trim()
        val trimmedDesc: String = description.trim()

        if (trimmedName.isBlank()) {
            _errorMessage.value = "Group name cannot be empty."
            return
        }

        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                _errorMessage.value = "You must be logged in to create a group."
                return@launch
            }

            try {
                _isLoading.value = true
                _creationBlocked.value = false
                _errorMessage.value = null

                val uid: String = user.uid

                // 1) Get user plan from /users/{uid}
                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                val plan: String = userDoc.getString("plan") ?: "free"

                // 2) Count groups created by this user
                val createdByMeCount: Int = _groups.value.count { it.isOwner }

                // 3) Check plan limit
                val allowed: Boolean = billing.canCreateGroup(plan, createdByMeCount)

                if (!allowed) {
                    _creationBlocked.value = true
                    _isLoading.value = false
                    return@launch
                }

                val groupData: Map<String, Any?> = hashMapOf(
                    "name" to trimmedName,
                    "description" to trimmedDesc,
                    "iconUrl" to "",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "members" to listOf(uid),
                    "memberCount" to 1L,

                    // ✅ archive fields (default)
                    "isArchived" to false,
                    "archivedAt" to null,
                    "archivedBy" to null
                )

                groupsCollection()
                    .add(groupData)
                    .await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "createGroup failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to create group."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // JOIN / LEAVE GROUP
    // -------------------------------------------------------------------------
    fun joinGroup(group: GroupItem) {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.value = "You must be logged in to join a group."
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ref = groupsCollection().document(group.id)

                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    val membersRaw: List<*> =
                        (snap.get("members") as? List<*>) ?: emptyList<Any?>()
                    val members: MutableList<String> =
                        membersRaw.filterIsInstance<String>().toMutableList()

                    if (!members.contains(uid)) {
                        members.add(uid)
                        tx.update(
                            ref,
                            mapOf(
                                "members" to members,
                                "memberCount" to members.size.toLong()
                            )
                        )
                    }
                }.await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "joinGroup failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to join group."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun leaveGroup(group: GroupItem) {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.value = "You must be logged in to leave a group."
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ref = groupsCollection().document(group.id)

                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    val membersRaw: List<*> =
                        (snap.get("members") as? List<*>) ?: emptyList<Any?>()
                    val members: MutableList<String> =
                        membersRaw.filterIsInstance<String>().toMutableList()

                    if (members.contains(uid)) {
                        members.remove(uid)
                        tx.update(
                            ref,
                            mapOf(
                                "members" to members,
                                "memberCount" to members.size.toLong()
                            )
                        )
                    }
                }.await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "leaveGroup failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to leave group."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun groupsCollection() =
        if (scope == GroupsScope.INNER_CIRCLE)
            firestore.collection("ic_groups")
        else
            firestore.collection("groups")

    // -------------------------------------------------------------------------
    // MEMBERS
    // -------------------------------------------------------------------------
    fun addMembersToGroup(groupId: String, memberIds: Set<String>) {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.value = "You must be logged in."
            return
        }
        if (memberIds.isEmpty()) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ref = groupsCollection().document(groupId)

                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)

                    val membersRaw: List<*> =
                        (snap.get("members") as? List<*>) ?: emptyList<Any?>()
                    val members: MutableSet<String> =
                        membersRaw.filterIsInstance<String>().toMutableSet()

                    members.add(uid)
                    members.addAll(memberIds)

                    tx.update(
                        ref,
                        mapOf(
                            "members" to members.toList(),
                            "memberCount" to members.size.toLong()
                        )
                    )
                }.await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "addMembersToGroup failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to add members."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateGroupMembers(groupId: String, finalMemberIds: Set<String>) {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.value = "You must be logged in."
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ref = groupsCollection().document(groupId)

                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)

                    val createdBy = snap.getString("createdBy") ?: ""
                    val membersRaw: List<*> =
                        (snap.get("members") as? List<*>) ?: emptyList<Any?>()
                    val existing: Set<String> = membersRaw.filterIsInstance<String>().toSet()

                    val locked = setOf(createdBy, uid).filter { it.isNotBlank() }.toSet()
                    val merged = (finalMemberIds + locked).filter { it.isNotBlank() }.toSet()
                    val safeFinal = if (merged.isEmpty()) existing else merged

                    tx.update(
                        ref,
                        mapOf(
                            "members" to safeFinal.toList(),
                            "memberCount" to safeFinal.size.toLong()
                        )
                    )
                }.await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "updateGroupMembers failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to update members."
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getGroupMemberIds(groupId: String): Set<String> {
        val snap = groupsCollection()
            .document(groupId)
            .get()
            .await()

        val membersRaw = snap.get("members") as? List<*> ?: emptyList<Any?>()
        return membersRaw.filterIsInstance<String>().toSet()
    }

    /**
     * ✅ Resolve /users/{uid} -> name map (shows names instead of UID)
     * Uses FieldPath.documentId() and chunks of 10.
     */
    suspend fun getUserNames(uids: List<String>): Map<String, String> {
        if (uids.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        val chunks = uids.distinct().chunked(10)

        for (chunk in chunks) {
            val snap = firestore.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            for (doc in snap.documents) {
                val uid = doc.id
                val name = doc.getString("fullName")
                    ?: doc.getString("name")
                    ?: doc.getString("displayName")
                    ?: doc.getString("username")
                    ?: "User"
                result[uid] = name
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // ARCHIVE (SOFT DELETE)
    // -------------------------------------------------------------------------
    fun archiveGroup(groupId: String) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _errorMessage.value = "You must be logged in."
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                groupsCollection()
                    .document(groupId)
                    .update(
                        mapOf(
                            "isArchived" to true,
                            "archivedAt" to FieldValue.serverTimestamp(),
                            "archivedBy" to uid
                        )
                    )
                    .await()

            } catch (e: Exception) {
                Log.e("GirlSpace", "archiveGroup failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to archive group."
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
