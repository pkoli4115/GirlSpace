package com.girlspace.app.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.subtleGlow(
    glowColor: Color,
    radius: Float = 80f
): Modifier = this.drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.25f),
                Color.Transparent
            ),
            center = Offset(size.width / 2, size.height / 2),
            radius = radius
        ),
        radius = radius
    )
}
