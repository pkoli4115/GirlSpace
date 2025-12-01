// ChatComposer – WhatsApp-style message input bar
// File: ChatComposer.kt

package com.girlspace.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * WhatsApp-style bottom composer:
 *
 * [Emoji]   [  pill text field with hint  ]   [Mic or Send]
 *    + attach & camera inside the pill on the left.
 *
 * This is completely stateless; you control everything from outside.
 */
@Composable
fun ChatComposer(
    text: String,
    isSending: Boolean,
    isRecording: Boolean,
    hasAttachments: Boolean,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
    onEmojiClick: () -> Unit,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit
) {
    val colors = ChatThemeDefaults.colors
    val canSend = text.isNotBlank() || hasAttachments

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji button on the far left
            IconButton(onClick = onEmojiClick) {
                Icon(
                    imageVector = GirlSpaceIcons.Emoji,
                    contentDescription = "Emoji",
                    tint = colors.iconActive
                )
            }

            // Middle pill: attach + camera + text
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.background)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                ) {
                    Icon(
                        imageVector = GirlSpaceIcons.Attach,
                        contentDescription = "Attach",
                        tint = colors.iconInactive
                    )
                }

                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                ) {
                    Icon(
                        imageVector = GirlSpaceIcons.Camera,
                        contentDescription = "Camera",
                        tint = colors.iconInactive
                    )
                }

                // Text input (multi-line, auto-expand)
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp),
                    placeholder = {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    singleLine = false,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = colors.background,
                        focusedContainerColor = colors.background,
                        disabledContainerColor = colors.background,
                        unfocusedIndicatorColor = colors.background,
                        focusedIndicatorColor = colors.background,
                        cursorColor = colors.iconActive,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right side: Mic (if no text) OR Send (if text / attachments)
            IconButton(
                onClick = {
                    if (canSend) onSendClick() else onMicClick()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (canSend)
                        colors.myBubble
                    else
                        colors.iconActive,
                    contentColor = colors.textOnMyBubble
                ),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
            ) {
                when {
                    isSending && canSend -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.heightIn(18.dp)
                        )
                    }

                    canSend -> {
                        Icon(
                            imageVector = GirlSpaceIcons.Send,
                            contentDescription = "Send"
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = GirlSpaceIcons.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Voice message"
                        )
                    }
                }
            }
        }
    }
}
