package com.girlspace.app.ui.feed

import com.girlspace.app.data.feed.Post
import com.google.firebase.Timestamp
import kotlin.math.max

/**
 * Ranking engine for the mixed feed.
 *
 * For now we use a simple, deterministic scoring:
 *  score = 0.4 * recency + 0.4 * engagement + 0.2 * interest
 *
 * All terms are normalized to [0, 1].
 */
object FeedRanking {

    fun rank(items: List<FeedItem>): List<FeedItem> {
        if (items.isEmpty()) return items

        // Keep original index to preserve stable ordering when scores tie.
        val withIndex = items.mapIndexed { index, item -> IndexedItem(item, index) }

        // Precompute raw metrics
        val recencies = withIndex.associate { it.item to rawRecency(it.item) }
        val engagements = withIndex.associate { it.item to rawEngagement(it.item) }
        val interests = withIndex.associate { it.item to rawInterest(it.item) }

        // Normalize
        val recencyNorm = normalize(recencies.values.toList())
        val engagementNorm = normalize(engagements.values.toList())
        val interestNorm = normalize(interests.values.toList())

        val recencyByItem = recencies.keys.zip(recencyNorm).toMap()
        val engagementByItem = engagements.keys.zip(engagementNorm).toMap()
        val interestByItem = interests.keys.zip(interestNorm).toMap()

        val scored = withIndex.map { indexed ->
            val item = indexed.item
            val rec = recencyByItem[item] ?: 0.0
            val eng = engagementByItem[item] ?: 0.0
            val int = interestByItem[item] ?: 0.0

            val score = 0.4 * rec + 0.4 * eng + 0.2 * int
            ScoredItem(item, indexed.originalIndex, score)
        }

        // Sort by score desc, tie-break by original index asc
        return scored
            .sortedWith(
                compareByDescending<ScoredItem> { it.score }
                    .thenBy { it.originalIndex }
            )
            .map { it.item }
    }

    // ------------------------------------------------------------------------
    // Raw metric extractors
    // ------------------------------------------------------------------------

    private fun rawRecency(item: FeedItem): Double {
        val millis = when (item) {
            is FeedItem.PostItem -> item.post.createdAt.toMillis()
            is FeedItem.ReelItem -> item.createdAt
            else -> 0L
        }
        return millis.toDouble()
    }

    private fun rawEngagement(item: FeedItem): Double {
        return when (item) {
            is FeedItem.PostItem -> {
                val p = item.post
                (p.likeCount + p.commentsCount).toDouble()
            }
            // For now, reels don’t have explicit engagement metrics
            else -> 0.0
        }
    }

    private fun rawInterest(item: FeedItem): Double {
        return when (item) {
            is FeedItem.PostItem -> {
                val p = item.post
                var score = 0.0
                if (p.isAuthorPremium) score += 1.0
                if (p.imageUrls.isNotEmpty() || p.videoUrls.isNotEmpty()) score += 1.0
                score
            }
            else -> 0.0
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun Timestamp?.toMillis(): Long {
        return this?.toDate()?.time ?: 0L
    }

    /**
     * Normalize a list of raw values to [0, 1].
     * If all values are identical, everything becomes 0.5 (neutral).
     */
    private fun normalize(values: List<Double>): List<Double> {
        if (values.isEmpty()) return values

        val min = values.minOrNull() ?: 0.0
        val maxVal = values.maxOrNull() ?: 0.0
        val range = max(maxVal - min, 1e-9)

        // All equal → flat 0.5
        if (range < 1e-9) {
            return List(values.size) { 0.5 }
        }

        return values.map { (it - min) / range }
    }

    private data class IndexedItem(
        val item: FeedItem,
        val originalIndex: Int
    )

    private data class ScoredItem(
        val item: FeedItem,
        val originalIndex: Int,
        val score: Double
    )
}
