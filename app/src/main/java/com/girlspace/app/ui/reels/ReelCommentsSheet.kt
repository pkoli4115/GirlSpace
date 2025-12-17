package com.girlspace.app.ui.reels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(
    reelId: String,
    onDismiss: () -> Unit
) {
    val vm: ReelsViewModel = hiltViewModel()
    val comments by vm.comments.collectAsState()
    val loading by vm.commentsLoading.collectAsState()

    var text by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Comments", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
            ) {
                items(comments, key = { it.id }) { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(c.authorName, style = MaterialTheme.typography.labelMedium)
                        Text(c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    Divider()
                }
                item {
                    if (loading) {
                        Box(Modifier.fillMaxWidth().padding(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        TextButton(onClick = { vm.loadMoreComments() }) { Text("Load more") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a comment…") },
                    maxLines = 2
                )
                Button(
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) {
                            vm.addComment(reelId, t)
                            text = ""
                        }
                    }
                ) { Text("Post") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
