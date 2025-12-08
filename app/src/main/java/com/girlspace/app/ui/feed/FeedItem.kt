package com.girlspace.app.ui.feed

import com.girlspace.app.data.feed.Post
import java.util.UUID

/**
 * Unified feed model used by FeedEngine, FeedViewModel, and FeedScreen.
 * Every feed element becomes a FeedItem.
 */
sealed class FeedItem {

    /** Unique & stable key for LazyColumn keying */
    abstract val key: String

    // -------------------------------------------------------------
    // PRIMARY FEED TYPES
    // -------------------------------------------------------------

    /** Normal post (your existing Post model) */
    data class PostItem(
        val post: Post,
        val postId: String = "",
        val likedBy: List<String> = emptyList(),
        val likeCount: Int = 0,

        ) : FeedItem() {
        override val key: String = "post_${post.postId}"
    }

    /** Reel item (from dedicated `reels` collection or from posts videoUrls) */
    data class ReelItem(
        val id: String,
        val videoUrl: String,
        val thumbnailUrl: String?,
        val createdAt: Long
    ) : FeedItem() {
        override val key: String = "reel_$id"
    }

    /** Ad item (image + click action) */
    data class AdItem(
        val adId: String,
        val imageUrl: String,
        val clickUrl: String?,
        val weight: Double
    ) : FeedItem() {
        override val key: String = "ad_$adId"
    }

    // -------------------------------------------------------------
    // DECORATIVE / UX ELEMENTS
    // -------------------------------------------------------------

    /** Horizontal Top Picks (always shown at feed top) */
    data class TopPicks(
        val picks: List<TopPickData>
    ) : FeedItem() {
        override val key: String = "top_picks"
    }

    /** Evergreen filler card shown when feed is empty or too short */
    data class EvergreenCard(
        val title: String,
        val caption: String
    ) : FeedItem() {
        override val key: String = "evergreen_${title.hashCode()}"
    }

    /** Loading shimmer block used during pagination */
    data class LoadingBlock(
        val blockId: String = UUID.randomUUID().toString()
    ) : FeedItem() {
        override val key: String = "loading_$blockId"
    }

    /** Error block (when pagination or fetch fails) */
    data class ErrorBlock(
        val message: String
    ) : FeedItem() {
        override val key: String = "error_${message.hashCode()}"
    }
}

/**
 * Top pick data items, used in the horizontal reel at top of feed.
 */
data class TopPickData(
    val id: String,
    val title: String,
    val imageUrl: String
)

/**
 * Raw reel data that comes from Firestore before transformed into FeedItem.ReelItem.
 */
data class RawReelData(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val createdAt: Long
)

/**
 * Ad metadata fetched from Firestore or Remote Config.
 */
data class AdData(
    val id: String,
    val imageUrl: String,
    val clickUrl: String?,
    val weight: Double = 1.0
)
