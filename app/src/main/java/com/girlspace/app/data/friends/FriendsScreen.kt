package com.girlspace.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.girlspace.app.data.friends.FriendRequestItem
import com.girlspace.app.data.friends.FriendUserSummary

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Friends", "Requests", "Connect", "Search")

    // ✅ show spinner only on “all empty + loading”
    val showGlobalLoading =
        uiState.isLoading &&
                uiState.friends.isEmpty() &&
                uiState.incomingRequests.isEmpty() &&
                uiState.suggestions.isEmpty() &&
                uiState.searchResults.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        uiState.error?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                // Friends tab – Messenger-style friend rows with 3-dot menu
                0 -> FriendsTab(
                    friends = uiState.friends,
                    followingIds = uiState.followingIds,
                    onViewProfile = { /* TODO: wire navigation */ },
                    onMessage = { /* TODO: wire chat navigation */ },
                    onFollow = viewModel::followUser,
                    onUnfollow = viewModel::unfollowUser,
                    onUnfriend = viewModel::unfriend,
                    onBlock = viewModel::blockUser
                )

                1 -> RequestsTab(
                    requests = uiState.incomingRequests,
                    onAccept = viewModel::acceptFriendRequest,
                    onReject = viewModel::rejectFriendRequest
                )

                2 -> SuggestionsTab(
                    suggestions = uiState.suggestions,
                    outgoingRequestIds = uiState.outgoingRequestIds,
                    friends = uiState.friends,
                    mutualCounts = uiState.mutualFriendsCounts,
                    onConfirm = viewModel::sendFriendRequest,
                    onDecline = viewModel::hideSuggestion
                )

                3 -> SearchTab(
                    uiState = uiState,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSendRequest = viewModel::sendFriendRequest,
                    outgoingRequestIds = uiState.outgoingRequestIds,
                    friends = uiState.friends
                )
            }

            if (showGlobalLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun FriendsTab(
    friends: List<FriendUserSummary>,
    followingIds: Set<String>,
    onViewProfile: (String) -> Unit,
    onMessage: (String) -> Unit,
    onFollow: (String) -> Unit,
    onUnfollow: (String) -> Unit,
    onUnfriend: (String) -> Unit,
    onBlock: (String) -> Unit
) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Your friends list will appear here.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(friends, key = { it.uid }) { friend ->
            var menuExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendAvatar(
                        name = friend.fullName,
                        photoUrl = friend.photoUrl
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = friend.fullName,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }

                        val isFollowing = followingIds.contains(friend.uid)

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View profile") },
                                onClick = {
                                    menuExpanded = false
                                    onViewProfile(friend.uid)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Message") },
                                onClick = {
                                    menuExpanded = false
                                    onMessage(friend.uid)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (isFollowing) "Unfollow" else "Follow")
                                },
                                onClick = {
                                    menuExpanded = false
                                    if (isFollowing) {
                                        onUnfollow(friend.uid)
                                    } else {
                                        onFollow(friend.uid)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Unfriend") },
                                onClick = {
                                    menuExpanded = false
                                    onUnfriend(friend.uid)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block") },
                                onClick = {
                                    menuExpanded = false
                                    onBlock(friend.uid)
                                }
                            )
                        }
                    }
                }
            }
            Divider()
        }
    }
}

@Composable
private fun FriendAvatar(
    name: String,
    photoUrl: String?
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "G",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<FriendRequestItem>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("You have no friend requests.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(requests, key = { it.fromUid }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = item.user.fullName.ifBlank { "User" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Button(onClick = { onAccept(item.fromUid) }) {
                        Text("Confirm")
                    }
                    OutlinedButton(onClick = { onReject(item.fromUid) }) {
                        Text("Decline")
                    }
                }
            }
            Divider()
        }
    }
}

@Composable
private fun SuggestionsTab(
    suggestions: List<FriendUserSummary>,
    outgoingRequestIds: Set<String>,
    friends: List<FriendUserSummary>,
    mutualCounts: Map<String, Int>,
    onConfirm: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No suggestions yet. New users will appear here.")
        }
        return
    }

    val friendIds = remember(friends) { friends.map { it.uid }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(suggestions, key = { it.uid }) { user ->
            val isFriend = friendIds.contains(user.uid)
            val hasRequested = outgoingRequestIds.contains(user.uid)
            val mutual = mutualCounts[user.uid] ?: 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                if (mutual > 0) {
                    Text(
                        text = if (mutual == 1) "1 mutual friend" else "$mutual mutual friends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    when {
                        isFriend -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Friends")
                            }
                        }

                        hasRequested -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Requested")
                            }
                        }

                        else -> {
                            Button(onClick = { onConfirm(user.uid) }) {
                                Text("Confirm")
                            }
                            OutlinedButton(onClick = { onDecline(user.uid) }) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }
            Divider()
        }
    }
}

@Composable
private fun SearchTab(
    uiState: FriendsUiState,
    onQueryChange: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    outgoingRequestIds: Set<String>,
    friends: List<FriendUserSummary>
) {
    val friendIds = remember(friends) { friends.map { it.uid }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search by name, email, or phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isSearchLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.searchQuery.trim().length < 3 -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results yet. Enter at least 3 characters.")
                }
            }

            uiState.searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No users found.")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(uiState.searchResults, key = { it.uid }) { user ->
                        val isFriend = friendIds.contains(user.uid)
                        val hasRequested = outgoingRequestIds.contains(user.uid)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = user.fullName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            val subtitleParts = listOfNotNull(
                                user.email,
                                user.phone
                            )
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                when {
                                    isFriend -> {
                                        OutlinedButton(onClick = {}, enabled = false) {
                                            Text("Friends")
                                        }
                                    }

                                    hasRequested -> {
                                        OutlinedButton(onClick = {}, enabled = false) {
                                            Text("Requested")
                                        }
                                    }

                                    else -> {
                                        Button(onClick = { onSendRequest(user.uid) }) {
                                            Text("Add friend")
                                        }
                                    }
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
