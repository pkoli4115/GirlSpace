// ChatMessageBubble – modern bubble UI for chat messages
// File: ChatMessageBubble.kt

package com.girlspace.app.ui.chat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.girlspace.app.data.chat.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    mine: Boolean,
    currentUserId: String?,
    isSelected: Boolean,
    isHighlighted: Boolean,
    showReactionStrip: Boolean,
    isPlayingAudio: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: () -> Unit,
    onMediaClick: (ChatMessage) -> Unit,
    onAudioClick: (ChatMessage) -> Unit,
    onReactionSelected: (String) -> Unit,
    onMoreReactions: () -> Unit
) {
    val chatColors = ChatThemeDefaults.colors
    val firestore = remember { FirebaseFirestore.getInstance() }

// Start with what message has
    var resolvedSenderName by remember(message.id) {
        mutableStateOf(message.senderName.ifBlank { "User" })
    }

// ✅ If incoming message and name looks generic, resolve from /users/{senderId}
    LaunchedEffect(message.id, mine) {
        if (mine) return@LaunchedEffect
        val current = resolvedSenderName.trim()
        if (current.isNotBlank() && current.lowercase() != "girlspace user" && current.lowercase() != "someone") {
            return@LaunchedEffect
        }

        val uid = message.senderId
        if (uid.isBlank()) return@LaunchedEffect

        runCatching {
            val userDoc = firestore.collection("users").document(uid).get().await()
            val name =
                userDoc.getString("displayName")
                    ?: userDoc.getString("name")
                    ?: userDoc.getString("fullName")
                    ?: userDoc.getString("username")
            if (!name.isNullOrBlank()) resolvedSenderName = name
        }
    }
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.createdAt) {
        runCatching { sdf.format(message.createdAt.toDate()) }.getOrNull() ?: ""
    }

    val seenByOther = remember(message.readBy, message.senderId, currentUserId) {
        if (!mine || currentUserId == null) false
        else message.readBy.any { it != message.senderId }
    }

    val statusTicks = if (mine) {
        if (seenByOther) "✓✓" else "✓"
    } else ""

    // Base bubble color
    val baseBg = if (mine) chatColors.myBubble else chatColors.theirBubble
    val textColor = if (mine) chatColors.textOnMyBubble else chatColors.textPrimary

    // Highlight / selection overlay
    val bgColor = when {
        isSelected -> chatColors.selection.copy(alpha = 0.7f)
        isHighlighted -> chatColors.selection.copy(alpha = 0.4f)
        else -> baseBg
    }

    Row(
        modifier = modifier,
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .align(Alignment.CenterVertically)   // ✅ FIX: use Alignment.Vertical
        ) {
            // Reaction strip (optional, above bubble)
            AnimatedVisibility(
                visible = showReactionStrip,
                enter = fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = spring()),
                exit = fadeOut()
            ) {
                ChatReactionBar(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .align(if (mine) Alignment.End else Alignment.Start),
                    onSelect = onReactionSelected,
                    onMore = onMoreReactions
                )
            }

            // Bubble itself
            Surface(
                modifier = Modifier
                    .shadow(
                        elevation = if (mine) 1.dp else 0.dp,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (mine) 18.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 18.dp
                        )
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongPress
                    ),
                color = bgColor,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (mine) 18.dp else 4.dp,
                    bottomEnd = if (mine) 4.dp else 18.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Sender name (for incoming messages in group or 1:1)
                    if (!mine) {
                        Text(
                            text = resolvedSenderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = chatColors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // MEDIA
                    if (message.mediaUrl != null && message.mediaType != null) {
                        when (message.mediaType) {
                            "image" -> {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onMediaClick(message) }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            "video" -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            color = Color.Black.copy(alpha = 0.05f)
                                        )
                                        .clickable { onMediaClick(message) }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "▶ Video",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            "audio" -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (mine)
                                                Color.White.copy(alpha = 0.15f)
                                            else
                                                Color.Black.copy(alpha = 0.04f)
                                        )
                                        .clickable { onAudioClick(message) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isPlayingAudio) "⏸" else "▶",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Voice message",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            else -> {
                                // Generic file (PDF, DOC, etc.)
                                val label = remember(message.mediaUrl) {
                                    val raw = message.mediaUrl.lowercase()
                                    when {
                                        raw.endsWith(".pdf") -> "📄 PDF"
                                        raw.endsWith(".docx") || raw.endsWith(".doc") -> "📝 Document"
                                        raw.endsWith(".ppt") || raw.endsWith(".pptx") -> "📊 Presentation"
                                        raw.endsWith(".xls") || raw.endsWith(".xlsx") -> "📈 Spreadsheet"
                                        else -> "📎 File"
                                    }
                                }

                                val fileName = message.text.takeIf { it.isNotBlank() }

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (mine)
                                                Color.White.copy(alpha = 0.12f)
                                            else
                                                Color.Black.copy(alpha = 0.04f)
                                        )
                                        .clickable { onMediaClick(message) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                    fileName?.let {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = chatColors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    // TEXT / DELETED HANDLING
                    val body = message.text
                    when {
                        body == "This message was deleted" -> {
                            Text(
                                text = "This message was deleted",
                                style = MaterialTheme.typography.bodySmall,
                                color = chatColors.textSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        body.isNotBlank() -> {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                    }

                    // Time + ticks
                    if (timeText.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine)
                                    chatColors.textOnMyBubble.copy(alpha = 0.8f)
                                else
                                    chatColors.textSecondary
                            )

                            if (statusTicks.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusTicks,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (seenByOther)
                                        Color(0xFF4FC3F7)
                                    else if (mine)
                                        chatColors.textOnMyBubble.copy(alpha = 0.8f)
                                    else
                                        chatColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Aggregated reactions below bubble
            AnimatedVisibility(visible = message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(if (mine) Alignment.End else Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    message.reactions.values.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chatColors.reactionBackground)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
