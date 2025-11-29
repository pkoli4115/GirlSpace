package com.girlspace.app.core.plan

/**
 * Unified limits for every feature depending on user's plan.
 *
 * All fields should have a corresponding entry in Firestore so that
 * limits can be adjusted without shipping a new app build.
 */
data class PlanLimits(
    // --- Images ---
    val maxImagesPerPost: Int,        // per post in feed & per message in chat
    // --- Videos ---
    val maxVideosPerDay: Int,         // total videos user can upload per day (feed + chats)

    // --- Stories / reels ---
    val maxStoryMedia: Int,           // how many photos/videos per story
    val maxReelDurationSec: Int,      // maximum reel / short video length

    // --- Groups / chat ---
    val allowedGroupCreations: Int,   // how many groups a user can create
    val canSendVideoInGroups: Boolean,
    val canSendVoiceNotes: Boolean,

    // --- Storage / monetisation ---
    val storageLimitGb: Int,
    val adsEnabled: Boolean,
    val creatorMode: Boolean          // Included in Premium+ (extra creator features)
)

/**
 * Local defaults used if Firestore config is missing.
 * Keep these in sync with your backend / admin JSON.
 */
object DefaultPlanLimits {

    // Free users – heavy limits, ads on
    val FREE = PlanLimits(
        maxImagesPerPost = 1,
        maxVideosPerDay = 1,
        maxStoryMedia = 2,
        maxReelDurationSec = 30,
        allowedGroupCreations = 0,
        canSendVideoInGroups = false,
        canSendVoiceNotes = false,
        storageLimitGb = 2,
        adsEnabled = true,
        creatorMode = false
    )

    // Basic monthly – a bit more freedom
    val BASIC = PlanLimits(
        maxImagesPerPost = 3,
        maxVideosPerDay = 2,
        maxStoryMedia = 4,
        maxReelDurationSec = 45,
        allowedGroupCreations = 2,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 10,
        adsEnabled = true,
        creatorMode = false
    )

    // Premium+ – full power
    val PREMIUM_PLUS = PlanLimits(
        maxImagesPerPost = 5,
        maxVideosPerDay = 5,
        maxStoryMedia = 6,
        maxReelDurationSec = 60,
        allowedGroupCreations = 10,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 50,
        adsEnabled = false,
        creatorMode = true
    )
}
