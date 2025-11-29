package com.girlspace.app.core.plan

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight singleton to expose current user's plan limits.
 *
 * Firestore structure:
 *
 *   users/{uid}
 *      plan: "free" | "basic" | "premium_plus"
 *
 *   config_plan_limits/{planId}
 *      maxImagesPerPost: number
 *      maxVideosPerDay: number
 *      maxStoryMedia: number
 *      maxReelDurationSec: number
 *      allowedGroupCreations: number
 *      canSendVideoInGroups: boolean
 *      canSendVoiceNotes: boolean
 *      storageLimitGb: number
 *      adsEnabled: boolean
 *      creatorMode: boolean
 */
object PlanLimitsRepository {

    private const val TAG = "PlanLimitsRepository"

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _currentPlanId = MutableStateFlow("free")
    val currentPlanId: StateFlow<String> = _currentPlanId.asStateFlow()

    private val _planLimits = MutableStateFlow<PlanLimits>(DefaultPlanLimits.FREE)
    val planLimits: StateFlow<PlanLimits> = _planLimits.asStateFlow()

    private var userListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var planListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Call once after login (e.g. from HomeRoot or Splash) to start listening.
     * Safe to call multiple times; it will clear previous listeners.
     */
    fun start() {
        val user = auth.currentUser ?: run {
            Log.w(TAG, "start(): no logged-in user, reverting to FREE plan")
            _currentPlanId.value = "free"
            _planLimits.value = DefaultPlanLimits.FREE
            clearListeners()
            return
        }

        clearListeners()

        userListenerRegistration = firestore
            .collection("users")
            .document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "User listener failed", error)
                    return@addSnapshotListener
                }

                val doc = snapshot ?: return@addSnapshotListener
                val planId = (doc.getString("plan") ?: "free").lowercase()

                if (planId != _currentPlanId.value) {
                    _currentPlanId.value = planId
                    loadPlanLimits(planId)
                }
            }

        // Initial load just in case
        loadPlanLimits(_currentPlanId.value)
    }

    private fun clearListeners() {
        userListenerRegistration?.remove()
        userListenerRegistration = null

        planListenerRegistration?.remove()
        planListenerRegistration = null
    }

    private fun loadPlanLimits(planIdRaw: String) {
        val planId = planIdRaw.ifBlank { "free" }.lowercase()

        planListenerRegistration?.remove()
        planListenerRegistration = null

        val default = when (planId) {
            "basic" -> DefaultPlanLimits.BASIC
            "premium_plus", "premiumplus", "premium+" -> DefaultPlanLimits.PREMIUM_PLUS
            else -> DefaultPlanLimits.FREE
        }

        // Optimistically apply defaults immediately
        _planLimits.value = default

        planListenerRegistration = firestore
            .collection("config_plan_limits")
            .document(planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Plan limits listener for '$planId' failed", error)
                    _planLimits.value = default
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    _planLimits.value = default
                    return@addSnapshotListener
                }

                val data = snapshot.data ?: run {
                    _planLimits.value = default
                    return@addSnapshotListener
                }

                scope.launch {
                    try {
                        val limits = PlanLimits(
                            maxImagesPerPost = (data["maxImagesPerPost"] as? Number)?.toInt()
                                ?: default.maxImagesPerPost,
                            maxVideosPerDay = (data["maxVideosPerDay"] as? Number)?.toInt()
                                ?: default.maxVideosPerDay,
                            maxStoryMedia = (data["maxStoryMedia"] as? Number)?.toInt()
                                ?: default.maxStoryMedia,
                            maxReelDurationSec = (data["maxReelDurationSec"] as? Number)?.toInt()
                                ?: default.maxReelDurationSec,
                            allowedGroupCreations = (data["allowedGroupCreations"] as? Number)?.toInt()
                                ?: default.allowedGroupCreations,
                            canSendVideoInGroups = (data["canSendVideoInGroups"] as? Boolean)
                                ?: default.canSendVideoInGroups,
                            canSendVoiceNotes = (data["canSendVoiceNotes"] as? Boolean)
                                ?: default.canSendVoiceNotes,
                            storageLimitGb = (data["storageLimitGb"] as? Number)?.toInt()
                                ?: default.storageLimitGb,
                            adsEnabled = (data["adsEnabled"] as? Boolean)
                                ?: default.adsEnabled,
                            creatorMode = (data["creatorMode"] as? Boolean)
                                ?: default.creatorMode
                        )

                        _planLimits.value = limits
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to parse plan limits for '$planId', using defaults", t)
                        _planLimits.value = default
                    }
                }
            }
    }
}
