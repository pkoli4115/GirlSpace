package com.girlspace.app.ui.theme

import androidx.compose.ui.graphics.Color

object ColorPalettes {

    // MUCH MORE DISTINCT palettes now

    // 🌸 Calm – soft lavender
    val Calm = GirlSpacePalette(
        primary = Color(0xFFEAE7FF),
        secondary = Color(0xFFB39DDB),
        text = Color(0xFF311B92)
    )

    // 💖 Romantic – rosy pink
    val Romantic = GirlSpacePalette(
        primary = Color(0xFFFFE3EC),
        secondary = Color(0xFFF48FB1),
        text = Color(0xFF880E4F)
    )

    // 🔥 Bold – warm orange/gold
    val Bold = GirlSpacePalette(
        primary = Color(0xFFFFF3E0),
        secondary = Color(0xFFFFB74D),
        text = Color(0xFFE65100)
    )

    // 🌈 Energetic – aqua / teal
    val Energetic = GirlSpacePalette(
        primary = Color(0xFFE0F7FA),
        secondary = Color(0xFF4DD0E1),
        text = Color(0xFF006064)
    )

    // 💜 Elegant – cool gray / purple
    val Elegant = GirlSpacePalette(
        primary = Color(0xFFEDEFF5),
        secondary = Color(0xFF90A4AE),
        text = Color(0xFF263238)
    )

    // 🩷 Playful – bright sunny pastel
    val Playful = GirlSpacePalette(
        primary = Color(0xFFFFF8E1),
        secondary = Color(0xFFFFD54F),
        text = Color(0xFFF57F17)
    )

    // 🌸 Feminine default – classic soft pink
    val Feminine = GirlSpacePalette(
        primary = Color(0xFFFDEFF7),
        secondary = Color(0xFFF8BBD0),
        text = Color(0xFF880E4F)
       )

    // Used for daily/random modes
    val DailyThemes = listOf(Calm, Romantic, Bold, Energetic, Elegant, Playful)
  }

data class GirlSpacePalette(
    val primary: Color,
    val secondary: Color,
    val text: Color
)
