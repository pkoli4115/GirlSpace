package com.girlspace.app.data.feed

import com.google.firebase.Timestamp

data class Comment(
    val commentId: String = "",
    val uid: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val authorName: String? = null,
    val authorPhoto: String? = null,
)
