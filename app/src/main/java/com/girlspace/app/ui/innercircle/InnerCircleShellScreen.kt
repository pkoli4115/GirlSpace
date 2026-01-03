package com.girlspace.app.ui.innercircle
import com.girlspace.app.data.groups.GroupsScope
import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.girlspace.app.data.friends.FriendScope
import com.girlspace.app.ui.chat.ChatsScreen
import com.girlspace.app.ui.friends.FriendsScreen
import com.girlspace.app.ui.groups.GroupsScreen
import com.girlspace.app.data.chat.ChatScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleShellScreen(
    navController: NavHostController,
    onUpgrade: () -> Unit = {}
) {
 val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Chats", "Communities", "My Circle")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inner Circle") },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate("inner_circle_settings")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Inner Circle Settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ChatsScreen(
                    scope = ChatScope.INNER_CIRCLE,
                    onOpenThread = { thread ->
                        navController.navigate("inner_chat/${thread.id}")
                        { launchSingleTop = true }
                    }
                )


                1 -> GroupsScreen(
                    navController = navController,
                    onUpgrade = onUpgrade,
                    scope = GroupsScope.INNER_CIRCLE
                )



                2 -> FriendsScreen(
                    profileUserId = null,
                    initialTab = "connections",
                    onOpenChat = { friendUid ->
                        navController.navigate("inner_chat_with_user/$friendUid")
                        { launchSingleTop = true }
                    },
                    onOpenProfile = { friendUid ->
                        navController.navigate("profile/$friendUid") { launchSingleTop = true }
                    },
                    onBack = {},
                    scope = FriendScope.INNER_CIRCLE
                )
            }
        }
    }
}
