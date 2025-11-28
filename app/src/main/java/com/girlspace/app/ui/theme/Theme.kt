package com.girlspace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// We'll expand these with theme packs later
private val LightColors = lightColorScheme(
    primary = PurplePink,
    secondary = SoftPink,
    tertiary = Lavender,
)

private val DarkColors = darkColorScheme(
    primary = PurplePink,
    secondary = SoftPink,
    tertiary = Lavender,
)

@Composable
fun GirlSpaceTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Later: route this through the Theme Engine (user prefs, seasonal, mood, etc.)
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = GirlSpaceTypography,
        shapes = GirlSpaceShapes,
        content = content
    )
}
