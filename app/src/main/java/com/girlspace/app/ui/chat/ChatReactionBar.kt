// ChatReactionBar – compact emoji reaction strip below/above bubbles
// File: ChatReactionBar.kt

package com.girlspace.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Simple reaction bar (😀 😍 😡 etc.) with a "+" for more.
 *
 * This is a replacement for the old components.ReactionBar, but you
 * can keep the same signature (onSelect / onMore) so wiring stays easy.
 */
@Composable
fun ChatReactionBar(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onMore: () -> Unit
) {
    val colors = ChatThemeDefaults.colors
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡")

    Surface(
        modifier = modifier,
        color = colors.reactionBackground,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(emoji) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "➕",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.selection.copy(alpha = 0.4f))
                    .clickable { onMore() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
