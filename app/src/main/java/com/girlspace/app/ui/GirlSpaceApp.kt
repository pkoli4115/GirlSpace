package com.girlspace.app.ui
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.girlspace.app.ui.chat.ChatViewModel
import com.girlspace.app.ui.feed.SavedPostsScreen
import com.girlspace.app.ui.home.HomeRoot
import com.girlspace.app.ui.login.LoginScreen
import com.girlspace.app.ui.onboarding.MoodOnboardingScreen
import com.girlspace.app.ui.profile.DeleteAccountScreen
import com.girlspace.app.ui.profile.ProfileScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.girlspace.app.ui.billing.PremiumScreen
import com.girlspace.app.ui.chat.ChatScreenV2
import com.girlspace.app.ui.groups.GroupChatScreen
import com.girlspace.app.ui.home.HomeRoot
import com.girlspace.app.ui.login.LoginScreen
import com.girlspace.app.ui.onboarding.MoodOnboardingScreen
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.profile.DeleteAccountScreen
import com.girlspace.app.ui.profile.ProfileScreen
import com.girlspace.app.ui.splash.SplashScreen
import com.girlspace.app.ui.splash.SplashViewModel
import com.girlspace.app.ui.theme.VibeTheme

@Composable
fun GirlSpaceApp() {
    // Read selected vibe ("serenity", "radiance", etc.) from DataStore
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val themeMode by onboardingViewModel.themeMode.collectAsState()

    // Apply global color scheme based on vibe
    VibeTheme(themeMode = themeMode) {
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
                        // Screen itself already calls saveFirstLaunchDone(),
                        // but calling again is harmless.
                        vm.saveFirstLaunchDone()
                        navController.navigate("home_root") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            /* ---------------------------------------------------
                4) HOME ROOT (Feed / Reels / Chats / Friends /
                   Communities / Menu)
            ---------------------------------------------------- */
            composable(route = "home_root") {
                HomeRoot(
                    navController = navController,
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
                    },
                    onOpenChatFromFriends = { friendUid ->
                        // NEW: navigate to a chat route by user id
                        navController.navigate("chat_with_user/$friendUid")
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
            // Self profile (existing)
            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    onLogout = { /* same as before */ },
                    onUpgrade = { /* same as before */ }
                )
            }

// Other-user profile (NEW)
            composable(
                route = "profile/{userId}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")

                ProfileScreen(
                    navController = navController,
                    onLogout = { /* same as before */ },
                    onUpgrade = { /* same as before */ },
                    profileUserId = userId      // <— this flips to OTHER mode when != current user
                )
            }

            /* ---------------------------------------------------
                6b) SAVED POSTS SCREEN
            ---------------------------------------------------- */
            composable(route = "savedPosts") {
                SavedPostsScreen(navController = navController)
            }


            /* ---------------------------------------------------
                7) DELETE ACCOUNT SCREEN
            ---------------------------------------------------- */
            composable(route = "deleteAccount") {
                DeleteAccountScreen(
                    onBack = { navController.popBackStack() },
                    onAccountDeleted = {
                        // After delete, send user to login and clear back stack
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            /* ---------------------------------------------------
                8) GROUP CHAT SCREEN
            ---------------------------------------------------- */
            composable(
                route = "group_chat/{groupId}/{groupName}"
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                val groupName =
                    backStackEntry.arguments?.getString("groupName") ?: "Group"

                GroupChatScreen(
                    groupId = groupId,
                    groupName = groupName,
                    onBack = { navController.popBackStack() }
                )
            }
            /* ---------------------------------------------------
               9) 1-1 CHAT BY USER ID (Friends → Message)
            ---------------------------------------------------- */
            composable(
                route = "chat_with_user/{otherUid}"
            ) { backStackEntry ->
                val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable

                // Use Hilt to get the existing ChatViewModel
                val chatVm: ChatViewModel = hiltViewModel()

                // Ensure or create a thread for this friend
                LaunchedEffect(otherUid) {
                    chatVm.startChatWithUser(otherUid)
                }

                // Observe when the thread is ready
                val selectedThread by chatVm.selectedThread.collectAsState()
                val thread = selectedThread

                if (thread != null) {
                    ChatScreenV2(
                        threadId = thread.id,
                        onBack = { navController.popBackStack() },
                        vm = chatVm
                    )
                }
            }

            /* -----------------------------
  * 9) 1-1 CHAT SCREEN (NEW)
  * ----------------------------- */
            composable(
                route = "chat/{threadId}"
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId")
                    ?: return@composable

                ChatScreenV2(
                    threadId = threadId,
                    onBack = { navController.popBackStack() }
                    // if your ChatScreenV2 needs vm or other params, pass them here as well
                    // vm = chatVm
                )
            }


        }
    }
}
