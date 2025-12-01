// ScrollToBottomButton – floating "jump to latest" button
// File: ScrollToBottomButton.kt

package com.girlspace.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Small circular button that appears when you're *not* at the bottom of the chat.
 * Optional unread count badge.
 */
@Composable
fun ScrollToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = ChatThemeDefaults.colors

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        ) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
    ) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickableWithoutRipple { onClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = GirlSpaceIcons.ScrollDown,
                    contentDescription = "Scroll to bottom",
                    tint = colors.iconActive,
                    modifier = Modifier.size(18.dp)
                )

                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 18.dp)
                            .clip(CircleShape)
                            .background(colors.myBubble),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textOnMyBubble
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper: clickable without ripple (for a more "floating" feel).
 *
 * ✅ Uses the receiver Modifier
 * ✅ Uses remember for MutableInteractionSource
 */
@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        indication = null,
        interactionSource = interactionSource,
        onClick = onClick
    )
}
