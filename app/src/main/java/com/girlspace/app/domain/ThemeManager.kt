package com.girlspace.app.domain

import com.girlspace.app.ui.theme.ColorPalettes
import com.girlspace.app.ui.theme.GirlSpacePalette
import java.time.LocalDate

object ThemeManager {

    fun resolveTheme(
        mode: String,
        mood: String,
        staticTheme: String
    ): GirlSpacePalette {

        return when (mode) {

            "mood" -> selectMoodPalette(mood)

            "daily" -> {
                val index = LocalDate.now().dayOfWeek.value % ColorPalettes.DailyThemes.size
                ColorPalettes.DailyThemes[index]
            }

            "random" -> {
                ColorPalettes.DailyThemes.random()
            }

            "static" -> {
                ColorPalettes.Feminine
            }

            else -> ColorPalettes.Feminine
        }
    }

    private fun selectMoodPalette(mood: String): GirlSpacePalette {
        return when (mood.lowercase()) {
            "calm" -> ColorPalettes.Calm
            "romantic" -> ColorPalettes.Romantic
            "bold" -> ColorPalettes.Bold
            "energetic" -> ColorPalettes.Energetic
            "elegant" -> ColorPalettes.Elegant
            "playful" -> ColorPalettes.Playful
            else -> ColorPalettes.Feminine
        }
    }
}
