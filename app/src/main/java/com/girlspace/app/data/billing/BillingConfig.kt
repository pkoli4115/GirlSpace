package com.girlspace.app.data.billing

data class PlanConfig(
    val key: String,
    val displayName: String,
    val imagesPerPost: Int,
    val dailyPostsLimit: Int,
    val storageGb: Int,
    val storiesPerDay: Int,
    val storiesMedia: String,
    val videoCallMinutesPerDay: Int,
    val groupCalls: Boolean,
    val reelsUpload: Boolean,
    val ads: String, // "full" | "basic" | "none"
)

data class ProductConfig(
    val productId: String,
    val planKey: String,
    val type: String, // "subscription" | "inapp"
)

data class BillingConfig(
    val plans: Map<String, PlanConfig>,
    val products: Map<String, ProductConfig>
) {
    companion object {
        fun default(): BillingConfig {
            val free = PlanConfig(
                key = "free",
                displayName = "Free (ads)",
                imagesPerPost = 1,
                dailyPostsLimit = 5,
                storageGb = 1,
                storiesPerDay = 2,
                storiesMedia = "text_only",
                videoCallMinutesPerDay = 3,
                groupCalls = false,
                reelsUpload = false,
                ads = "full"
            )

            val plus = PlanConfig(
                key = "plus",
                displayName = "Plus",
                imagesPerPost = 5,
                dailyPostsLimit = 20,
                storageGb = 5,
                storiesPerDay = 2,
                storiesMedia = "basic_media",
                videoCallMinutesPerDay = 30,
                groupCalls = false,
                reelsUpload = true,
                ads = "basic"
            )

            val vip = PlanConfig(
                key = "vip",
                displayName = "VIP",
                imagesPerPost = 10,
                dailyPostsLimit = 100,
                storageGb = 10,
                storiesPerDay = 50,
                storiesMedia = "full",
                videoCallMinutesPerDay = 300,
                groupCalls = true,
                reelsUpload = true,
                ads = "none"
            )

            val plusMonthly = ProductConfig(
                productId = "com.qtilabs.girlspace.sub.plus.monthly",
                planKey = "plus",
                type = "subscription"
            )
            val vipMonthly = ProductConfig(
                productId = "com.qtilabs.girlspace.sub.vip.monthly",
                planKey = "vip",
                type = "subscription"
            )
            val vipLifetime = ProductConfig(
                productId = "com.qtilabs.girlspace.vip.lifetime",
                planKey = "vip",
                type = "inapp"
            )

            return BillingConfig(
                plans = mapOf(
                    "free" to free,
                    "plus" to plus,
                    "vip" to vip
                ),
                products = mapOf(
                    "plus_monthly" to plusMonthly,
                    "vip_monthly" to vipMonthly,
                    "vip_lifetime" to vipLifetime
                )
            )
        }
    }
}
