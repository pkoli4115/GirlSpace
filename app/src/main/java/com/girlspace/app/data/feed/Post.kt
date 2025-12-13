package com.girlspace.app.data.feed
import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.Timestamp

/**
 * Feed post model.
 *
 * NOTE:
 *  - `imageUrls` can hold multiple images.
 *  - `videoUrls` is optional; use it for short clips / reels attached to the post.
 */
@Keep
@IgnoreExtraProperties
data class Post(
    val postId: String = "",
    val uid: String = "",
    val text: String = "",

    // Media
    val imageUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList(),

    // Meta
    val createdAt: Timestamp? = null,
    val likeCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val commentsCount: Int = 0,

    // Author info (denormalised for fast feed rendering)
    val authorName: String? = null,
    val authorPhoto: String? = null,
    val isAuthorPremium: Boolean = false
)
