package com.girlspace.app.ui.chat
import com.girlspace.app.data.chat.ChatScope
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.combinedClickable
import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

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
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    scope: ChatScope = ChatScope.PUBLIC,
    onOpenThread: (ChatThread) -> Unit = {},
    vm: ChatViewModel = viewModel()
) {
    val friends by vm.friends.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val allThreads by vm.threads.collectAsState()
    val totalUnread = remember(allThreads) { allThreads.sumOf { it.unreadCount } }

    val error by vm.errorMessage.collectAsState()
    val lastStartedThread by vm.lastStartedThread.collectAsState()
    LaunchedEffect(scope) {
        vm.setScope(scope)
    }

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedThreadIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    fun enterSelection(threadId: String) {
        isSelectionMode = true
        selectedThreadIds = setOf(threadId)
    }

    fun toggleSelection(threadId: String) {
        selectedThreadIds =
            if (selectedThreadIds.contains(threadId)) selectedThreadIds - threadId
            else selectedThreadIds + threadId

        if (selectedThreadIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedThreadIds = emptySet()
    }

    val context = LocalContext.current
    val myId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val nameCache = remember { mutableStateMapOf<String, String>() } // uid -> name
    // Local search state (screen only)
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val mutedIds by vm.mutedThreadIds.collectAsState()
    var actionThread by remember { mutableStateOf<ChatThread?>(null) }
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    // When a new thread is started from avatar tap, navigate to it
    LaunchedEffect(lastStartedThread) {
        lastStartedThread?.let { thread ->
            onOpenThread(thread)
            vm.clearLastStartedThread()
        }
    }
    BackHandler(enabled = isSelectionMode) {
        clearSelection()
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
    val peopleRowRaw: List<ChatUserSummary> =
        if (filteredPeople.isNotEmpty()) filteredPeople else fallbackPeople

// ✅ Dedupe by uid to avoid duplicate keys + repeated avatars
    val peopleRow: List<ChatUserSummary> = remember(peopleRowRaw) {
        peopleRowRaw.distinctBy { it.uid }
    }


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

    // Presence map for threads list (1-1 online dot)
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
            // ✅ Optional: removes extra automatic insets if your UI has too much top space
            // If this line errors, delete it.
            topBar = {
                if (!isSelectionMode) {
                    // ✅ NORMAL TOP BAR (your current one, unchanged behavior)
                    TopAppBar(
                        // If this line errors, delete it.
                        windowInsets = WindowInsets(0, 0, 0, 0),

                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Chats",
                                    style = MaterialTheme.typography.titleLarge
                                )

                                if (totalUnread > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (totalUnread > 99) "99+" else totalUnread.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
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
                } else {
                    // ✅ WHATSAPP STYLE SELECTION BAR (NO dialog)
                    val selectedThreads = remember(allThreads, selectedThreadIds) {
                        allThreads.filter { selectedThreadIds.contains(it.id) }
                    }

                    val anyMuted = selectedThreads.any { mutedIds.contains(it.id) }
                    val anyGroup = selectedThreads.any { it.isGroup() }
                    val allOneToOne = selectedThreads.isNotEmpty() && selectedThreads.all { !it.isGroup() }

                    TopAppBar(
                        // If this line errors, delete it.
                        windowInsets = WindowInsets(0, 0, 0, 0),

                        navigationIcon = {
                            IconButton(onClick = { clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        title = {
                            Text(
                                text = selectedThreadIds.size.toString(),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        actions = {
                            // 🔕 Mute / Unmute
                            IconButton(
                                onClick = {
                                    val newMuted = !anyMuted
                                    selectedThreads.forEach { t ->
                                        vm.setThreadMuted(t.id, newMuted)
                                    }
                                    clearSelection()
                                }
                            ) {
                                Icon(
                                    imageVector = if (anyMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = if (anyMuted) "Unmute" else "Mute"
                                )
                            }

                            // 📌 Pin / Unpin (UI ready; wire later to Firestore if you want)
                            IconButton(
                                onClick = {
                                    vm.toggleThreadsPinned(selectedThreadIds)
                                    clearSelection()
                                }
                            ) {
                                Icon(Icons.Filled.PushPin, contentDescription = "Pin/Unpin")
                            }
                            // 🗑 Delete (for me) — UI action (wire later to actual delete logic)
                            IconButton(
                                onClick = {
                                    vm.deleteThreadsForMe(selectedThreadIds)
                                    clearSelection()
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }


                            // 🚫 Block (1-1 only)
                            if (allOneToOne) {
                                IconButton(
                                    onClick = {
                                        // TODO: vm.blockUserFromThread(selectedThreads.first())
                                        clearSelection()
                                    }
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = "Block")
                                }
                            }

                            // 🚪 Leave group (only if any selected is group)
                            if (anyGroup) {
                                IconButton(
                                    onClick = {
                                        // TODO: vm.leaveGroupThreads(selectedThreadIds)
                                        clearSelection()
                                    }
                                ) {
                                    Text("Exit")
                                }
                            }
                        }
                    )
                }
            }
        ) {paddingValues ->
     Column(
         modifier = Modifier
             .fillMaxSize()
             .padding(paddingValues)
             .padding(horizontal = 12.dp)
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
                    itemsIndexed(
                        items = peopleRow,
                        key = { index, user -> "${user.uid}_$index" } // ✅ guaranteed unique
                    ) { _, user ->
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.startChatWithUser(user.uid) },
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
                // 🔒 HARD SAFETY: ensure no duplicate thread IDs reach LazyColumn
                val safeThreads = remember(threads) {
                    threads.distinctBy { it.id }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = safeThreads,
                        key = { index, thread ->
                            // 🔒 GUARANTEED unique per composition frame
                            "${thread.id}_$index"
                        }
                    ) { _, thread ->
                    val otherId = thread.otherUserId(myId)
                        val isOnline = presenceMap[otherId] ?: false

                        val isSelected = selectedThreadIds.contains(thread.id)
                        ChatThreadRow(
                            thread = thread,
                            myId = myId,
                            scope = scope,
                            isOnline = isOnline,
                            nameCache = nameCache,
                            isMuted = mutedIds.contains(thread.id),
                            isSelected = isSelected,
                            onToggleMute = { mute ->
                                // keep existing mute logic intact
                                vm.setThreadMuted(thread.id, mute)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(thread.id)
                                } else {
                                    vm.selectThread(thread)
                                    onOpenThread(thread)
                                }
                            },
                            onLongPress = {
                                if (!isSelectionMode) {
                                    enterSelection(thread.id)
                                } else {
                                    toggleSelection(thread.id)
                                }
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
 * Single chat thread row (1-1 + group support).
 *
 * - 1-1: single avatar + online dot + last message
 * - Group: stacked avatars + “You, A, B +N” + “X online • last message”
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    myId: String,
    scope: ChatScope,
    isOnline: Boolean,
    isMuted: Boolean,
    isSelected: Boolean,
    onToggleMute: (Boolean) -> Unit,
    nameCache: MutableMap<String, String>,
    onClick: () -> Unit,
    onLongPress: () -> Unit
)
{
    val firestore = remember { FirebaseFirestore.getInstance() }

    var title by remember(thread.id) { mutableStateOf(thread.otherUserName(myId)) }
    var isGroup by remember(thread.id) { mutableStateOf(false) }
    var groupOnlineCount by remember(thread.id) { mutableStateOf(0) }
    var avatarUrls by remember(thread.id) { mutableStateOf<List<String>>(emptyList()) }

    // Load participants, names, avatars, and presence for groups
    LaunchedEffect(thread.id) {
        try {
            val threadCollection = if (scope == ChatScope.INNER_CIRCLE) "ic_chatThreads" else "chatThreads"

            val doc = firestore.collection(threadCollection)
                .document(thread.id)
                .get()
                .await()

            val participants =
                (doc.get("participants") as? List<*>)?.filterIsInstance<String>()
                    ?: listOfNotNull(thread.userA, thread.userB).distinct()

            isGroup = participants.size > 2

            if (!isGroup) {
                val otherUid = thread.otherUserId(myId)

                // ✅ Use cached name if we have it (prevents "GirlSpace user" flicker on scroll)
                val cached = nameCache[otherUid]
                if (!cached.isNullOrBlank()) {
                    title = cached
                    return@LaunchedEffect
                }

                // fallback once (only until first fetch completes)
                title = thread.otherUserName(myId)

                try {
                    val userDoc = firestore.collection("users")
                        .document(otherUid)
                        .get()
                        .await()

                    val displayName =
                        userDoc.getString("displayName")
                            ?: userDoc.getString("name")
                            ?: userDoc.getString("fullName")
                            ?: userDoc.getString("username")

                    if (!displayName.isNullOrBlank()) {
                        nameCache[otherUid] = displayName
                        title = displayName
                    }
                } catch (_: Exception) {
                }

                return@LaunchedEffect
            }

            val names = mutableListOf<String>()
            val avatars = mutableListOf<String>()
            var onlineCount = 0

            for (uid in participants) {
                // Load user profile
                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (userDoc.exists()) {
                    val displayName = userDoc.getString("displayName")
                        ?: userDoc.getString("name")
                        ?: userDoc.getString("fullName")
                        ?: userDoc.getString("username")
                        ?: "GirlSpace user"

                    names += if (uid == myId) "You" else displayName

                    val avatar = userDoc.getString("photoUrl")
                    if (!avatar.isNullOrBlank()) {
                        avatars += avatar
                    }
                }

                // Load presence
                val statusDoc = firestore.collection("user_status")
                    .document(uid)
                    .get()
                    .await()

                val online = statusDoc.getBoolean("online") ?: false
                if (online) onlineCount++
            }

            avatarUrls = avatars

            title = when {
                names.size <= 3 -> names.joinToString(", ")
                else -> "${names.take(3).joinToString(", ")} +${names.size - 3}"
            }

            groupOnlineCount = onlineCount
        } catch (e: Exception) {
            Log.e("ChatThreadRow", "Failed to load group info", e)
            isGroup = false
            title = thread.otherUserName(myId)
        }
    }

    val lastMessage = thread.lastMessage.ifBlank { "Say hi 👋" }

    val timeText =
        if (thread.lastTimestamp > 0) {
            val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            df.format(Date(thread.lastTimestamp))
        } else ""

    val subtitle = if (isGroup && groupOnlineCount > 0) {
        "$groupOnlineCount online • $lastMessage"
    } else {
        lastMessage
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPress() }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar: 1-1 uses old avatar+dot, group uses stacked avatars
        if (isGroup) {
            GroupAvatarStack(avatarUrls)
        } else {
            // avatar with online/offline dot (legacy behavior)
            Box(
                modifier = Modifier.size(40.dp),
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
                        text = title.firstOrNull()?.uppercase() ?: "G",
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
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
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
            if (isMuted) {
                Text(
                    text = "🔕",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
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

/**
 * Group avatar stack: up to 2 photos + "+N"
 */
@Composable
private fun GroupAvatarStack(urls: List<String>) {
    Box(modifier = Modifier.size(40.dp)) {
        val first = urls.getOrNull(0)
        val second = urls.getOrNull(1)
        val extra = (urls.size - 2).coerceAtLeast(0)

        if (first != null) {
            AsyncImage(
                model = first,
                contentDescription = "Group avatar 1",
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        if (second != null) {
            AsyncImage(
                model = second,
                contentDescription = "Group avatar 2",
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        if (extra > 0) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
