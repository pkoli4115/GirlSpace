package com.girlspace.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.girlspace.app.ui.billing.PremiumScreen
import com.girlspace.app.ui.home.HomeRoot
import com.girlspace.app.ui.login.LoginScreen
import com.girlspace.app.ui.onboarding.MoodOnboardingScreen
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.profile.ProfileScreen
import com.girlspace.app.ui.splash.SplashScreen
import com.girlspace.app.ui.splash.SplashViewModel
import com.girlspace.app.ui.theme.GirlSpaceTheme

@Composable
fun GirlSpaceApp() {
    GirlSpaceTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {

            /* ---------------------------------------------------
                1) SPLASH
            ---------------------------------------------------- */
            composable(route = "splash") {
                val vm: SplashViewModel = hiltViewModel()
                val firstLaunchDone by vm.firstLaunchDone.collectAsState(initial = false)

                SplashScreen(
                    firstLaunchDone = firstLaunchDone,

                    // NEW USER → Login screen
                    onShowOnboarding = {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    },

                    // RETURNING USER → Home
                    onShowLogin = {
                        navController.navigate("home_root") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            /* ---------------------------------------------------
                2) LOGIN
            ---------------------------------------------------- */
            composable(route = "login") {
                LoginScreen(navController = navController)
            }

            /* ---------------------------------------------------
                3) ONBOARDING (Mood / Theme)
            ---------------------------------------------------- */
            composable(route = "onboarding") {
                val vm: OnboardingViewModel = hiltViewModel()

                MoodOnboardingScreen(
                    viewModel = vm,
                    onNext = {
                        vm.saveFirstLaunchDone()
                        navController.navigate("home_root") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            /* ---------------------------------------------------
                4) HOME ROOT (Feed / Reels / Chats / Groups / Profile)
            ---------------------------------------------------- */
            composable(route = "home_root") {
                HomeRoot(
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = {
                        navController.navigate("premium")
                    },
                    onOpenProfile = {
                        navController.navigate("profile")
                    }
                )
            }

            /* ---------------------------------------------------
                5) PREMIUM UPGRADE SCREEN
            ---------------------------------------------------- */
            composable(route = "premium") {
                PremiumScreen(navController = navController)
            }

            /* ---------------------------------------------------
                6) PROFILE SCREEN
            ---------------------------------------------------- */
            composable(route = "profile") {
                ProfileScreen(
                    navController = navController,
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = {
                        navController.navigate("premium")
                    }
                )
            }
        }
    }
}
