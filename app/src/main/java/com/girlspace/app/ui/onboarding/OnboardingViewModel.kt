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

    // current mood (“calm”, “romantic”, etc.)
    val mood = prefs.mood.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "calm"
    )

    // theme mode (“mood”, “daily”, “random”, “static_feminine_default”, etc.)
    val themeMode = prefs.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "daily"
    )

    // whether onboarding has already been completed
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

    fun saveThemeMode(value: String) {
        viewModelScope.launch {
            prefs.saveThemeMode(value)
        }
    }

    /** Mark onboarding as completed in DataStore */
    fun saveFirstLaunchDone() {
        viewModelScope.launch {
            prefs.saveFirstLaunchDone(true)
        }
    }

    // ✅ Backwards-compat alias so any old call to setFirstLaunchDone() still compiles
    fun setFirstLaunchDone() = saveFirstLaunchDone()
}
