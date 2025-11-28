package com.girlspace.app.data.feed

import com.google.firebase.Timestamp

data class Post(
    val postId: String = "",
    val uid: String = "",
    val text: String = "",
    val imageUrls: List<String> = emptyList(),   // 👈 multiple images
    val createdAt: Timestamp? = null,
    val likeCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val commentsCount: Int = 0,
    val authorName: String? = null,
    val authorPhoto: String? = null,
    val isAuthorPremium: Boolean = false

)
