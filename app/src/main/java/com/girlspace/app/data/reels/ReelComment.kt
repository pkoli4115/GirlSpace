package com.girlspace.app.data.reels

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Keep
data class ReelComment(
    @DocumentId
    var id: String = "", // ✅ must be var for safest Firestore mapping
    val reelId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)
