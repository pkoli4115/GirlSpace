package com.girlspace.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String = "Togetherly",
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    vibe: VibeMode = VibeMode.Default,
    animated: Boolean = true,
    actions: @Composable (() -> Unit)? = null
) {
    // ✅ Read theme values ONLY in composable scope
    val titleTextStyle: TextStyle = MaterialTheme.typography.titleLarge
    val onSurface: Color = MaterialTheme.colorScheme.onSurface
    val surface: Color = MaterialTheme.colorScheme.surface

    val colors = remember(vibe, onSurface) {
        when (vibe) {
            VibeMode.Wisdom -> listOf(
                Color(0xFF00C6FF), // cyan-ish
                Color(0xFF0072FF)  // deep blue
            )

            VibeMode.Pulse -> listOf(
                Color(0xFFFF2D55), // hot pink/red
                Color(0xFF6C63FF)  // purple
            )

            VibeMode.Harmony -> listOf(
                Color(0xFF00C853), // green
                Color(0xFFB2FF59)  // light green
            )

            VibeMode.Ignite -> listOf(
                Color(0xFFFF3D00), // fiery orange-red
                Color(0xFFFFD600)  // amber
            )

            else -> listOf(Color(0xFFFF5FA2), Color(0xFFFFB14A), Color(0xFF6C63FF))
        }
    }

    val transition = rememberInfiniteTransition(label = "brandSweep")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animated) 2800 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    TopAppBar(
        title = {
            Text(
                text = title,
                style = titleTextStyle,
                color = onSurface, // fallback
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val w = size.width.coerceAtLeast(1f)
                        val startX = -w + (2f * w * t)

                        val brush = Brush.linearGradient(
                            colors = colors,
                            start = Offset(startX, 0f),
                            end = Offset(startX + w, 0f)
                        )

                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                        }
                    }
            )
        },
        navigationIcon = {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = { actions?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = surface,
            titleContentColor = onSurface,
            navigationIconContentColor = onSurface,
            actionIconContentColor = onSurface
        )
    )
}

enum class VibeMode {
    Default,
    Serenity,
    Radiance,
    Wisdom,
    Pulse,
    Harmony,
    Ignite
}

