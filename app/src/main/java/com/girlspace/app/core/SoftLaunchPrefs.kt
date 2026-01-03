package com.girlspace.app.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "soft_launch_prefs")

object SoftLaunchPrefs {
    private val KEY_SEEN_UPLOAD_BANNER = booleanPreferencesKey("seen_upload_banner")

    fun seenUploadBannerFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_SEEN_UPLOAD_BANNER] ?: false }

    suspend fun markUploadBannerSeen(context: Context) {
        context.dataStore.edit { prefs -> prefs[KEY_SEEN_UPLOAD_BANNER] = true }
    }
}
