package com.girlspace.app.core.plan

/**
 * Local fallback plan limits.
 * Used ONLY if Firestore does not return a custom config.
 */
object DefaultPlanProvider {

    val FREE = PlanLimits(
        maxImagesPerPost = 1,
        maxStoryMedia = 2,             // Stories per day
        maxReelDurationSec = 10,
        allowedGroupCreations = 0,
        canSendVideoInGroups = false,
        canSendVoiceNotes = false,
        storageLimitGb = 1,
        adsEnabled = true,
        creatorMode = false,
        maxVideosPerDay = 0            // NEW — Free users cannot upload videos
    )

    val BASIC = PlanLimits(
        maxImagesPerPost = 5,
        maxStoryMedia = 2,
        maxReelDurationSec = 20,
        allowedGroupCreations = 1,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 5,
        adsEnabled = true,             // lighter ads
        creatorMode = false,
        maxVideosPerDay = 3            // NEW — you can change the number
    )

    val PREMIUM = PlanLimits(
        maxImagesPerPost = 10,
        maxStoryMedia = 1000,
        maxReelDurationSec = 60,
        allowedGroupCreations = 100,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 10,
        adsEnabled = false,
        creatorMode = true,
        maxVideosPerDay = 10           // NEW — practically unlimited
    )
}
