package com.girlspace.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.girlspace.app.ui.chat.ChatsScreen
import com.girlspace.app.ui.feed.FeedScreen
import com.girlspace.app.ui.groups.GroupsScreen

enum class HomeTab {
    Feed, Reels, Chats, Groups, Profile
}

@Composable
fun HomeRoot(
    navController: NavHostController,
    onLogout: () -> Unit,       // reserved for future use (e.g. header menu)
    onUpgrade: () -> Unit,      // used by Groups + Profile → PremiumScreen
    onOpenProfile: () -> Unit   // opens full ProfileScreen route
) {
    var currentTab by remember { mutableStateOf(HomeTab.Feed) }
    var showCreatePost by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == HomeTab.Feed,
                    onClick = { currentTab = HomeTab.Feed },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Feed") },
                    label = { Text("Feed") }
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.Reels,
                    onClick = { currentTab = HomeTab.Reels },
                    icon = { Icon(Icons.Filled.Movie, contentDescription = "Reels") },
                    label = { Text("Reels") }
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.Chats,
                    onClick = { currentTab = HomeTab.Chats },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.Groups,
                    onClick = { currentTab = HomeTab.Groups },
                    icon = { Icon(Icons.Filled.Group, contentDescription = "Groups") },
                    label = { Text("Groups") }
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.Profile,
                    onClick = {
                        currentTab = HomeTab.Profile
                        // Navigate to full ProfileScreen outside HomeRoot
                        onOpenProfile()
                    },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == HomeTab.Feed) {
                FloatingActionButton(
                    onClick = { showCreatePost = true }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create post")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (currentTab) {
                HomeTab.Feed -> FeedScreen(
                    isCreatePostOpen = showCreatePost,
                    onDismissCreatePost = { showCreatePost = false }
                )

                HomeTab.Reels -> ReelsTab()

                HomeTab.Chats -> ChatsTab()

                HomeTab.Groups -> GroupsScreen(
                    navController = navController,
                    onUpgrade = onUpgrade
                )


                // Profile tab just triggers navigation via onOpenProfile()
                HomeTab.Profile -> {
                    // Intentionally empty – content comes from NavGraph "profile" route
                }
            }
        }
    }
}

@Composable
private fun ReelsTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Reels coming soon…")
    }
}

@Composable
private fun ChatsTab() {
    // Use the real ChatsScreen instead of placeholder text
    ChatsScreen()
}
