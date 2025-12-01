// GirlSpace – ChatTheme (local theme layer for chat UI)
// File: ChatTheme.kt

package com.girlspace.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Minimal chat-specific color scheme that sits on top of MaterialTheme.
 * This keeps chat visuals consistent and easy to tweak later.
 */
data class ChatColors(
    val background: Color,
    val myBubble: Color,
    val theirBubble: Color,
    val selection: Color,
    val replyBackground: Color,
    val reactionBackground: Color,
    val iconActive: Color,
    val iconInactive: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnMyBubble: Color,
    val divider: Color
)

private val DefaultChatColors = ChatColors(
    background = GirlSpaceColors.SurfaceSoft,
    myBubble = GirlSpaceColors.BubbleMine,
    theirBubble = GirlSpaceColors.BubbleTheirs,
    selection = GirlSpaceColors.Selection,
    replyBackground = GirlSpaceColors.SurfaceStrong,
    reactionBackground = GirlSpaceColors.SurfaceStrong,
    iconActive = GirlSpaceColors.IconActive,
    iconInactive = GirlSpaceColors.IconInactive,
    textPrimary = GirlSpaceColors.TextPrimary,
    textSecondary = GirlSpaceColors.TextSecondary,
    textOnMyBubble = GirlSpaceColors.TextOnPrimary,
    divider = Color(0x11000000)
)

val LocalChatColors = staticCompositionLocalOf { DefaultChatColors }

/**
 * Wrap chat UI with ChatTheme to gain access to LocalChatColors.
 *
 * Usage:
 * ChatTheme {
 *     // ChatScreen content here
 * }
 */
@Composable
fun ChatTheme(
    colors: ChatColors = DefaultChatColors,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalChatColors provides colors,
        content = content
    )
}

/**
 * Convenience accessors so inside chat UI we can write:
 * ChatThemeDefaults.colors.myBubble, etc.
 */
object ChatThemeDefaults {
    val colors: ChatColors
        @Composable get() = LocalChatColors.current
}
