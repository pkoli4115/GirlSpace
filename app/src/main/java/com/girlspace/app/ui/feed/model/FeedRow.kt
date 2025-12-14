package com.girlspace.app.ui.feed

import com.girlspace.app.data.feed.Post
sealed class FeedRow {

    data class PostRow(
        val post: Post
    ) : FeedRow()

    data class AdRow(
        val adId: String = "native_ad"
    ) : FeedRow()
}
