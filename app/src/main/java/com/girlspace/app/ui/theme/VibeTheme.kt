package com.girlspace.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme.typography
// Keys must match what we store from MoodOnboardingScreen
object Vibes {
    const val SERENITY = "serenity"
    const val RADIANCE = "radiance"
    const val WISDOM = "wisdom"
    const val PULSE = "pulse"
    const val HARMONY = "harmony"
    const val IGNITE = "ignite"
}

// Base light palette you already like (adjust if needed)
private val BaseLightColors = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1B1B1F)
)

// Optional dark base for Pulse later if you want real dark mode
private val BaseDarkColors = darkColorScheme()

private val SerenityColors = BaseLightColors.copy(
    primary = Color(0xFF1877F2),  // Facebook blue
    onPrimary = Color.White
)

private val RadianceColors = BaseLightColors.copy(
    primary = Color(0xFFF56040),  // Instagram-ish warm
    onPrimary = Color.White
)

private val WisdomColors = BaseLightColors.copy(
    primary = Color(0xFF0A66C2),  // LinkedIn blue
    onPrimary = Color.White
)

private val HarmonyColors = BaseLightColors.copy(
    primary = Color(0xFF25D366),  // WhatsApp green
    onPrimary = Color.White
)

private val IgniteColors = BaseLightColors.copy(
    primary = Color(0xFFFF0000),  // YouTube red
    onPrimary = Color.White
)

private val PulseColorsLight = BaseLightColors.copy(
    primary = Color(0xFF111827),  // near-black
    onPrimary = Color.White
)

/**
 * Global theme that reads the selected vibe and applies colors.
 *
 * Use this instead of directly calling MaterialTheme in your Activity.
 */
@Composable
fun VibeTheme(
    themeMode: String,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        Vibes.SERENITY -> SerenityColors
        Vibes.RADIANCE -> RadianceColors
        Vibes.WISDOM -> WisdomColors
        Vibes.HARMONY -> HarmonyColors
        Vibes.IGNITE -> IgniteColors
        Vibes.PULSE -> if (darkTheme) BaseDarkColors else PulseColorsLight
        else -> BaseLightColors // fallback
    }

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content
    )
}
