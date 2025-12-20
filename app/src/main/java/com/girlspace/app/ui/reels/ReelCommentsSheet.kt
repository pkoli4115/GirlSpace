package com.girlspace.app.ui.reels

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(
    title: String,
    reelId: String,
    comments: List<ReelComment>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    // --- Report Comment state ---
    var showReportComment by rememberSaveable { mutableStateOf(false) }
    var pendingReportCommentId by rememberSaveable { mutableStateOf<String?>(null) }

    // find comment object when dialog is shown
    val pendingComment = remember(pendingReportCommentId, comments) {
        val id = pendingReportCommentId
        if (id.isNullOrBlank()) null else comments.firstOrNull { it.id == id }
    }

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
                        CommentRow(
                            c = c,
                            myUid = myUid,
                            onReport = {
                                pendingReportCommentId = c.id
                                showReportComment = true
                            }
                        )
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

    // ✅ Report Comment dialog (outside sheet content but still within this composable)
    if (showReportComment && pendingComment != null) {
        ReportCommentDialog(
            reelId = reelId,
            comment = pendingComment,
            onDismiss = {
                showReportComment = false
                pendingReportCommentId = null
            },
            onSubmitted = {
                showReportComment = false
                pendingReportCommentId = null
            }
        )
    }
}

@Composable
private fun CommentRow(
    c: ReelComment,
    myUid: String,
    onReport: () -> Unit
) {
    val df = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }
    val time = c.createdAt?.toDate()?.let(df::format).orEmpty()

    var menuOpen by remember { mutableStateOf(false) }

    val canReport = c.authorId.isNotBlank() && c.authorId != myUid

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = c.authorName.ifBlank { "User" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // ⋮ menu
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More"
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Report Comment") },
                        enabled = canReport,
                        onClick = {
                            menuOpen = false
                            onReport()
                        }
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCommentDialog(
    reelId: String,
    comment: ReelComment,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var selected by remember { mutableStateOf(ReportReason.SPAM) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Report Comment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select a reason:", style = MaterialTheme.typography.bodyMedium)

                ReportReason.values().forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !submitting) { selected = r }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selected == r),
                            onClick = { if (!submitting) selected = r }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(r.label)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = !submitting,
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (uid.isNullOrBlank()) {
                        error = "Please sign in to report."
                        return@TextButton
                    }

                    // do not allow reporting own comment
                    if (comment.authorId.isBlank() || comment.authorId == uid) {
                        error = "You can’t report your own comment."
                        return@TextButton
                    }

                    submitting = true
                    error = null

                    val reportId = UUID.randomUUID().toString()
                    val data = hashMapOf(
                        "type" to "REEL_COMMENT",
                        "reelId" to reelId,
                        "targetId" to comment.id,
                        "reporterUserId" to uid,
                        "reportedUserId" to comment.authorId,
                        "reason" to selected.wire,
                        "note" to note.text.trim().takeIf { it.isNotBlank() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "status" to "OPEN"
                    )

                    if (data["note"] == null) data.remove("note")

                    firestore.collection("reports")
                        .document(reportId)
                        .set(data)
                        .addOnSuccessListener {
                            submitting = false
                            onSubmitted()
                        }
                        .addOnFailureListener { e ->
                            submitting = false
                            error = e.message ?: "Failed to submit report"
                        }
                }
            ) { Text(if (submitting) "Submitting…" else "Submit") }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
