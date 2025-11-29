package com.girlspace.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Quick reactions row + "+" button for full emoji picker.
 * "+" is always the last item.
 */
@Composable
fun ReactionBar(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onMore: () -> Unit
) {
    // ✅ includes "+" at the end
    val reactions = listOf("❤️", "👍", "😂", "😮", "😢", "🔥", "➕")

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            reactions.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable {
                            if (emoji == "➕") {
                                onMore()
                            } else {
                                onSelect(emoji)
                            }
                        }
                )
            }
        }
    }
}
