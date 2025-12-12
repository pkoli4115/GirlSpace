package com.girlspace.app.moderation

/**
 * Generic send state for any moderated text send action.
 * You can reuse this in Chat, Feed, Reels, etc.
 */
sealed class ModerationSendState {
    object Idle : ModerationSendState()
    object Sending : ModerationSendState()
    object PendingModeration : ModerationSendState() // waiting for Cloud Function
    data class Error(val message: String) : ModerationSendState()
}
