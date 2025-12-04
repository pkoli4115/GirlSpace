package com.girlspace.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Videocam

// ─────────────────────────────────────────────────────────────
// NORMAL CHAT TOP BAR (Search + Share moved to overflow menu)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBarV2(
    title: String,
    isOnline: Boolean,
    lastSeen: String?,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onVideoClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onAddParticipantsClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconColor = MaterialTheme.colorScheme.primary

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        isOnline -> "Online"
                        !lastSeen.isNullOrBlank() -> "last seen $lastSeen"
                        else -> null
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // 🔹 KEEP video icon (as requested)
            IconButton(onClick = onVideoClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    tint = iconColor
                )
            }

            // 🔹 SEARCH + SHARE removed from here, moved into menu

            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = iconColor
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {

                // 🔍 Search
                DropdownMenuItem(
                    text = { Text("Search in chat") },
                    onClick = {
                        showMenu = false
                        onSearchClick()
                    }
                )

                // 📤 Share
                DropdownMenuItem(
                    text = { Text("Share chat") },
                    onClick = {
                        showMenu = false
                        onShareClick()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Chat info") },
                    onClick = {
                        showMenu = false
                        onInfoClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add participants") },
                    onClick = {
                        showMenu = false
                        onAddParticipantsClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report chat") },
                    onClick = {
                        showMenu = false
                        onReportClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Block user") },
                    onClick = {
                        showMenu = false
                        onBlockClick()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ─────────────────────────────────────────────────────────────
// SELECTION MODE TOP BAR (unchanged except mandatory parts)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBarV2(
    count: Int,
    canReply: Boolean,
    onClearSelection: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onShareOrForward: () -> Unit,
    onPin: () -> Unit,
    onReport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Clear selection"
                )
            }
        },
        actions = {
            if (canReply) {
                IconButton(onClick = onReply) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Reply"
                    )
                }
            }

            IconButton(onClick = onStar) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = "Star"
                )
            }

            IconButton(onClick = onShareOrForward) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share / Forward"
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }

            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Pin") },
                    onClick = {
                        menuExpanded = false
                        onPin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report") },
                    onClick = {
                        menuExpanded = false
                        onReport()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
