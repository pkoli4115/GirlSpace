package com.girlspace.app.ui.billing

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Reads billing / plan limits from Firestore document:
 * `/config/billing_plans`
 *
 * Also provides helper functions:
 * - canCreateGroup(plan, createdCount)
 * - maxImagesPerPost(plan)
 */
class BillingConfigRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    data class PlanConfig(
        val maxImages: Int = 1,
        val maxGroups: Int = 0
    )

    private var cached: Map<String, PlanConfig>? = null

    suspend fun load(): Map<String, PlanConfig> {
        cached?.let { return it }

        val snap = firestore.collection("config")
            .document("billing_plans")
            .get()
            .await()

        val freePlan = snap.get("free.maxImages")?.toString()?.toIntOrNull() ?: 1
        val basicPlan = snap.get("basic.maxImages")?.toString()?.toIntOrNull() ?: 5
        val premiumPlan = snap.get("premium.maxImages")?.toString()?.toIntOrNull() ?: 10

        val freeGroups = snap.get("free.maxGroups")?.toString()?.toIntOrNull() ?: 0
        val basicGroups = snap.get("basic.maxGroups")?.toString()?.toIntOrNull() ?: 1
        val premiumGroups = snap.get("premium.maxGroups")?.toString()?.toIntOrNull() ?: 99

        val map = mapOf(
            "free" to PlanConfig(maxImages = freePlan, maxGroups = freeGroups),
            "basic" to PlanConfig(maxImages = basicPlan, maxGroups = basicGroups),
            "premium" to PlanConfig(maxImages = premiumPlan, maxGroups = premiumGroups)
        )

        cached = map
        return map
    }

    /** --------------------------------------------------------
     *  PLAN HELPERS
     * --------------------------------------------------------- */

    suspend fun maxImagesPerPost(plan: String): Int {
        val map = load()
        return map[plan]?.maxImages ?: 1
    }

    suspend fun maxGroupsAllowed(plan: String): Int {
        val map = load()
        return map[plan]?.maxGroups ?: 0
    }

    fun canCreateGroup(plan: String, currentCreatedCount: Int): Boolean {
        return when (plan) {
            "premium" -> true                // unlimited
            "basic" -> currentCreatedCount < 1
            "free" -> false
            else -> false
        }
    }
}
