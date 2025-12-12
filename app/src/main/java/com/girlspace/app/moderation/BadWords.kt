// BadWords.kt
data class BadWords(
    val english: List<String>,
    val hindi: List<String>,
    val telugu: List<String>,
    val hinglish_telugu: List<String>,
    val patterns: Patterns
)

data class Patterns(
    val starts_with: List<String>,
    val starts_with_telugu: List<String>
)
