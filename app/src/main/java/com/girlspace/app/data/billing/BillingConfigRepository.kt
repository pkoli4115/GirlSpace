package com.girlspace.app.data.billing

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class BillingConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun loadConfig(): BillingConfig {
        return try {
            val doc = firestore.collection("config")
                .document("billing")
                .get()
                .await()

            val rawJson = doc.getString("json")
            if (rawJson.isNullOrBlank()) {
                Log.w("BillingConfig", "No json field in config/billing; using defaults")
                BillingConfig.default()
            } else {
                parseConfig(rawJson)
            }
        } catch (e: Exception) {
            Log.e("BillingConfig", "Failed to load config, using defaults", e)
            BillingConfig.default()
        }
    }

    private fun parseConfig(json: String): BillingConfig {
        val root = JSONObject(json)

        val plansObj = root.getJSONObject("plans")
        val plans = mutableMapOf<String, PlanConfig>()
        for (key in plansObj.keys()) {
            val p = plansObj.getJSONObject(key)
            plans[key] = PlanConfig(
                key = key,
                displayName = p.optString("displayName", key),
                imagesPerPost = p.optInt("imagesPerPost", 1),
                dailyPostsLimit = p.optInt("dailyPostsLimit", 5),
                storageGb = p.optInt("storageGb", 1),
                storiesPerDay = p.optInt("storiesPerDay", 0),
                storiesMedia = p.optString("storiesMedia", "text_only"),
                videoCallMinutesPerDay = p.optInt("videoCallMinutesPerDay", 0),
                groupCalls = p.optBoolean("groupCalls", false),
                reelsUpload = p.optBoolean("reelsUpload", false),
                ads = p.optString("ads", "full")
            )
        }

        val productsObj = root.optJSONObject("products") ?: JSONObject()
        val products = mutableMapOf<String, ProductConfig>()
        for (key in productsObj.keys()) {
            val pr = productsObj.getJSONObject(key)
            products[key] = ProductConfig(
                productId = pr.optString("productId"),
                planKey = pr.optString("planKey"),
                type = pr.optString("type", "inapp")
            )
        }

        return BillingConfig(
            plans = plans.ifEmpty { BillingConfig.default().plans },
            products = products.ifEmpty { BillingConfig.default().products }
        )
    }
}
