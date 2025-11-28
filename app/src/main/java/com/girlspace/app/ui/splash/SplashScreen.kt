package com.girlspace.app.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SplashScreen(
    firstLaunchDone: Boolean,
    onShowOnboarding: () -> Unit,
    onShowLogin: () -> Unit
) {
    // If user completed onboarding → go straight to home
    LaunchedEffect(Unit) {
        if (firstLaunchDone) {
            onShowLogin()
        } else {
            onShowOnboarding()
        }
    }
}
