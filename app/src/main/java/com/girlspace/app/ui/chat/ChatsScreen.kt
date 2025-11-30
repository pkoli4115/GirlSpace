package com.girlspace.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.girlspace.app.data.chat.ChatThread
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// Simple user summary used by both ChatsScreen + ChatViewModel
data class ChatUserSummary(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
    val mutualCount: Int,
    // presence info for green/grey dot
    val isOnline: Boolean = false,
    val lastActiveAt: Long? = null
)

/**
 * Messenger-style Chats list:
 * - Header with small search icon
 * - Optional search bar (when icon tapped)
 * - Auto-scrolling horizontal row of friends & suggestions
 * - Vertical list of chat threads
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    onOpenThread: (ChatThread) -> Unit = {},
    vm: ChatViewModel = viewModel()
) {
    val friends by vm.friends.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val allThreads by vm.threads.collectAsState()
    val error by vm.errorMessage.collectAsState()
    val lastStartedThread by vm.lastStartedThread.collectAsState()

    val context = LocalContext.current
    val myId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Local search state (screen only)
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    // When a new thread is started from avatar tap, navigate to it
    LaunchedEffect(lastStartedThread) {
        lastStartedThread?.let { thread ->
            onOpenThread(thread)
            vm.consumeLastStartedThread()
        }
    }

    // Merge friends + suggestions into one row (Messenger-style)
    val friendIds = friends.map { it.uid }.toSet()
    val combinedPeople: List<ChatUserSummary> =
        friends + suggestions.filter { s -> s.uid !in friendIds }

    // Filter people row by search text (name)
    val filteredPeople: List<ChatUserSummary> = remember(combinedPeople, searchText) {
        val q = searchText.trim().lowercase()
        if (q.isBlank()) {
            combinedPeople
        } else {
            combinedPeople.filter { person ->
                person.displayName.lowercase().contains(q)
            }
        }
    }

    // Fallback avatars from existing threads if we have no people yet
    val fallbackPeople: List<ChatUserSummary> = remember(combinedPeople, allThreads, myId) {
        if (combinedPeople.isNotEmpty()) {
            emptyList()
        } else {
            allThreads.map { thread ->
                val name = thread.otherUserName(myId)
                ChatUserSummary(
                    uid = thread.otherUserId(myId),
                    displayName = name,
                    avatarUrl = null,
                    mutualCount = 0
                )
            }
        }
    }

    // Final list for the avatars row
    val peopleRow: List<ChatUserSummary> =
        if (filteredPeople.isNotEmpty()) filteredPeople else fallbackPeople

    // Filter threads by search (name or last message)
    val threads: List<ChatThread> = remember(allThreads, searchText, myId) {
        val q = searchText.trim().lowercase()
        if (q.isBlank()) {
            allThreads
        } else {
            allThreads.filter { thread ->
                val name = thread.otherUserName(myId).lowercase()
                val lastMsg = thread.lastMessage.lowercase()
                name.contains(q) || lastMsg.contains(q)
            }
        }
    }

    // Presence map for threads list
    val presenceMap: Map<String, Boolean> = remember(friends, suggestions) {
        (friends + suggestions).associate { it.uid to it.isOnline }
    }

    // Auto-scrolling state for avatars row
    val avatarListState = rememberLazyListState()

    LaunchedEffect(peopleRow.size) {
        if (peopleRow.size > 1) {
            while (true) {
                avatarListState.animateScrollBy(2f)

                val lastVisibleIndex =
                    avatarListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                if (lastVisibleIndex >= peopleRow.lastIndex) {
                    avatarListState.scrollToItem(0)
                }

                delay(40L) // controls auto-scroll speed
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            // Search bar (shown only if active)
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search for contacts") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Friends + suggestions row
            if (peopleRow.isNotEmpty()) {
                Text(
                    text = "Friends & suggestions",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    state = avatarListState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(peopleRow, key = { it.uid }) { user ->
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    // Start / open chat (thread + navigate via lastStartedThread)
                                    vm.startChatWithUser(user.uid)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ChatUserAvatar(
                                name = user.displayName,
                                avatarUrl = user.avatarUrl,
                                isOnline = user.isOnline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Chat threads list
            if (threads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchText.isBlank())
                            "No chats yet. Start a conversation from suggestions above."
                        else
                            "No chats match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = threads,
                        key = { it.id }
                    ) { thread ->
                        val otherId = thread.otherUserId(myId)
                        val isOnline = presenceMap[otherId] ?: false

                        ChatThreadRow(
                            thread = thread,
                            myId = myId,
                            isOnline = isOnline,
                            onClick = {
                                vm.selectThread(thread)
                                onOpenThread(thread)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Circular avatar for people row (friends & suggestions) with status dot.
 */
@Composable
private fun ChatUserAvatar(
    name: String,
    avatarUrl: String?,
    isOnline: Boolean
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // main circle
        Box(
            modifier = Modifier
                .matchParentSize()
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
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
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

        // status dot (bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isOnline) Color(0xFF4CAF50) else Color(0xFFB0BEC5)
                )
        )
    }
}

/**
 * Single chat thread row in main list.
 */
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    myId: String,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val displayName = thread.otherUserName(myId)
    val lastMessage = thread.lastMessage.ifBlank { "Say hi 👋" }

    val timeText =
        if (thread.lastTimestamp > 0) {
            val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            df.format(Date(thread.lastTimestamp))
        } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // avatar with online/offline dot
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) Color(0xFF4CAF50) else Color(0xFFB0BEC5)
                    )
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (thread.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = thread.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
