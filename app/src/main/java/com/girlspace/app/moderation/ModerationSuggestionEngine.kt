package com.girlspace.app.moderation

import javax.inject.Inject
import javax.inject.Singleton

data class ModerationInsight(
    val mainAbusiveTerm: String?,
    val allAbusiveTerms: List<String>,
    val suggestedCategory: String,
    val adminNote: String
)

@Singleton
class ModerationSuggestionEngine @Inject constructor() {

    fun buildInsight(
        decision: ModerationDecision
    ): ModerationInsight {
        if (!decision.isAbusive || decision.abusiveTerms.isEmpty()) {
            return ModerationInsight(
                mainAbusiveTerm = null,
                allAbusiveTerms = emptyList(),
                suggestedCategory = "None",
                adminNote = "No abusive terms detected."
            )
        }

        val main = decision.abusiveTerms.first()

        val category = when {
            // simple categorisation, you can refine later
            main.contains("lanja", ignoreCase = true) ||
                    main.contains("లంజా") ||
                    main.contains("slut", ignoreCase = true) ||
                    main.contains("whore", ignoreCase = true) ->
                "Sexual insult"

            main.contains("fuck", ignoreCase = true) ||
                    main.contains("shit", ignoreCase = true) ||
                    main.contains("చోదు") ||
                    main.contains("चूत") ->
                "Strong profanity"

            else -> "General abuse"
        }

        val note = "User used abusive term '$main'. " +
                "Review context (chat/post) and decide: warn, mute, or ban."

        return ModerationInsight(
            mainAbusiveTerm = main,
            allAbusiveTerms = decision.abusiveTerms,
            suggestedCategory = category,
            adminNote = note
        )
    }
}
