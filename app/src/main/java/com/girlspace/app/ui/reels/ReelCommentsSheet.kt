package com.girlspace.app.ui.reels
import com.girlspace.app.data.reels.ReelComment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(
    title: String,
    comments: List<ReelComment>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            if (isLoading && comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments, key = { it.id }) { c ->
                        CommentRow(c)
                    }

                    item(key = "load_more") {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = onLoadMore,
                                enabled = !isLoading
                            ) {
                                Text(if (isLoading) "Loading…" else "Load more")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Divider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a comment…") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val t = input.trim()
                            if (t.isNotEmpty()) {
                                onSend(t)
                                input = ""
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty()) {
                            onSend(t)
                            input = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CommentRow(c: ReelComment) {
    val df = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }
    val time = c.createdAt?.toDate()?.let(df::format).orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = c.authorName.ifBlank { "User" },
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = c.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}
