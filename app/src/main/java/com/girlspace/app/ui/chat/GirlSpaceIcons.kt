// GirlSpace – Chat icon aliases (Compose ImageVector)
// File: GirlSpaceIcons.kt

package com.girlspace.app.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central place for all chat-related icons.
 * Internally uses Material Icons, but can be switched to custom vectors later.
 */
object GirlSpaceIcons {

    // Top bar – navigation
    val Back: ImageVector = Icons.Filled.ArrowBack
    val Menu: ImageVector = Icons.Filled.MoreVert

    // Top bar – selection mode actions
    val Reply: ImageVector = Icons.Outlined.Reply
    val Forward: ImageVector = Icons.Outlined.Forward
    val Share: ImageVector = Icons.Filled.Share
    val Star: ImageVector = Icons.Filled.Star
    val Pin: ImageVector = Icons.Outlined.Pin
    val Info: ImageVector = Icons.Filled.Info
    val Delete: ImageVector = Icons.Filled.Delete
    val Report: ImageVector = Icons.Filled.Flag

    // Top bar – chat actions
    val AddUser: ImageVector = Icons.Filled.PersonAdd

    // Composer
    val Emoji: ImageVector = Icons.Outlined.EmojiEmotions
    val Attach: ImageVector = Icons.Filled.AttachFile
    val Camera: ImageVector = Icons.Filled.CameraAlt
    val Mic: ImageVector = Icons.Filled.Mic
    val Send: ImageVector = Icons.Filled.Send

    // Misc
    val ScrollDown: ImageVector = Icons.Filled.ArrowDownward
    val Overflow: ImageVector = Icons.Filled.Menu
}
