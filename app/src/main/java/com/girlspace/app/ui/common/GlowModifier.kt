package com.girlspace.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/**
 * Subtle pulsing glow used for active reel
 */
fun Modifier.glowPulse(
    color: Color,
    enabled: Boolean
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val transition = rememberInfiniteTransition(label = "glow")
        val alpha = transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        drawBehind {
            drawRect(color.copy(alpha = alpha.value))
        }
    }
}
