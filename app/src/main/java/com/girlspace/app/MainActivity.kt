package com.girlspace.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.CallbackManager
import com.girlspace.app.ui.GirlSpaceApp
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.theme.VibeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // Used by LoginScreen for Facebook login
        val fbCallbackManager: CallbackManager by lazy {
            CallbackManager.Factory.create()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 Tell Android: don't reserve space for system bars, let us draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 🔹 Make status bar icons dark on light backgrounds (like Facebook)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContent {
            // Get current vibe from DataStore via OnboardingViewModel
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            val themeMode by onboardingViewModel.themeMode.collectAsState()

            // Apply global color scheme based on selected vibe
            VibeTheme(themeMode = themeMode) {
                GirlSpaceApp()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fbCallbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
