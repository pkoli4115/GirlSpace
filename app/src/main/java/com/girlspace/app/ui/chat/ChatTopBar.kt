// ChatTopBar – normal chat header (1:1 or group)
// File: ChatTopBar.kt
//
// Neutral, modern top bar that sits inside your existing MaterialTheme.
// Uses ChatTheme + GirlSpaceIcons. No ViewModel logic here.

package com.girlspace.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatTopBar(
    title: String,
    subtitle: String?,
    isOnline: Boolean,
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChatThemeDefaults.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GirlSpaceIcons.Back,
                    contentDescription = "Back",
                    tint = colors.iconActive
                )
            }

            // Avatar + name + status
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = { /* could open profile later */ }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Simple avatar placeholder – later you can swap in image url
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    GirlSpaceColors.Primary.copy(alpha = 0.9f),
                                    GirlSpaceColors.Secondary.copy(alpha = 0.9f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Online status dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (isOnline) GirlSpaceColors.Success
                                else Color(0xFF9CA3AF)
                            )
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Add user to chat
            IconButton(onClick = onAddUser) {
                Icon(
                    imageVector = GirlSpaceIcons.AddUser,
                    contentDescription = "Add participant",
                    tint = colors.iconActive
                )
            }

            // Overflow menu
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = GirlSpaceIcons.Menu,
                    contentDescription = "Chat menu",
                    tint = colors.iconInactive
                )
            }
        }
    }
}
