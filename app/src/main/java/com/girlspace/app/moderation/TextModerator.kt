package com.girlspace.app.moderation

import javax.inject.Inject
import javax.inject.Singleton

enum class ContentKind {
    CHAT_MESSAGE,
    FEED_POST,
    COMMENT,
    GROUP_NAME,
    GROUP_DESCRIPTION,
    USER_BIO
}

data class ModerationDecision(
    val isAbusive: Boolean,
    val shouldWarnUser: Boolean,
    val abusiveTerms: List<String> = emptyList()
)

@Singleton
class TextModerator @Inject constructor(
    private val badWordsProvider: BadWordsProvider
) {

    fun moderateText(
        textInput: String,
        kind: ContentKind
    ): ModerationDecision {
        val config = badWordsProvider.getConfig()
        val text = textInput.lowercase()

        val detected = mutableSetOf<String>()

        // 1) Exact words (all langs)
        val exactWords = buildList {
            addAll(config.english)
            addAll(config.hindi)
            // telugu entries without wildcard
            addAll(config.telugu.filterNot { it.endsWith("*") })
            addAll(config.hinglishTelugu)
        }.map { it.lowercase() }

        exactWords.forEach { word ->
            if (word.isNotBlank() && text.contains(word)) {
                detected += word
            }
        }

        // 2) Wildcard in Latin (lanja*, modda*)
        config.patterns.startsWith.forEach { root ->
            if (root.isBlank()) return@forEach
            val regex = Regex("\\b${Regex.escape(root)}[a-zA-Z]*\\b")
            if (regex.containsMatchIn(text)) {
                detected += "$root*"
            }
        }

        // 3) Wildcard in Telugu (లంజా*, మొద్దా*)
        config.patterns.startsWithTelugu.forEach { root ->
            if (root.isBlank()) return@forEach
            val regex = Regex("${Regex.escape(root)}[\\u0C00-\\u0C7F]*")
            if (regex.containsMatchIn(text)) {
                detected += "${root}*"
            }
        }

        // 4) Handle elongated spam like "laaaanjaaaa"
        val cleaned = text.replace(Regex("(.)\\1+"), "$1")
        exactWords.forEach { word ->
            if (word.isNotBlank() && cleaned.contains(word)) {
                detected += word
            }
        }

        val abusive = detected.isNotEmpty()

        return ModerationDecision(
            isAbusive = abusive,
            shouldWarnUser = abusive, // Option B: soft warning
            abusiveTerms = detected.toList()
        )
    }
}
