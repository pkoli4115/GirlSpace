package com.girlspace.app.ui.splash

import androidx.lifecycle.ViewModel
import com.girlspace.app.data.preferences.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    prefs: ThemePreferences
) : ViewModel() {

    val firstLaunchDone = prefs.firstLaunchDone
}
