package com.girlspace.app.ui.chat.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPickerSheet(
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    val emojis = remember {
        listOf(
            "😀","😁","😂","🤣","😃","😄","😅","😆",
            "😉","😊","🥰","😍","🤩","😘","😗","😙",
            "😚","🙂","🤗","🤔","😐","😑","🙄","😏",
            "😣","😥","😮","🤤","😪","😫","😭","😤",
            "😡","😠","🤬","🤯","😳","🥵","🥶","😎",
            "🤓","😇","🥳","🤠","😴","🤢","🤮","🤧"
        )
    }

    Surface(
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Emoji", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("Close") }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.height(320.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center
            ) {
                items(emojis.size) { index ->
                    Text(
                        text = emojis[index],
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(6.dp)
                            .clickable { onEmojiSelected(emojis[index]) }
                    )
                }
            }
        }
    }
}
