package com.girlspace.app.data.notifications

import com.google.firebase.Timestamp

enum class NotificationLevel {
    ALL,
    IMPORTANT_ONLY,
    OFF
}

enum class BirthdayNotifyMode {
    OFF,
    SELF_ONLY,
    FRIENDS_ONLY,
    SELF_AND_FRIENDS
}

data class NotificationPrefs(
    val chat: NotificationLevel = NotificationLevel.ALL,
    val social: NotificationLevel = NotificationLevel.IMPORTANT_ONLY, // comments + friend requests
    val inspiration: NotificationLevel = NotificationLevel.OFF,
    val festivals: NotificationLevel = NotificationLevel.OFF,
    val birthdays: BirthdayNotifyMode = BirthdayNotifyMode.SELF_ONLY,

    // Quiet hours (optional)
    val quietHoursEnabled: Boolean = false,
    val quietStart: String = "22:00",   // HH:mm
    val quietEnd: String = "08:00",     // HH:mm
    val timezone: String = "Asia/Kolkata",

    val updatedAt: Timestamp? = null
)
