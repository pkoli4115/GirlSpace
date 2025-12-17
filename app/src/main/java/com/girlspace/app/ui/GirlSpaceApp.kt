package com.girlspace.app.ui
import com.girlspace.app.ui.reels.create.ReelCaptureScreen
import com.girlspace.app.ui.reels.create.ReelGalleryPickScreen
import com.girlspace.app.ui.reels.create.ReelYoutubeScreen
import com.girlspace.app.ui.reels.create.ReelCreateFromGalleryScreen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.girlspace.app.ui.billing.PremiumScreen
import com.girlspace.app.ui.chat.ChatScreenV2
import com.girlspace.app.ui.chat.ChatViewModel
import com.girlspace.app.ui.feed.PostDetailScreen
import com.girlspace.app.ui.feed.SavedPostsScreen
import com.girlspace.app.ui.friends.FriendsScreen
import com.girlspace.app.ui.groups.GroupChatScreen
import com.girlspace.app.ui.home.HomeRoot
import com.girlspace.app.ui.login.LoginScreen
import com.girlspace.app.ui.notifications.NotificationsScreen
import com.girlspace.app.ui.onboarding.MoodOnboardingScreen
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.profile.DeleteAccountScreen
import com.girlspace.app.ui.profile.ProfileScreen
import com.girlspace.app.ui.profile.UserMediaScreen
import com.girlspace.app.ui.profile.UserPostsScreen
import com.girlspace.app.ui.splash.SplashScreen
import com.girlspace.app.ui.splash.SplashViewModel
import com.girlspace.app.ui.theme.VibeTheme
import com.girlspace.app.utils.DeepLinkStore

@Composable
fun GirlSpaceApp() {
    // Read selected vibe ("serenity", "radiance", etc.) from DataStore
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val themeMode by onboardingViewModel.themeMode.collectAsState()

    // Apply global color scheme based on vibe
    VibeTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val openThreadId by DeepLinkStore.openChatThreadId.collectAsState()
        val sharedText by DeepLinkStore.sharedText.collectAsState()
        val sharedVideoUri by DeepLinkStore.sharedVideoUri.collectAsState()


        // Deep link handling
        LaunchedEffect(openThreadId) {
            val tid = openThreadId
            if (!tid.isNullOrBlank()) {
                navController.navigate("chat/$tid") { launchSingleTop = true }
                DeepLinkStore.clearChat()
            }

        }
// ✅ Share: text/link → open YouTube/link import screen
        LaunchedEffect(sharedText) {
            val text = sharedText
            if (!text.isNullOrBlank()) {
                navController.navigate("reelYoutube") { launchSingleTop = true }
                // The screen will read DeepLinkStore.sharedText (next step if needed)
                DeepLinkStore.clearSharedText()
            }
        }

// ✅ Share: video uri → open gallery upload screen with prefilled uri
        LaunchedEffect(sharedVideoUri) {
            val uriStr = sharedVideoUri
            if (!uriStr.isNullOrBlank()) {
                navController.navigate("reelCreateFromGallery") { launchSingleTop = true }
                // The screen will read DeepLinkStore.sharedVideoUri
                // (we clear after the screen reads it; for now keep it until screen opens)
                // If you prefer immediate clear, tell me — but keeping it avoids races.
            }
        }

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
                    }
                )
            }

            /* ---------------------------------------------------
                4) HOME ROOT
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
                        navController.navigate("chat_with_user/$friendUid")
                    }
                )
            }

            /* ---------------------------------------------------
                Notifications
            ---------------------------------------------------- */
            composable("notifications") {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { deepLink ->
                        if (deepLink.startsWith("togetherly://chat/")) {
                            val threadId = deepLink.substringAfterLast("/")
                            navController.navigate("chat/$threadId")
                        }
                    }
                )
            }

            /* ---------------------------------------------------
                5) PREMIUM
            ---------------------------------------------------- */
            composable(route = "premium") {
                PremiumScreen(navController = navController)
            }

            /* ---------------------------------------------------
                6) PROFILE
            ---------------------------------------------------- */
            // Self profile
            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = { navController.navigate("premium") }
                )
            }

            // Other-user profile
            composable(
                route = "profile/{userId}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")

                ProfileScreen(
                    navController = navController,
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = { navController.navigate("premium") },
                    profileUserId = userId
                )
            }

            composable(
                route = "user_posts/{userId}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                UserPostsScreen(
                    userId = userId,
                    navController = navController
                )
            }

            // User-specific media (posts / reels / photos)
            composable(
                route = "user_media/{userId}/{type}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                val type = backStackEntry.arguments?.getString("type") ?: "posts"

                UserMediaScreen(
                    userId = userId,
                    type = type,
                    navController = navController
                )
            }

            // Friends / Followers / Following
            composable(
                route = "friends?userId={userId}&tab={tab}",
                arguments = listOf(
                    navArgument("userId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = "friends"
                    }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")
                val tab = backStackEntry.arguments?.getString("tab")

                FriendsScreen(
                    profileUserId = userId,
                    initialTab = tab,
                    onOpenChat = { friendUid ->
                        navController.navigate("chat_with_user/$friendUid")
                    },
                    onOpenProfile = { friendUid ->
                        navController.navigate("profile/$friendUid")
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            /* ---------------------------------------------------
                6b) SAVED POSTS
            ---------------------------------------------------- */
            composable(route = "savedPosts") {
                SavedPostsScreen(navController = navController)
            }

            // Single post detail
            composable(
                route = "postDetail/{postId}",
                arguments = listOf(
                    navArgument("postId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
                PostDetailScreen(
                    postId = postId,
                    navController = navController
                )
            }

            /* ---------------------------------------------------
                7) DELETE ACCOUNT
            ---------------------------------------------------- */
            composable(route = "deleteAccount") {
                DeleteAccountScreen(
                    onBack = { navController.popBackStack() },
                    onAccountDeleted = {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            /* ---------------------------------------------------
                8) GROUP CHAT
            ---------------------------------------------------- */
            composable(
                route = "group_chat/{groupId}/{groupName}",
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("groupName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                val rawName = backStackEntry.arguments?.getString("groupName") ?: "Group"
                val groupName = Uri.decode(rawName)

                GroupChatScreen(
                    groupId = groupId,
                    groupName = groupName,
                    onBack = { navController.popBackStack() }
                )
            }

            /* ---------------------------------------------------
                9) 1-1 CHAT BY USER ID
            ---------------------------------------------------- */
            composable(
                route = "chat_with_user/{otherUid}",
                arguments = listOf(
                    navArgument("otherUid") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable

                val chatVm: ChatViewModel = hiltViewModel()

                LaunchedEffect(otherUid) {
                    chatVm.startChatWithUser(otherUid)
                }

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
            composable(
                route = "reelsViewer/{startReelId}",
                arguments = listOf(navArgument("startReelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val reelId = backStackEntry.arguments?.getString("startReelId") ?: return@composable

                com.girlspace.app.ui.reels.ReelsViewerScreen(
                    startReelId = reelId,
                    onBack = { navController.popBackStack() }
                )
            }

            /* ---------------------------------------------------
               REELS CREATE ROUTES (to prevent crash)
            ---------------------------------------------------- */
            composable("reelCapture") {
                com.girlspace.app.ui.reels.ReelsCreateStubRoute(
                    title = "Record (Camera)",
                    onDone = { navController.popBackStack() }
                )
            }

            composable("reelGalleryPick") {
                com.girlspace.app.ui.reels.ReelsCreateStubRoute(
                    title = "Pick from Gallery",
                    onDone = { navController.popBackStack() }
                )
            }

            composable("reelYoutube") {
                com.girlspace.app.ui.reels.ReelsCreateStubRoute(
                    title = "Add YouTube URL",
                    onDone = { navController.popBackStack() }
                )
            }
            composable("reelCapture") {
                ReelCaptureScreen(onBack = { navController.popBackStack() })
            }

            composable("reelGalleryPick") {
                ReelGalleryPickScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("reelYoutube") {
                ReelYoutubeScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = "reelCreate/{videoUri}",
                arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoUri = backStackEntry.arguments?.getString("videoUri") ?: return@composable
                ReelCreateFromGalleryScreen(
                    videoUriString = videoUri,
                    onBack = { navController.popBackStack() },
                    onCreated = { reelId ->
                        // After upload → open viewer on the new reel
                        navController.navigate("reelsViewer/$reelId") {
                            launchSingleTop = true
                        }
                    }
                )
            }


            /* ---------------------------------------------------
                10) 1-1 CHAT BY THREAD ID
            ---------------------------------------------------- */
            composable(
                route = "chat/{threadId}",
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable

                ChatScreenV2(
                    threadId = threadId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
