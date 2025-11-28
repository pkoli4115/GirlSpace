package com.girlspace.app.core.plan

/**
 * If Firestore has no custom config, we use these defaults.
 */

object DefaultPlanProvider {

    val FREE = PlanLimits(
        maxImagesPerPost = 1,
        maxStoryMedia = 2,                       // 2 per day
        maxReelDurationSec = 10,                 // 10 seconds
        allowedGroupCreations = 0,               // cannot create groups
        canSendVideoInGroups = false,
        canSendVoiceNotes = false,
        storageLimitGb = 1,
        adsEnabled = true,
        creatorMode = false
    )

    val BASIC = PlanLimits(
        maxImagesPerPost = 5,
        maxStoryMedia = 2,
        maxReelDurationSec = 20,
        allowedGroupCreations = 1,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 5,
        adsEnabled = true,                       // lighter ads
        creatorMode = false
    )

    val PREMIUM = PlanLimits(
        maxImagesPerPost = 10,
        maxStoryMedia = 1000,                    // unlimited practically
        maxReelDurationSec = 60,
        allowedGroupCreations = 100,
        canSendVideoInGroups = true,
        canSendVoiceNotes = true,
        storageLimitGb = 10,
        adsEnabled = false,
        creatorMode = true                       // INCLUDED
    )
}
