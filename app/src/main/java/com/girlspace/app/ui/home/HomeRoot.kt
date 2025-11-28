package com.girlspace.app.ui.home

import com.girlspace.app.ui.feed.FeedScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.facebook.login.LoginManager
import com.girlspace.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth


enum class HomeTab {
    Feed, Reels, Chats, Groups, Profile
}

@Composable
fun HomeRoot(
    onLogout: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenProfile: () -> Unit
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

                /** -------------------------------
                 *  🔥 NEW: FULL PROFILE SCREEN NAVIGATION
                 *  (instead of mini internal ProfileTab)
                 *  -------------------------------- */
                NavigationBarItem(
                    selected = currentTab == HomeTab.Profile,
                    onClick = {
                        currentTab = HomeTab.Profile

                        // Navigate outside HomeRoot → Full ProfileScreen
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
                HomeTab.Groups -> GroupsTab()

                /**
                 * ✅ Profile Tab: DO NOTHING HERE
                 * It immediately navigates out of HomeRoot
                 * → Full ProfileScreen()
                 */
                HomeTab.Profile -> {}
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Chats coming soon…")
    }
}

@Composable
private fun GroupsTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Groups coming soon…")
    }
}
