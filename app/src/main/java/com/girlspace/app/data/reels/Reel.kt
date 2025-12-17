package com.girlspace.app.data.reels

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Keep
data class Reel(
    @DocumentId val id: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val durationSec: Int = 0,
    val caption: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val visibility: String = "PUBLIC",
    val tags: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val source: Map<String, Any?> = emptyMap(),
    val metrics: Map<String, Any?> = emptyMap()
) {
    fun isSeededPixabay(): Boolean =
        (source["provider"] as? String).orEmpty().equals("pixabay", ignoreCase = true)

    fun likeCount(): Long = (metrics["likes"] as? Number)?.toLong() ?: 0L
    fun shareCount(): Long = (metrics["shares"] as? Number)?.toLong() ?: 0L
    fun viewCount(): Long = (metrics["views"] as? Number)?.toLong() ?: 0L
}
