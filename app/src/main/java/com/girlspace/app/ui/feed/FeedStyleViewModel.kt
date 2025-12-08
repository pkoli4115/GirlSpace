package com.girlspace.app.ui.feed

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.preferences.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Feed-level style preset derived from the global theme / vibe.
 */
data class FeedVibe(
    val key: String,
    val displayName: String,
    val backgroundBrush: Brush,
    val cardShape: RoundedCornerShape,
    val cardElevation: Dp,
    val composerBackground: Color,
    val composerBorderColor: Color,
    val likeAccent: Color
)

/**
 * Simple model for quick toggle chips shown on the FeedScreen.
 */
data class QuickVibe(
    val key: String,
    val label: String
)

/**
 * Map the global theme key from ThemePreferences to a feed-specific preset.
 *
 * Keys are shared with onboarding vibe screen:
 *  - "serenity"
 *  - "radiance"
 *  - "wisdom"
 *  - "pulse"
 *  - "harmony"
 *  - "ignite"
 */
private fun feedVibeForTheme(themeKey: String): FeedVibe {
    return when (themeKey) {
        // Calm, light, social
        "serenity" -> FeedVibe(
            key = "serenity",
            displayName = "Calm",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFFE3F2FD),
                    Color(0xFFFFFFFF)
                )
            ),
            cardShape = RoundedCornerShape(18.dp),
            cardElevation = 2.dp,
            composerBackground = Color.White.copy(alpha = 0.98f),
            composerBorderColor = Color(0xFFE0E7FF),
            likeAccent = Color(0xFF1877F2) // calm blue
        )

        // Warm, expressive
        "radiance" -> FeedVibe(
            key = "radiance",
            displayName = "Radiance",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFFF3E0),
                    Color(0xFFFFFFFF)
                )
            ),
            cardShape = RoundedCornerShape(20.dp),
            cardElevation = 3.dp,
            composerBackground = Color(0xFFFFFBFE),
            composerBorderColor = Color(0xFFFFE0B2),
            likeAccent = Color(0xFFF97316) // sunset orange
        )

        // Minimal, pro
        "wisdom" -> FeedVibe(
            key = "wisdom",
            displayName = "Minimal",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFFF9FAFB),
                    Color(0xFFFFFFFF)
                )
            ),
            cardShape = RoundedCornerShape(16.dp),
            cardElevation = 1.dp,
            composerBackground = Color.White,
            composerBorderColor = Color(0xFFE5E7EB),
            likeAccent = Color(0xFF2563EB) // pro blue
        )

        // Dark, luxe, neon
        "pulse" -> FeedVibe(
            key = "pulse",
            displayName = "DarkLux",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFF020617),
                    Color(0xFF111827)
                )
            ),
            cardShape = RoundedCornerShape(22.dp),
            cardElevation = 6.dp,
            composerBackground = Color(0xFF020617),
            composerBorderColor = Color(0xFF1F2937),
            likeAccent = Color(0xFFEC4899) // neon pink
        )

        // Fresh, community
        "harmony" -> FeedVibe(
            key = "harmony",
            displayName = "Harmony",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFFEFFDF5),
                    Color(0xFFFFFFFF)
                )
            ),
            cardShape = RoundedCornerShape(18.dp),
            cardElevation = 2.dp,
            composerBackground = Color.White.copy(alpha = 0.97f),
            composerBorderColor = Color(0xFFBBF7D0),
            likeAccent = Color(0xFF22C55E) // green
        )

        // Bold, creator / video first
        "ignite" -> FeedVibe(
            key = "ignite",
            displayName = "Ignite",
            backgroundBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFFE4E6),
                    Color(0xFFFFFFFF)
                )
            ),
            cardShape = RoundedCornerShape(20.dp),
            cardElevation = 3.dp,
            composerBackground = Color(0xFFFFFBFB),
            composerBorderColor = Color(0xFFFCA5A5),
            likeAccent = Color(0xFFEF4444) // strong red
        )

        else -> feedVibeForTheme("serenity")
    }
}

@HiltViewModel
class FeedStyleViewModel @Inject constructor(
    private val prefs: ThemePreferences
) : ViewModel() {

    /**
     * Raw theme key from DataStore (same as OnboardingViewModel.themeMode).
     */
    val themeMode = prefs.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "serenity"
    )

    /**
     * Resolved FeedVibe used to drive FeedScreen polish.
     */
    val currentVibe = themeMode
        .map { key -> feedVibeForTheme(key) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = feedVibeForTheme("serenity")
        )

    /**
     * Quick presets surfaced as chips on FeedScreen.
     *
     * These map directly to the onboarding keys but with UX labels:
     *  - Calm     → serenity
     *  - DarkLux  → pulse
     *  - Minimal  → wisdom
     */
    val quickVibes: List<QuickVibe> = listOf(
        QuickVibe(key = "serenity", label = "Calm"),
        QuickVibe(key = "pulse", label = "DarkLux"),
        QuickVibe(key = "wisdom", label = "Minimal")
    )

    /**
     * Update the selected vibe. This writes to ThemePreferences so it stays
     * consistent with onboarding and any app-wide theming.
     */
    fun setVibe(key: String) {
        viewModelScope.launch {
            prefs.saveThemeMode(key)
        }
    }
}
