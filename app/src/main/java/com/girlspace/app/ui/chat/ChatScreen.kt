package com.girlspace.app.ui.chat
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.girlspace.app.R
import com.girlspace.app.data.chat.ChatMessage
import com.girlspace.app.ui.chat.components.ReactionBar
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: String,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val selectedThread by vm.selectedThread.collectAsState()
    val inputText by vm.inputText.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val selectedMsgForReaction by vm.selectedMessageForReaction.collectAsState()
    val isSending by vm.isSending.collectAsState()

    // 🔹 Presence
    val isOtherOnline by vm.isOtherOnline.collectAsState()
    val lastSeenText by vm.lastSeenText.collectAsState()

    val context = LocalContext.current
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    // 🔊 Bee / pop sound for reactions + new incoming messages
    val reactionPlayer = remember {
        MediaPlayer.create(context, R.raw.reaction_bee)
    }

    // Track last message id to detect NEW incoming messages
    var lastMessageId by remember { mutableStateOf<String?>(null) }

    // Play sound when a NEW message arrives from the *other* user
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val latest = messages.last()
            if (latest.id != lastMessageId && latest.senderId != currentUid) {
                try {
                    if (reactionPlayer.isPlaying) {
                        reactionPlayer.seekTo(0)
                    }
                    reactionPlayer.start()
                } catch (_: Exception) {
                }
            }
            lastMessageId = latest.id
        }
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

    // Ensure correct thread is selected when opened via route "chat/{threadId}"
    LaunchedEffect(threadId) {
        vm.ensureThreadSelected(threadId)
    }

    val otherName = remember(selectedThread, currentUid) {
        val me = currentUid ?: ""
        selectedThread?.otherUserName(me) ?: "Chat"
    }

    // Show errors as toast (plus dialog below)
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // ... keep the rest of your Scaffold / content exactly as you have it ...

    // List state for auto-scroll
    val listState = rememberLazyListState()

    // Auto-scroll to latest message whenever the list grows
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = otherName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Subtitle: Online / Last seen (no "Typing…" here now)
                        val subtitle: String? = when {
                            isOtherOnline -> "Online"
                            !lastSeenText.isNullOrBlank() -> lastSeenText
                            else -> null
                        }

                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                // Keep content above keyboard while header stays visible
                .imePadding()
        ) {
            if (selectedThread == null) {
                // Fallback
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversation selected.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    state = listState
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.senderId == currentUid

                        ChatMessageBubble(
                            message = msg,
                            mine = mine,
                            showReactionBar = !mine && selectedMsgForReaction == msg.id,
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

                // Typing indicator just above composer
                AnimatedVisibility(visible = isTyping) {
                    Text(
                        text = "Typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                // Composer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = inputText,
                        onValueChange = { vm.setInputText(it) },
                        placeholder = { Text("Type a message…") },
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { vm.sendMessage() },
                        enabled = inputText.isNotBlank() && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
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
    }

    // Emoji picker dialog (More…)
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

    // Error dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Chat") },
            text = { Text(errorMessage ?: "") }
        )
    }
}

/* ─────────────────────────────
   Message bubble with reactions
   ───────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    mine: Boolean,
    showReactionBar: Boolean,
    onLongPress: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onMoreReactions: () -> Unit
) {
    val senderName = message.senderName.ifBlank { "GirlSpace user" }
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.createdAt) {
        message.createdAt.let { sdf.format(it.toDate()) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp)
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
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (!mine) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mine) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

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

/* ─────────────────────────────
   Emoji picker dialog (More…)
   ───────────────────────────── */

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

    AlertDialog(
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
