// ChatSelectionBar – selection mode header (WhatsApp-style actions)
// File: ChatSelectionBar.kt
//
// Shown when one or more messages are selected.
// Only emits callbacks; all behavior is in your ViewModel / ChatScreen.

package com.girlspace.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatSelectionBar(
    selectionCount: Int,
    onBack: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onStar: () -> Unit,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChatThemeDefaults.colors

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back / close selection
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GirlSpaceIcons.Back,
                    contentDescription = "Clear selection",
                    tint = colors.iconActive
                )
            }

            Text(
                text = "$selectionCount selected",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            Spacer(modifier = Modifier.weight(1f))

            // Reply
            IconButton(
                onClick = onReply,
                enabled = selectionCount == 1
            ) {
                Icon(
                    imageVector = GirlSpaceIcons.Reply,
                    contentDescription = "Reply",
                    tint = if (selectionCount == 1) colors.iconActive else colors.iconInactive
                )
            }

            // Forward
            IconButton(onClick = onForward) {
                Icon(
                    imageVector = GirlSpaceIcons.Forward,
                    contentDescription = "Forward",
                    tint = colors.iconActive
                )
            }

            // Share (outside app)
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = GirlSpaceIcons.Share,
                    contentDescription = "Share",
                    tint = colors.iconActive
                )
            }

            // Star
            IconButton(onClick = onStar) {
                Icon(
                    imageVector = GirlSpaceIcons.Star,
                    contentDescription = "Star",
                    tint = colors.iconActive
                )
            }

            // Pin
            IconButton(onClick = onPin) {
                Icon(
                    imageVector = GirlSpaceIcons.Pin,
                    contentDescription = "Pin",
                    tint = colors.iconActive
                )
            }

            // Info
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = GirlSpaceIcons.Info,
                    contentDescription = "Info",
                    tint = colors.iconActive
                )
            }

            // Delete
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = GirlSpaceIcons.Delete,
                    contentDescription = "Delete",
                    tint = colors.iconActive
                )
            }

            // Report
            IconButton(onClick = onReport) {
                Icon(
                    imageVector = GirlSpaceIcons.Report,
                    contentDescription = "Report",
                    tint = colors.iconActive
                )
            }
        }
    }
}
