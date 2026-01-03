package com.girlspace.app.ui
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.google.firebase.firestore.FirebaseFirestore
import com.girlspace.app.data.groups.GroupsScope
import kotlinx.coroutines.tasks.await
import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.girlspace.app.data.chat.ChatScope
import com.girlspace.app.data.friends.FriendScope
import com.girlspace.app.security.InnerCircleSession
import com.girlspace.app.ui.billing.PremiumScreen
import com.girlspace.app.ui.chat.ChatScreenV2
import com.girlspace.app.ui.chat.ChatViewModel
import com.girlspace.app.ui.common.VibeMode
import com.girlspace.app.ui.feed.PostDetailScreen
import com.girlspace.app.ui.feed.SavedPostsScreen
import com.girlspace.app.ui.friends.FriendsScreen
import com.girlspace.app.ui.groups.GroupChatScreen
import com.girlspace.app.ui.groups.GroupsViewModel
import com.girlspace.app.ui.home.HomeRoot
import com.girlspace.app.ui.innercircle.InnerCircleEntryFlowScreen
import com.girlspace.app.ui.innercircle.InnerCircleShellScreen
import com.girlspace.app.ui.innercircle.lock.GuardInnerCircleRoute
import com.girlspace.app.ui.innercircle.lock.InnerCircleLockScreen
import com.girlspace.app.ui.innercircle.settings.InnerCircleChangePinScreen
import com.girlspace.app.ui.innercircle.settings.InnerCircleResetPinScreen
import com.girlspace.app.ui.innercircle.settings.InnerCircleSettingsScreen
import com.girlspace.app.ui.login.LoginScreen
import com.girlspace.app.ui.notifications.NotificationsScreen
import com.girlspace.app.ui.onboarding.MoodOnboardingScreen
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.profile.DeleteAccountScreen
import com.girlspace.app.ui.profile.ProfileScreen
import com.girlspace.app.ui.profile.UserMediaScreen
import com.girlspace.app.ui.profile.UserPostsScreen
import com.girlspace.app.ui.reels.create.ReelCaptureScreen
import com.girlspace.app.ui.reels.create.ReelCreateFromGalleryScreen
import com.girlspace.app.ui.reels.create.ReelGalleryPickScreen
import com.girlspace.app.ui.reels.create.ReelYoutubeScreen
import com.girlspace.app.ui.splash.SplashScreen
import com.girlspace.app.ui.splash.SplashViewModel
import com.girlspace.app.ui.theme.VibeTheme
import com.girlspace.app.utils.DeepLinkStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun GirlSpaceApp(navController: androidx.navigation.NavHostController) {

    // Read selected vibe ("serenity", "radiance", etc.) from DataStore
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val themeMode by onboardingViewModel.themeMode.collectAsState()
    val mood by onboardingViewModel.mood.collectAsState(initial = "serenity")

    val vibeMode = remember(themeMode, mood) {
        if (themeMode.trim().lowercase() != "mood") {
            VibeMode.Default
        } else {
            when (mood.trim().lowercase()) {
                "serenity" -> VibeMode.Serenity
                "radiance" -> VibeMode.Radiance
                "wisdom" -> VibeMode.Wisdom
                "pulse" -> VibeMode.Pulse
                "harmony" -> VibeMode.Harmony
                "ignite" -> VibeMode.Ignite
                else -> VibeMode.Default
            }
        }
    }

    // Apply global color scheme based on vibe
    VibeTheme(themeMode = themeMode) {
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

        // ✅ Share: text/link → open YouTube/link import screen (prefilled)
        LaunchedEffect(sharedText) {
            val text = sharedText
            if (!text.isNullOrBlank()) {
                val encoded = Uri.encode(text)
                navController.navigate("reelYoutube/$encoded") { launchSingleTop = true }
                DeepLinkStore.clearSharedText()
            }
        }

        // ✅ Share: video uri → open gallery upload screen with prefilled uri
        LaunchedEffect(sharedVideoUri) {
            val uriStr = sharedVideoUri
            if (!uriStr.isNullOrBlank()) {
                val encoded = Uri.encode(uriStr)
                navController.navigate("reelCreate/$encoded") { launchSingleTop = true }
                DeepLinkStore.clearSharedVideoUri()
            }
        }

        // ✅ Apply FLAG_SECURE ONLY for Inner Circle entry/shell
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route.orEmpty()

        SecureWindowForInnerCircle(
            enabled = currentRoute.startsWith("inner_circle_entry") ||
                    currentRoute.startsWith("inner_circle_shell") ||
                    currentRoute.startsWith("inner_chat") ||
                    currentRoute.startsWith("inner_chat_with_user") ||
                    currentRoute.startsWith("add_members")
        )

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
                    onShowOnboarding = {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onShowLogin = {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onShowHome = {
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
                        // ✅ lock Inner Circle session on logout
                        InnerCircleSession.lock()

                        // ✅ Ensure real sign-out happens
                        FirebaseAuth.getInstance().signOut()

                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = { navController.navigate("premium") },
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenChatFromFriends = { friendUid ->
                        navController.navigate("chat_with_user/$friendUid")
                    },
                    vibeMode = vibeMode
                )
            }

            composable("inner_circle_settings") {
                InnerCircleSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onChangePin = { navController.navigate("inner_circle_change_pin") },
                    onResetPin = { navController.navigate("inner_circle_reset_pin") }
                )
            }

            composable("inner_circle_change_pin") {
                InnerCircleChangePinScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }

            composable("inner_circle_reset_pin") {
                val context = LocalContext.current
                val store = remember { com.girlspace.app.security.InnerCircleLockStore(context.applicationContext) }
                val scope = rememberCoroutineScope()

                InnerCircleResetPinScreen(
                    onVerified = {
                        scope.launch {
                            store.clearPin()
                            InnerCircleSession.lock()

                            Toast.makeText(context, "PIN reset ✅. Please set a new PIN.", Toast.LENGTH_SHORT).show()

                            val target = Uri.encode("inner_circle_settings")
                            navController.navigate("inner_lock/$target") {
                                popUpTo("inner_circle_reset_pin") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            /* ---------------------------------------------------
                Notifications
            ---------------------------------------------------- */
            composable("notifications") {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { deepLink ->
                        when {
                            deepLink.startsWith("togetherly://chat/") -> {
                                val threadId = deepLink.substringAfterLast("/")
                                navController.navigate("chat/$threadId")
                            }

                            deepLink.startsWith("togetherly://reels/") -> {
                                val reelId = deepLink.substringAfterLast("/")
                                navController.navigate("reelsViewer/$reelId")
                            }
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
            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    onLogout = {
                        InnerCircleSession.lock()
                        FirebaseAuth.getInstance().signOut()

                        navController.navigate("login") {
                            popUpTo("home_root") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onUpgrade = { navController.navigate("premium") }
                )
            }

            composable(
                route = "profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
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
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                UserPostsScreen(userId = userId, navController = navController)
            }

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

            composable(
                route = "postDetail/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
                PostDetailScreen(postId = postId, navController = navController)
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
                arguments = listOf(navArgument("otherUid") { type = NavType.StringType })
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
                route = "reelYoutube/{prefill}",
                arguments = listOf(navArgument("prefill") { type = NavType.StringType })
            ) { backStackEntry ->
                val prefill = backStackEntry.arguments?.getString("prefill")?.let { Uri.decode(it) }
                ReelYoutubeScreen(
                    onBack = { navController.popBackStack() },
                    prefillUrl = prefill
                )
            }

            composable("inner_circle_entry") {
                GuardInnerCircleRoute(
                    navController = navController,
                    targetRoute = "inner_circle_entry"
                ) {
                    InnerCircleEntryFlowScreen(
                        onClose = { navController.popBackStack() },
                        onEntered = {
                            navController.navigate("inner_circle_shell") {
                                popUpTo("inner_circle_entry") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            composable("inner_circle_shell") {
                GuardInnerCircleRoute(
                    navController = navController,
                    targetRoute = "inner_circle_shell"
                ) {
                    InnerCircleShellScreen(
                        navController = navController,
                        onUpgrade = { navController.navigate("premium") }
                    )
                }
            }
            /* ---------------------------------------------------
                ADD MEMBERS (OWNER CAN REMOVE) — FIXED for INNER CIRCLE
            ---------------------------------------------------- */
            composable(route = "add_members/{groupId}/{scope}") { backStackEntry ->

                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                val scopeStr = backStackEntry.arguments?.getString("scope") ?: "public"

                val friendScope =
                    if (scopeStr == "inner") FriendScope.INNER_CIRCLE else FriendScope.PUBLIC

                // ---------- state ----------
                var showRemoveConfirm by remember { mutableStateOf(false) }
                var pendingFinalSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
                var pendingRemovedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

                var existingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var isOwnerOrAdmin by remember { mutableStateOf(false) }

                val groupVm: GroupsViewModel = hiltViewModel()
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid

                // ✅ CRITICAL: point GroupsViewModel to correct collection (groups vs ic_groups)
                LaunchedEffect(scopeStr) {
                    groupVm.setScope(
                        if (scopeStr == "inner") GroupsScope.INNER_CIRCLE else GroupsScope.PUBLIC
                    )
                }

                // ✅ Load members + owner/admin check from correct collection
                LaunchedEffect(groupId, scopeStr, currentUid) {
                    existingIds = groupVm.getGroupMemberIds(groupId)

                    isOwnerOrAdmin = try {
                        val collection = if (scopeStr == "inner") "ic_groups" else "groups"
                        val snap = FirebaseFirestore.getInstance()
                            .collection(collection)
                            .document(groupId)
                            .get()
                            .await()

                        val createdBy = snap.getString("createdBy")
                        val admins = (snap.get("adminIds") as? List<*>) ?: emptyList<Any?>()

                        createdBy == currentUid || admins.contains(currentUid)
                    } catch (e: Exception) {
                        // fallback (should rarely hit)
                        val group = groupVm.groups.value.firstOrNull { it.id == groupId }
                        group?.createdBy == currentUid || group?.isOwner == true
                    }
                }

                val lockedIds = setOfNotNull(currentUid) // cannot uncheck yourself
                val disabled = if (isOwnerOrAdmin) lockedIds else existingIds
                val preselected = existingIds

                // ---------- CONFIRM DIALOG ----------
                if (showRemoveConfirm) {
                    AlertDialog(
                        onDismissRequest = {
                            showRemoveConfirm = false
                            pendingFinalSelection = emptySet()
                            pendingRemovedIds = emptySet()
                        },
                        title = { Text("Remove members?") },
                        text = { Text("You are removing ${pendingRemovedIds.size} member(s) from this group.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showRemoveConfirm = false
                                groupVm.updateGroupMembers(groupId, pendingFinalSelection)
                                pendingFinalSelection = emptySet()
                                pendingRemovedIds = emptySet()
                                navController.navigateUp()
                            }) { Text("Remove") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showRemoveConfirm = false
                                pendingFinalSelection = emptySet()
                                pendingRemovedIds = emptySet()
                            }) { Text("Cancel") }
                        }
                    )
                }

                // ---------- UI ----------
                if (scopeStr == "inner") {
                    GuardInnerCircleRoute(
                        navController = navController,
                        targetRoute = "add_members/$groupId/$scopeStr"
                    ) {
                        FriendsScreen(
                            scope = friendScope,
                            selectionMode = true,
                            disabledIds = disabled,
                            preselectedIds = preselected,
                            selectionHint = if (isOwnerOrAdmin) "Tip: Uncheck to remove members" else null,
                            onBack = { navController.popBackStack() },
                            onSelectionDone = { selectedIds ->
                                if (isOwnerOrAdmin) {
                                    val removed = existingIds - selectedIds
                                    if (removed.isNotEmpty()) {
                                        pendingFinalSelection = selectedIds
                                        pendingRemovedIds = removed
                                        showRemoveConfirm = true
                                    } else {
                                        groupVm.updateGroupMembers(groupId, selectedIds)
                                        navController.navigateUp()
                                    }
                                } else {
                                    val toAdd = selectedIds - existingIds
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("picked_member_ids", toAdd.toList())
                                    navController.navigateUp()
                                }
                            }
                        )
                    }
                } else {
                    FriendsScreen(
                        scope = friendScope,
                        selectionMode = true,
                        disabledIds = disabled,
                        preselectedIds = preselected,
                        selectionHint = if (isOwnerOrAdmin) "Tip: Uncheck to remove members" else null,
                        onBack = { navController.popBackStack() },
                        onSelectionDone = { selectedIds ->
                            if (isOwnerOrAdmin) {
                                val removed = existingIds - selectedIds
                                if (removed.isNotEmpty()) {
                                    pendingFinalSelection = selectedIds
                                    pendingRemovedIds = removed
                                    showRemoveConfirm = true
                                } else {
                                    groupVm.updateGroupMembers(groupId, selectedIds)
                                    navController.navigateUp()
                                }
                            } else {
                                val toAdd = selectedIds - existingIds
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("picked_member_ids", toAdd.toList())
                                navController.navigateUp()
                            }
                        }
                    )
                }
            }
            composable(
                route = "reelCreate/{videoUri}",
                arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoUriEncoded = backStackEntry.arguments?.getString("videoUri") ?: return@composable
                val videoUriDecoded = Uri.decode(videoUriEncoded)

                ReelCreateFromGalleryScreen(
                    videoUriString = videoUriDecoded,
                    onBack = { navController.popBackStack() },
                    onCreated = { reelId ->
                        navController.navigate("reelsViewer/$reelId") {
                            launchSingleTop = true
                            popUpTo("reelGalleryPick") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = "inner_chat/{threadId}",
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable

                GuardInnerCircleRoute(
                    navController = navController,
                    targetRoute = "inner_chat/$threadId"
                ) {
                    val chatVm: ChatViewModel = hiltViewModel()

                    LaunchedEffect(threadId) {
                        chatVm.setScope(ChatScope.INNER_CIRCLE)
                        chatVm.ensureThreadSelected(threadId)
                    }

                    ChatScreenV2(
                        threadId = threadId,
                        onBack = { navController.popBackStack() },
                        vm = chatVm
                    )
                }
            }

            composable(
                route = "inner_chat_with_user/{otherUid}",
                arguments = listOf(navArgument("otherUid") { type = NavType.StringType })
            ) { backStackEntry ->
                val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable

                GuardInnerCircleRoute(
                    navController = navController,
                    targetRoute = "inner_chat_with_user/$otherUid"
                ) {
                    val chatVm: ChatViewModel = hiltViewModel()

                    LaunchedEffect(otherUid) {
                        chatVm.setScope(ChatScope.INNER_CIRCLE)
                        chatVm.startChatWithUser(otherUid, scope = ChatScope.INNER_CIRCLE)
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
            }

            composable(
                route = "inner_lock/{target}",
                arguments = listOf(navArgument("target") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedTarget = backStackEntry.arguments?.getString("target") ?: ""
                val targetRoute = Uri.decode(encodedTarget)

                InnerCircleLockScreen(
                    onBack = { navController.popBackStack() },
                    onUnlocked = {
                        InnerCircleSession.unlock()

                        if (targetRoute.isNotBlank()) {
                            navController.navigate(targetRoute) {
                                popUpTo("inner_lock/$encodedTarget") { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onForgotPin = {
                        navController.navigate("inner_circle_reset_pin")
                    }
                )
            }

            /* ---------------------------------------------------
                10) 1-1 CHAT BY THREAD ID
            ---------------------------------------------------- */
            composable(
                route = "chat/{threadId}",
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
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

@Composable
private fun SecureWindowForInnerCircle(enabled: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(enabled) {
        val window = activity?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                InnerCircleSession.lock()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
