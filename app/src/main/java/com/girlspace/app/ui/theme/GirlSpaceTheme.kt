package com.girlspace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme.typography
@Composable
fun GirlSpaceTheme(
    palette: GirlSpacePalette,
    content: @Composable () -> Unit
) {
    val colors = lightColorScheme(
        primary = palette.primary,
        secondary = palette.secondary,
        onPrimary = palette.text,
        onSecondary = palette.text,
        background = palette.primary,
        surface = palette.primary,
        onBackground = palette.text,
        onSurface = palette.text
    )

    MaterialTheme(
        colorScheme = colors,
        typography = GirlSpaceTypography,
        content = content
    )
}
