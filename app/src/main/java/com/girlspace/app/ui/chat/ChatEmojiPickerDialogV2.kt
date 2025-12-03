package com.girlspace.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Emoji picker used when user taps "+" in the reaction bar.
 * Same behavior as before, just moved out of ChatScreenV2.
 */
@Composable
fun ComposerEmojiPickerDialogV2(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val allEmojis = listOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
        "😉", "😊", "🥰", "😍", "🤩", "😘", "😗", "😙",
        "😚", "🙂", "🤗", "🤔", "😐", "😑", "🙄", "😏",
        "😣", "😥", "😮", "🤤", "😪", "😫", "😭", "😤",
        "😡", "😠", "🤬", "🤯", "😳", "🥵", "🥶", "😎",
        "🤓", "😇", "🥳", "🤠", "😴", "🤢", "🤮", "🤧"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Pick emoji") },
        text = {
            Column {
                Text(
                    text = "Tap an emoji to react",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val rows = allEmojis.chunked(8)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                    }
                            )
                        }
                    }
                }
            }
        }
    )
}
