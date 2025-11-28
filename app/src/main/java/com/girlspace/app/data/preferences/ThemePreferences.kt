package com.girlspace.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level DataStore instance
private val Context.themeDataStore by preferencesDataStore(
    name = "girlspace_theme"
)

class ThemePreferences(private val context: Context) {

    companion object {
        private val KEY_MOOD = stringPreferencesKey("mood")
        private val KEY_MODE = stringPreferencesKey("theme_mode") // mood / daily / random / static
        private val KEY_STATIC_THEME = stringPreferencesKey("static_theme")
        private val KEY_FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
    }

    // --- FLOWS ---
    val mood: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_MOOD] ?: "calm"
    }

    val themeMode: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_MODE] ?: "mood"
    }

    val staticTheme: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_STATIC_THEME] ?: "feminine_default"
    }

    val firstLaunchDone: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH_DONE] ?: false
    }

    // --- SAVE FUNCTIONS ---
    suspend fun saveMood(value: String) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_MOOD] = value
        }
    }

    suspend fun saveThemeMode(value: String) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_MODE] = value
        }
    }

    suspend fun saveStaticTheme(value: String) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_STATIC_THEME] = value
        }
    }

    suspend fun markOnboardingDone() {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH_DONE] = true
        }
    }
    suspend fun saveFirstLaunchDone(value: Boolean) {
        context.themeDataStore.edit {
            it[KEY_FIRST_LAUNCH_DONE] = value
        }
        suspend fun setFirstLaunchDone(done: Boolean) {
            context.themeDataStore.edit { prefs ->
                prefs[KEY_FIRST_LAUNCH_DONE] = done
            }
        }

    }

}
