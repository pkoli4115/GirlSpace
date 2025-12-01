// GirlSpace – Chat color tokens (neutral, brand-agnostic)
// File: GirlSpaceColors.kt

package com.girlspace.app.ui.chat

import androidx.compose.ui.graphics.Color

/**
 * Neutral, modern palette suitable for any brand.
 * You can later remap these to your global theme or rename them.
 */
object GirlSpaceColors {

    // Primary accent (buttons, active icons, my bubble)
    val Primary = Color(0xFF4E9BFF)      // soft blue

    // Secondary accent (selection, highlights)
    val Secondary = Color(0xFF6C35FF)    // violet

    // Tertiary accent (reactions, subtle accents)
    val Accent = Color(0xFF3DDC97)       // mint / teal

    // Surfaces
    val SurfaceSoft = Color(0xFFF5F5FA)  // light neutral surface
    val SurfaceStrong = Color(0xFFE0E3F0)

    // Chat bubbles
    val BubbleMine = Primary
    val BubbleTheirs = Color(0xFFFFFFFF)

    // Selection / “flash highlight” for scroll-to-message & selection mode
    val Selection = Color(0xFFEDE5FF)    // soft lavender

    // Icons
    val IconActive = Primary
    val IconInactive = Color(0xFF9BA0B5)

    // Text
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)
    val TextOnPrimary = Color(0xFFFFFFFF)

    // Status
    val Success = Color(0xFF10B981)
    val Error = Color(0xFFEF4444)
    val Warning = Color(0xFFF59E0B)
}
