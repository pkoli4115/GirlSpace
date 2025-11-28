package com.girlspace.app.core.plan

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Reads user's plan (free/basic/premium) from Firestore
 * and returns the appropriate limits.
 */

class UserPlanEvaluator(
    private val firestore: FirebaseFirestore
) {

    suspend fun getLimits(uid: String): PlanLimits {
        val snap = firestore.collection("users").document(uid).get().await()

        val plan = snap.getString("plan") ?: "free"

        return when (plan) {
            "basic" -> DefaultPlanProvider.BASIC
            "premium" -> DefaultPlanProvider.PREMIUM
            else -> DefaultPlanProvider.FREE
        }
    }
}
