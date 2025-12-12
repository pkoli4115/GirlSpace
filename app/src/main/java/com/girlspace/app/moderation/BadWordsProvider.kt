package com.girlspace.app.moderation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PatternsConfig(
    @SerializedName("starts_with")
    val startsWith: List<String> = emptyList(),
    @SerializedName("starts_with_telugu")
    val startsWithTelugu: List<String> = emptyList()
)

data class BadWordsConfig(
    val english: List<String> = emptyList(),
    val hindi: List<String> = emptyList(),
    val telugu: List<String> = emptyList(),
    @SerializedName("hinglish_telugu")
    val hinglishTelugu: List<String> = emptyList(),
    val patterns: PatternsConfig = PatternsConfig()
)

@Singleton
class BadWordsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var cachedConfig: BadWordsConfig? = null

    fun getConfig(): BadWordsConfig {
        val existing = cachedConfig
        if (existing != null) return existing

        synchronized(this) {
            val again = cachedConfig
            if (again != null) return again

            val json = context.assets
                .open("bad_words.json")
                .bufferedReader()
                .use { it.readText() }

            val config = Gson().fromJson(json, BadWordsConfig::class.java)
            cachedConfig = config
            return config
        }
    }
}
