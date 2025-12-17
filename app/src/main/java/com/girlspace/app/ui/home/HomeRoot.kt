package com.girlspace.app.ui.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import com.girlspace.app.ui.chat.ChatViewModel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.girlspace.app.core.plan.PlanLimitsRepository
import com.girlspace.app.ui.chat.ChatsScreen   // ✅ use ChatsScreen here
import com.girlspace.app.ui.feed.FeedScreen
import com.girlspace.app.ui.groups.GroupsScreen
import com.girlspace.app.ui.friends.FriendsScreen   // ✅ NEW IMPORT

// We keep "Feed" as the enum name for backward compatibility,
// but this is effectively your "Home" tab.
enum class HomeTab {
    Feed, Reels, Chats, Friends, Communities, Menu
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRoot(
    navController: NavHostController,
    onLogout: () -> Unit,            // used in Menu
    onUpgrade: () -> Unit,           // used by Communities + Menu
    onOpenProfile: () -> Unit,       // opens full ProfileScreen route (used in Menu)
    onOpenChatFromFriends: (String) -> Unit   // NEW: friendUid → open chat
) {

    // 🔹 Start listening to plan limits once the user is in Home
    LaunchedEffect(Unit) {
        PlanLimitsRepository.start()
    }

    var currentTab by remember { mutableStateOf(HomeTab.Feed) }
    var showCreatePost by remember { mutableStateOf(false) }
    val chatVm: ChatViewModel = viewModel()
    val threads by chatVm.threads.collectAsState()
    val totalUnreadChats = remember(threads) { threads.sumOf { it.unreadCount } }

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 🔄 Pager state for horizontal swipe between main tabs
    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal
    ) {
        HomeTab.values().size
    }
    val scope = rememberCoroutineScope()

    // Keep currentTab in sync with pager swipes
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (currentTab.ordinal != page) {
            currentTab = HomeTab.values()[page]
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                NotificationBell {
                    navController.navigate("notifications")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                // Feed (HOME)
                NavigationBarItem(
                    selected = currentTab == HomeTab.Feed,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Feed.ordinal)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home, // ✅ HOME icon
                            contentDescription = "Home",
                            tint = if (currentTab == HomeTab.Feed) selectedColor else unselectedColor
                        )
                    },
                    alwaysShowLabel = false
                )

                // Reels
                NavigationBarItem(
                    selected = currentTab == HomeTab.Reels,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Reels.ordinal)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = "Reels",
                            tint = if (currentTab == HomeTab.Reels) selectedColor else unselectedColor
                        )
                    },
                    alwaysShowLabel = false
                )

                // Chats
                NavigationBarItem(
                    selected = currentTab == HomeTab.Chats,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Chats.ordinal)
                        }
                    },
                    icon = {
                        BadgedNavIcon(
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = "Chats",
                                    tint = if (currentTab == HomeTab.Chats) selectedColor else unselectedColor
                                )
                            },
                            badgeCount = totalUnreadChats
                        )
                    },

                            alwaysShowLabel = false
                )

                // Friends (two-person outline)
                NavigationBarItem(
                    selected = currentTab == HomeTab.Friends,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Friends.ordinal)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = "Friends",
                            tint = if (currentTab == HomeTab.Friends) selectedColor else unselectedColor
                        )
                    },
                    alwaysShowLabel = false
                )

                // Communities (three-person groups icon)
                NavigationBarItem(
                    selected = currentTab == HomeTab.Communities,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Communities.ordinal)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = "Communities",
                            tint = if (currentTab == HomeTab.Communities) selectedColor else unselectedColor
                        )
                    },
                    alwaysShowLabel = false
                )

                // Menu (Profile, Settings, etc.)
                NavigationBarItem(
                    selected = currentTab == HomeTab.Menu,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(HomeTab.Menu.ordinal)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu",
                            tint = if (currentTab == HomeTab.Menu) selectedColor else unselectedColor
                        )
                    },
                    alwaysShowLabel = false
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (HomeTab.values()[page]) {
                    HomeTab.Feed -> FeedScreen(
                        navController = navController,
                        isCreatePostOpen = showCreatePost,
                        onDismissCreatePost = { showCreatePost = false },
                        onOpenCreatePost = { showCreatePost = true }
                    )


                    HomeTab.Reels -> com.girlspace.app.ui.reels.ReelsScreen(navController = navController)

                    HomeTab.Chats -> ChatsScreen(
                        onOpenThread = { thread ->
                            // ChatScreen uses threadId argument
                            navController.navigate("chat/${thread.id}")
                        }
                    )

                    HomeTab.Friends -> FriendsScreen(
                        profileUserId = null,          // current user
                        initialTab = "friends",        // default tab when coming from bottom nav
                        onOpenChat = onOpenChatFromFriends,
                        onOpenProfile = { friendUid ->
                            navController.navigate("profile/$friendUid")
                        }
                        // onBack left as default; no back arrow when opened from bottom nav
                    )
                    HomeTab.Communities -> GroupsScreen(
                        navController = navController,
                        onUpgrade = onUpgrade
                    )

                    HomeTab.Menu -> MenuTab(
                        onOpenProfile = onOpenProfile,
                        onUpgrade = onUpgrade,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

/**
 * Simple placeholder for Reels tab content.
 * We’ll replace with real Reels feed later.
 */
@Composable
private fun ReelsTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Reels coming soon…")
    }
}

// ---- Friends Tab (legacy stub) ----
// (kept as-is but no longer used; safe to keep for now)

private enum class FriendsSubTab {
    Friends, Requests, Suggestions, Search
}

@Composable
private fun FriendsTab() {
    var subTab by remember { mutableStateOf(FriendsSubTab.Friends) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Top row: Friends | Requests | Suggestions | Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FriendsSubTab.values().forEach { tab ->
                val isSelected = subTab == tab
                Text(
                    text = when (tab) {
                        FriendsSubTab.Friends -> "Friends"
                        FriendsSubTab.Requests -> "Requests"
                        FriendsSubTab.Suggestions -> "Suggestions"
                        FriendsSubTab.Search -> "Search"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { subTab = tab }
                )
            }
        }

        Divider()

        // Content area for the selected sub-tab
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (subTab) {
                FriendsSubTab.Friends -> {
                    Text(
                        "Your friends list will appear here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                FriendsSubTab.Requests -> {
                    Text(
                        "Incoming and outgoing friend requests will appear here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                FriendsSubTab.Suggestions -> {
                    Text(
                        "Smart friend suggestions will appear here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                FriendsSubTab.Search -> {
                    Text(
                        "Search for users by name, email, or username.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgedNavIcon(
    icon: @Composable () -> Unit,
    badgeCount: Int
) {
    Box {
        icon()

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}
@Composable
private fun NotificationBell(
    onClick: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    val uid = auth.currentUser?.uid
    var unreadCount by remember(uid) { mutableStateOf(0) }

    // Attach listener only when uid exists; otherwise keep badge at 0 but still show bell icon.
    androidx.compose.runtime.DisposableEffect(uid) {
        if (uid.isNullOrBlank()) {
            unreadCount = 0
            onDispose { }
        } else {
            val reg = firestore.collection("users")
                .document(uid)
                .collection("notifications")
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        // fail-open: don’t crash or hide icon
                        unreadCount = 0
                        return@addSnapshotListener
                    }

                    val docs = snap?.documents.orEmpty()

                    // Backward compatible:
                    // - read:Boolean (your intended)
                    // - isRead:Boolean (common variant)
                    // - missing field => treat as unread (so it still shows in badge)
                    unreadCount = docs.count { d ->
                        val read = d.getBoolean("read")
                        val isRead = d.getBoolean("isRead")
                        val isSeen = d.getBoolean("seen")

                        when {
                            read != null -> read == false
                            isRead != null -> isRead == false
                            isSeen != null -> isSeen == false
                            else -> true // no flag -> consider unread (safer for badge)
                        }
                    }
                }

            onDispose { reg.remove() }
        }
    }

    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications"
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


// ---- Menu Tab ----

@Composable
private fun MenuTab(
    onOpenProfile: () -> Unit,
    onUpgrade: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text(
            text = "Menu",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        Divider()

        // Profile
        ListItem(
            headlineContent = { Text("Profile") },
            supportingContent = { Text("View and edit your profile") },
            modifier = Modifier.clickable { onOpenProfile() }
        )

        Divider()

        ListItem(
            headlineContent = { Text("Communities") },
            supportingContent = { Text("Manage your groups and communities") },
            modifier = Modifier.clickable {
                // Wire this to a communities management route later if needed
            }
        )

        Divider()

        ListItem(
            headlineContent = { Text("Manage Subscriptions") },
            supportingContent = { Text("Unlock group chats, video calls and more") },
            modifier = Modifier.clickable { onUpgrade() }
        )

        Divider()

        ListItem(
            headlineContent = { Text("Settings") },
            supportingContent = { Text("Privacy, security and app preferences") },
            modifier = Modifier.clickable {
                // TODO: navigate to SettingsScreen
            }
        )

        Divider()

        ListItem(
            headlineContent = {
                Text(
                    text = "Logout",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            modifier = Modifier.clickable { onLogout() }
        )
    }
}
