package com.girlspace.app.notifications

import android.content.Context

/**
 * Stores currently-open chat thread id to suppress notifications
 * when user is already viewing that thread.
 */
object ActiveChatTracker {
    private const val PREFS = "togetherly_chat_state"
    private const val KEY_ACTIVE_THREAD = "active_thread_id"

    fun setActiveThread(context: Context, threadId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_THREAD, threadId ?: "")
            .apply()
    }

    fun getActiveThread(context: Context): String? {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_THREAD, null)
            ?.trim()
            ?: return null
        return v.takeIf { it.isNotEmpty() }
    }

    fun clear(context: Context) = setActiveThread(context, null)
}
