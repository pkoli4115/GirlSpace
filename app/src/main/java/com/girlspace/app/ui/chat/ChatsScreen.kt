package com.girlspace.app.ui.chat
import androidx.compose.foundation.clickable
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.girlspace.app.data.chat.ChatThread
import com.girlspace.app.ui.chat.components.ReactionBar
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatsScreen(
    vm: ChatViewModel = viewModel()
) {
    val threads by vm.threads.collectAsState()
    val messages by vm.messages.collectAsState()
    val selectedThread by vm.selectedThread.collectAsState()
    val inputText by vm.inputText.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val isStartingChat by vm.isStartingChat.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val selectedMsgForReaction by vm.selectedMessageForReaction.collectAsState()
    val isSending by vm.isSending.collectAsState()

    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    var newChatEmail by remember { mutableStateOf("") }

    // 🔊 Bee / pop sound for reactions
    val context = LocalContext.current
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // Start new chat by email
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newChatEmail,
                onValueChange = { newChatEmail = it },
                label = { Text("Start new chat by email") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    val email = newChatEmail.trim()
                    if (email.isNotEmpty()) {
                        vm.startChatWithEmail(email)
                    }
                },
                enabled = !isStartingChat && newChatEmail.isNotBlank()
            ) {
                if (isStartingChat) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Starting…")
                } else {
                    Text("Start chat")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your chats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (threads.isEmpty() && !isStartingChat) {
                Text(
                    text = "No chats yet. Start one using an email above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Thread list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(threads) { thread ->
                    ChatThreadRow(
                        thread = thread,
                        myId = currentUid,
                        isSelected = selectedThread?.id == thread.id,
                        onClick = { vm.selectThread(thread) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Messages + composer
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    if (selectedThread == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a chat above or start a new one.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(messages) { msg ->
                                val mine = msg.senderId == currentUid

                                ChatMessageBubble(
                                    message = msg,
                                    mine = mine,
                                    showReactionBar = !mine && selectedMsgForReaction == msg.id,
                                    onLongPress = {
                                        // Only react to other user's messages
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

                        Spacer(modifier = Modifier.height(6.dp))

                        if (isTyping && inputText.isNotBlank()) {
                            Text(
                                text = "Typing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp, bottom = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = inputText,
                                onValueChange = { vm.setInputText(it) },
                                placeholder = { Text("Type a message…") },
                                maxLines = 4,
                                enabled = selectedThread != null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { vm.sendMessage() },
                                enabled = inputText.isNotBlank() &&
                                        selectedThread != null &&
                                        !isSending
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

@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    myId: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val safeMyId = myId ?: ""
    val displayName = thread.otherUserName(safeMyId)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (thread.lastMessage.isNotBlank()) {
                    Text(
                        text = thread.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (thread.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = thread.unreadCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

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
            // 🔧 increased max width so reaction bar + all emojis fit
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            // Reaction bar attached above this bubble
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

            // Reactions as small line under bubble
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
