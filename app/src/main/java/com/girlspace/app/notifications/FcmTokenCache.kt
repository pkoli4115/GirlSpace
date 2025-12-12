package com.girlspace.app.data.notifications

import android.content.Context

/**
 * Stores the latest FCM token when user isn't logged-in yet.
 * Once login happens, we flush it to Firestore and clear it.
 */
object FcmTokenCache {
    private const val PREFS = "togetherly_push"
    private const val KEY_TOKEN = "cached_fcm_token"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun get(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }
}
