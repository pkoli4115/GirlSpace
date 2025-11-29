package com.girlspace.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.preferences.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: ThemePreferences
) : ViewModel() {

    /**
     * Legacy mood string. Kept for backwards-compatibility with any older code.
     * Not strictly needed for the new platform-theme onboarding, but harmless.
     */
    val mood = prefs.mood.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "calm"
    )

    /**
     * Selected global theme "vibe" for the app.
     *
     * We now treat this as the PLATFORM THEME key, e.g.:
     *  - "facebook"
     *  - "instagram"
     *  - "linkedin"
     *  - "tiktok"
     *  - "whatsapp"
     *  - "youtube"
     *
     * Your app-level Theme can observe this and switch primary colors accordingly.
     */
    val themeMode = prefs.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // Default to a neutral, trusted theme if nothing chosen yet.
        initialValue = "serenity"
    )

    /** Whether onboarding (vibe selection) has already been completed. */
    val firstLaunchDone = prefs.firstLaunchDone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    fun saveMood(value: String) {
        viewModelScope.launch {
            prefs.saveMood(value)
        }
    }

    /** Save the selected theme key, e.g. "facebook", "instagram", etc. */
    fun saveThemeMode(value: String) {
        viewModelScope.launch {
            prefs.saveThemeMode(value)
        }
    }

    /** Mark onboarding as completed in DataStore. */
    fun saveFirstLaunchDone() {
        viewModelScope.launch {
            prefs.saveFirstLaunchDone(true)
        }
    }

    // Backwards-compat alias so any old call to setFirstLaunchDone() still compiles.
    fun setFirstLaunchDone() = saveFirstLaunchDone()
}
