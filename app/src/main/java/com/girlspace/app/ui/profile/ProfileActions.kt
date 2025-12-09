package com.girlspace.app.ui.profile

/**
 * All user intents originating from the ProfileScreen UI.
 * The UI calls viewModel.onAction(action) with these.
 */
sealed interface ProfileAction {

    // Lifecycle
    data class LoadProfile(val userId: String) : ProfileAction
    object Refresh : ProfileAction

    // Top interaction bar (other profile)
    object OnFollowClicked : ProfileAction
    object OnMessageClicked : ProfileAction
    object OnAddToGroupClicked : ProfileAction

    // Self profile actions
    object OnEditProfileClicked : ProfileAction
    object OnSettingsClicked : ProfileAction
    object OnShareProfileClicked : ProfileAction

    // Tab changes
    data class OnTabSelected(val tab: ProfileTab) : ProfileAction

    // Safety / abuse flow
    object OnReportClicked : ProfileAction
    data class SubmitReport(val reason: String, val details: String?) : ProfileAction
    object OnBlockClicked : ProfileAction
    object ConfirmBlockUser : ProfileAction
    object DismissBlockConfirm : ProfileAction

    // Clear one-off messages after UI shows them
    object ClearMessage : ProfileAction
}
