package com.girlspace.app.ui.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.ui.billing.BillingConfigRepository
import com.google.firebase.auth.FirebaseAuth
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

    private val _groups: MutableStateFlow<List<GroupItem>> =
        MutableStateFlow(emptyList())
    val groups: StateFlow<List<GroupItem>> = _groups

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // When user’s plan forbids creating a new group
    private val _creationBlocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val creationBlocked: StateFlow<Boolean> = _creationBlocked

    private var listener: ListenerRegistration? = null

    init {
        observeGroups()
    }

    // -------------------------------------------------------------------------
    // LOAD & OBSERVE GROUPS
    // -------------------------------------------------------------------------

    private fun observeGroups() {
        val uid: String? = auth.currentUser?.uid
        if (uid == null) {
            _groups.value = emptyList()
            return
        }

        _isLoading.value = true

        listener?.remove()
        listener = firestore.collection("groups")
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

                val list: List<GroupItem> = docs.documents.map { doc ->
                    val data: Map<String, Any?> = doc.data ?: emptyMap()

                    val createdBy: String = data["createdBy"] as? String ?: ""
                    val membersRaw: List<*> =
                        (data["members"] as? List<*>) ?: emptyList<Any?>()
                    val members: List<String> = membersRaw.filterIsInstance<String>()

                    val memberCount: Long =
                        (data["memberCount"] as? Long) ?: members.size.toLong()

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
                val createdByMeCount: Int = _groups.value.count { groupItem ->
                    groupItem.isOwner
                }

                // 3) Check plan limit
                val allowed: Boolean = billing.canCreateGroup(plan, createdByMeCount)

                if (!allowed) {
                    _creationBlocked.value = true
                    _isLoading.value = false
                    return@launch
                }

                // 4) Create the group
                val groupData: Map<String, Any?> = hashMapOf(
                    "name" to trimmedName,
                    "description" to trimmedDesc,
                    "iconUrl" to "",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "members" to listOf(uid),
                    "memberCount" to 1L
                )

                firestore.collection("groups")
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
                val ref = firestore.collection("groups").document(group.id)

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
                val ref = firestore.collection("groups").document(group.id)

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

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
