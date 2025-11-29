package com.girlspace.app.ui.chat.components
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.girlspace.app.data.chat.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPress() }
                )
                .background(
                    color = if (isMe)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(12.dp)
        ) {
            Text(message.text)
        }

        // Show reactions below the bubble
        AnimatedVisibility(message.reactions.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 4.dp)) {
                message.reactions.values.forEach { emoji ->
                    Text(
                        text = emoji,
                        modifier = Modifier.padding(end = 6.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
