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
import androidx.compose.material3.TextButton
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
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = iconColor
                )
            }
            IconButton(onClick = onVideoClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    tint = iconColor
                )
            }
            IconButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share chat",
                    tint = iconColor
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBarV2(
    count: Int,
    canReply: Boolean,
    canShareOrForward: Boolean,
    onClearSelection: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onMoreShare: () -> Unit,
    onPin: () -> Unit,
    onReport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "$count selected",
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

            if (canShareOrForward) {
                IconButton(onClick = onForward) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = "Forward"
                    )
                }
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
                if (canShareOrForward) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuExpanded = false
                            onMoreShare()
                        }
                    )
                }

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
