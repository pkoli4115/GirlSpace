package com.girlspace.app.core.plan

object PlanEnforcer {

    fun checkPostImages(limit: PlanLimits, selectedCount: Int): Boolean {
        return selectedCount <= limit.maxImagesPerPost
    }

    fun checkReelDuration(limit: PlanLimits, durationSec: Int): Boolean {
        return durationSec <= limit.maxReelDurationSec
    }

    fun checkStoryMedia(limit: PlanLimits, count: Int): Boolean {
        return count <= limit.maxStoryMedia
    }

    fun canCreateGroup(limit: PlanLimits, currentGroupsCreated: Int): Boolean {
        return currentGroupsCreated < limit.allowedGroupCreations
    }
}
