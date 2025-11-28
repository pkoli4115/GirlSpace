package com.girlspace.app.core.plan

/**
 * Unified limits for every feature depending on user's plan.
 * Loaded dynamically from Firestore when possible.
 */

data class PlanLimits(
    val maxImagesPerPost: Int,
    val maxStoryMedia: Int,
    val maxReelDurationSec: Int,
    val allowedGroupCreations: Int,
    val canSendVideoInGroups: Boolean,
    val canSendVoiceNotes: Boolean,
    val storageLimitGb: Int,
    val adsEnabled: Boolean,
    val creatorMode: Boolean       // Included in Premium+
)
