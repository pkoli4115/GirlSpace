package com.girlspace.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/* ─────────────────────────────
   Add participants dialog
   ───────────────────────────── */

@Composable
fun AddParticipantsDialogV2(
    friends: List<ChatUserSummary>,
    existingIds: List<String>,
    maxParticipants: Int,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // Safety check – never exceed limit
                    val total = existingIds.size + selected.size
                    if (total <= maxParticipants) {
                        onConfirm(selected)
                    } else {
                        Toast
                            .makeText(
                                context,
                                "Maximum $maxParticipants participants allowed in a group.",
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add participants") },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                val candidates = friends.filter { it.uid !in existingIds }
                if (candidates.isEmpty()) {
                    Text("No friends available to add.")
                } else {
                    candidates.forEach { user ->
                        val checked = selected.contains(user.uid)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) {
                                        // Unselect
                                        selected = selected - user.uid
                                    } else {
                                        val total = existingIds.size + selected.size
                                        if (total < maxParticipants) {
                                            // Select new user
                                            selected = selected + user.uid
                                        } else {
                                            // Hit the limit → show Toast
                                            Toast
                                                .makeText(
                                                    context,
                                                    "You can only have up to $maxParticipants participants in this group.",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        val total = existingIds.size + selected.size
                                        if (total < maxParticipants) {
                                            selected = selected + user.uid
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    "You can only have up to $maxParticipants participants in this group.",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    } else {
                                        selected = selected - user.uid
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    )
}