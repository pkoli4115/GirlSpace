package com.girlspace.app.ui.groups
import androidx.compose.foundation.layout.widthIn
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.girlspace.app.R
import com.girlspace.app.data.chat.ChatMessage
import android.app.Activity
import android.view.WindowManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.girlspace.app.ui.common.findActivity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    groupName: String,
    onBack: (() -> Unit)? = null,
    vm: GroupChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val inputText by vm.inputText.collectAsState()
    val isSending by vm.isSending.collectAsState()
    val error by vm.error.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val selectedForReaction by vm.selectedMessageForReaction.collectAsState()

    val context = LocalContext.current
    val currentUid = vm.currentUserId()
    val activity = context.findActivity()
    val firestore = remember { FirebaseFirestore.getInstance() }

    var isInnerCircleGroup by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        try {
            val snap = firestore.collection("groups")
                .document(groupId)
                .get()
                .await()

            val scope = snap.getString("scope")
                ?: snap.getString("visibility")
                ?: ""

            isInnerCircleGroup =
                snap.getBoolean("isInnerCircle") == true ||
                        scope.equals("inner", ignoreCase = true) ||
                        scope.contains("inner", ignoreCase = true)
        } catch (_: Exception) {
            isInnerCircleGroup = false
        }
    }

    DisposableEffect(isInnerCircleGroup) {
        val window = activity?.window
        if (isInnerCircleGroup) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Start listening to this group
    LaunchedEffect(groupId) {
        vm.start(groupId)
    }

    // 🔊 Bee / pop sound
    val reactionPlayer = remember {
        MediaPlayer.create(context, R.raw.reaction_bee)
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                reactionPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    // Full emoji picker state
    var showEmojiPicker by remember { mutableStateOf(false) }
    var emojiPickerTargetId by remember { mutableStateOf<String?>(null) }

    // Track last notified message for potential local notification (optional)
    var lastNotifiedMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages.size) {
        val last = messages.lastOrNull()
        if (last != null &&
            last.senderId != null &&
            last.senderId != currentUid &&
            last.id.isNotBlank() &&
            last.id != lastNotifiedMessageId
        ) {
            // If you have a notification helper for groups, call it here.
            lastNotifiedMessageId = last.id
        }
    }

    // ─────────────────────────────────────────────
    // Simple "online" heuristic for the group
    // (any message in the last 2 min = active)
    // ─────────────────────────────────────────────
    val groupActiveNow = remember(messages) {
        val now = System.currentTimeMillis()
        val threshold = 2 * 60 * 1000L // 2 minutes
        messages.any { msg ->
            val ts = msg.createdAt.toDate().time
            now - ts <= threshold
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = groupName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (groupActiveNow) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)) // green dot
                                )
                            }
                        }
                        when {
                            isTyping -> {
                                Text(
                                    text = "Someone is typing…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            groupActiveNow -> {
                                Text(
                                    text = "Active now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    val mine = msg.senderId == currentUid

                    GroupMessageBubble(
                        message = msg,
                        mine = mine,
                        showReactionBar = !mine && selectedForReaction == msg.id,
                        onLongPress = {
                            if (!mine) {
                                try {
                                    if (reactionPlayer.isPlaying) {
                                        reactionPlayer.seekTo(0)
                                    }
                                    reactionPlayer.start()
                                } catch (_: Exception) {
                                }
                                vm.openReactionPicker(msg.id)
                                emojiPickerTargetId = msg.id
                            }
                        },
                        onReactionSelected = { emoji ->
                            vm.reactToMessage(msg.id, emoji)
                            vm.closeReactionPicker()
                            emojiPickerTargetId = null
                        },
                        onMoreReactions = {
                            emojiPickerTargetId = msg.id
                            showEmojiPicker = true
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = inputText,
                    onValueChange = { vm.updateInput(it) },
                    placeholder = { Text("Type a message…") },
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.sendMessage() },
                    enabled = inputText.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }

    if (showEmojiPicker && emojiPickerTargetId != null) {
        EmojiPickerDialog(
            onDismiss = {
                showEmojiPicker = false
                emojiPickerTargetId = null
                vm.closeReactionPicker()
            },
            onEmojiSelected = { emoji ->
                emojiPickerTargetId?.let { msgId ->
                    vm.reactToMessage(msgId, emoji)
                }
                vm.closeReactionPicker()
                showEmojiPicker = false
                emojiPickerTargetId = null
            }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Group chat") },
            text = { Text(error ?: "") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    message: ChatMessage,
    mine: Boolean,
    showReactionBar: Boolean,
    onLongPress: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onMoreReactions: () -> Unit
) {
    val timeText = message.createdAt.toDate().let {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            AnimatedVisibility(visible = showReactionBar) {
                ReactionBar(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .align(if (mine) Alignment.End else Alignment.Start),
                    onSelect = onReactionSelected,
                    onMore = onMoreReactions
                )
            }

            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (mine) 16.dp else 0.dp,
                            bottomEnd = if (mine) 0.dp else 16.dp
                        )
                    )
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (!mine) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message.senderName.firstOrNull()?.uppercase() ?: "G",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.senderName.ifBlank { "GirlSpace user" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                val body = when {
                    message.text.isNotBlank() -> message.text
                    message.mediaUrl != null -> "[Media]"
                    else -> ""
                }

                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mine) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Reactions line
            AnimatedVisibility(visible = message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    message.reactions.values.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// Re-use reaction bar & emoji picker

@Composable
private fun ReactionBar(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onMore: () -> Unit
) {
    val quickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            quickReactions.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clickable { onSelect(emoji) }
                )
            }

            Text(
                text = "➕",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clickable { onMore() }
            )
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val allEmojis = listOf(
        "😀","😁","😂","🤣","😃","😄","😅","😆",
        "😉","😊","🥰","😍","🤩","😘","😗","😙",
        "😚","🙂","🤗","🤔","😐","😑","🙄","😏",
        "😣","😥","😮","🤤","😪","😫","😭","😤",
        "😡","😠","🤬","🤯","😳","🥵","🥶","😎",
        "🤓","😇","🥳","🤠","😴","🤢","🤮","🤧"
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("More reactions") },
        text = {
            Column {
                Text(
                    text = "Tap an emoji to react",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                val chunked = allEmojis.chunked(8)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { onEmojiSelected(emoji) }
                            )
                        }
                    }
                }
            }
        }
    )
}
