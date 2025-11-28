package com.girlspace.app.ui.groups

/**
 * UI model for a GirlSpace group / community.
 */
data class GroupItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val createdBy: String = "",
    val memberCount: Long = 0L,
    val isMember: Boolean = false,
    val isOwner: Boolean = false
)
