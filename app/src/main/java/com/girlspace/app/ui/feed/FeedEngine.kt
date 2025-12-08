package com.girlspace.app.ui.feed

import com.girlspace.app.data.feed.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core engine that loads:
 *  - paginated posts
 *  - paginated reels
 *  - ads (configurable)
 *  - top picks
 *  - evergreen content
 * Then merges them into a mixed, ranked feed.
 */
class FeedEngine(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ---------------------------------------------------------
    // Pagination state
    // ---------------------------------------------------------
    private var lastPostSnapshot: com.google.firebase.firestore.DocumentSnapshot? = null
    private var lastReelSnapshot: com.google.firebase.firestore.DocumentSnapshot? = null

    private val isLoadingPosts = AtomicBoolean(false)
    private val isLoadingReels = AtomicBoolean(false)

    // ---------------------------------------------------------
    // Configurable parameters (remote config supported)
    // ---------------------------------------------------------
    var pageSizePosts = 10
    var pageSizeReels = 6
    var adInterval = 7                // insert an ad every 7 items
    var enableAds = true

    // Reels merge behavior:
    //  - if TRUE → both dedicated reels and post-video reels are included
    var includeReelsFromPosts = true

    // ---------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------

    /**
     * Loads the first page of everything:
     *   posts + reels
     */
    suspend fun loadInitialPage(): List<FeedItem> = withContext(Dispatchers.IO) {
        resetPagination()

        val postsDeferred = async { loadNextPostsInternal() }
        val reelsDeferred = async { loadNextReelsInternal() }

        val posts = postsDeferred.await()
        val reels = reelsDeferred.await()

        return@withContext assembleFeed(
            posts = posts,
            reels = reels,
            prependTopPicks = true
        )
    }

    /**
     * Loads next page during infinite scroll.
     */
    suspend fun loadNextPage(): List<FeedItem> = withContext(Dispatchers.IO) {

        val postsDeferred = async { loadNextPostsInternal() }
        val reelsDeferred = async { loadNextReelsInternal() }

        val newPosts = postsDeferred.await()
        val newReels = reelsDeferred.await()

        return@withContext assembleFeed(
            posts = newPosts,
            reels = newReels,
            prependTopPicks = false
        )
    }

    // ---------------------------------------------------------
    // INTERNAL LOADERS
    // ---------------------------------------------------------

    private suspend fun loadNextPostsInternal(): List<Post> {
        if (isLoadingPosts.get()) return emptyList()

        isLoadingPosts.set(true)

        return try {
            var query = firestore.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSizePosts.toLong())

            if (lastPostSnapshot != null) {
                query = query.startAfter(lastPostSnapshot!!)
            }

            val snapshot = query.get().await()
            if (snapshot.documents.isNotEmpty()) {
                lastPostSnapshot = snapshot.documents.last()
            }

            snapshot.documents.mapNotNull { doc ->
                val post = doc.toObject(Post::class.java)
                post?.copy(postId = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            isLoadingPosts.set(false)
        }
    }

    private suspend fun loadNextReelsInternal(): List<RawReelData> {
        if (isLoadingReels.get()) return emptyList()

        isLoadingReels.set(true)

        return try {
            var query = firestore.collection("reels")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSizeReels.toLong())

            if (lastReelSnapshot != null) {
                query = query.startAfter(lastReelSnapshot!!)
            }

            val snapshot = query.get().await()
            if (snapshot.documents.isNotEmpty()) {
                lastReelSnapshot = snapshot.documents.last()
            }

            snapshot.documents.mapNotNull { doc ->
                RawReelData(
                    id = doc.id,
                    videoUrl = doc.getString("videoUrl") ?: return@mapNotNull null,
                    thumbnailUrl = doc.getString("thumbnailUrl"),
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            isLoadingReels.set(false)
        }
    }

    // ---------------------------------------------------------
    // FEED ASSEMBLY
    // ---------------------------------------------------------

    private fun assembleFeed(
        posts: List<Post>,
        reels: List<RawReelData>,
        prependTopPicks: Boolean
    ): List<FeedItem> {

        val merged = mutableListOf<FeedItem>()

        // Optional: Prepend Top Picks on initial load only
        if (prependTopPicks) {
            merged += FeedItem.TopPicks(
                picks = listOf(
                    TopPickData("1", "Popular Today", "https://picsum.photos/600/302"),
                    TopPickData("2", "Trending", "https://picsum.photos/600/303"),
                    TopPickData("3", "Inspiring", "https://picsum.photos/600/304"),
                )
            )
        }

        // Convert posts → FeedItem.PostItem
        val postItems = posts.map { FeedItem.PostItem(it) }

        // Convert reels → FeedItem.ReelItem
        val reelItems = reels.map {
            FeedItem.ReelItem(
                id = it.id,
                videoUrl = it.videoUrl,
                thumbnailUrl = it.thumbnailUrl,
                createdAt = it.createdAt
            )
        }

        // Optionally generate reels from posts with videos
        val postVideoReels = if (includeReelsFromPosts) {
            posts.filter { it.videoUrls.isNotEmpty() }
                .map { p ->
                    FeedItem.ReelItem(
                        id = "postReel_${p.postId}",
                        videoUrl = p.videoUrls.first(),
                        thumbnailUrl = p.imageUrls.firstOrNull(),
                        createdAt = p.createdAt?.toDate()?.time ?: 0L
                    )
                }
        } else {
            emptyList()
        }

        // Merge them all into a single pool
        val pool = mutableListOf<FeedItem>()
        pool.addAll(postItems)
        pool.addAll(reelItems)
        pool.addAll(postVideoReels)

        // Ranking pipeline (calls FeedRanking)
        val ranked = FeedRanking.rank(pool)

        // Ads insertion
        val withAds = injectAds(ranked)

        // Evergreen fallback if too few posts
        if (withAds.isEmpty()) {
            withAds += FeedItem.EvergreenCard(
                title = "Welcome to GirlSpace",
                caption = "Be the first to post and inspire others ✨"
            )
        }

        return withAds
    }

    // ---------------------------------------------------------
    // AD INSERTION LOGIC
    // ---------------------------------------------------------
    private fun injectAds(items: List<FeedItem>): MutableList<FeedItem> {
        if (!enableAds) return items.toMutableList()

        val output = mutableListOf<FeedItem>()
        var countSinceLastAd = 0

        items.forEach { item ->
            output += item
            countSinceLastAd++

            if (countSinceLastAd >= adInterval) {
                countSinceLastAd = 0
                output += FeedItem.AdItem(
                    adId = "ad_${System.nanoTime()}",
                    imageUrl = "https://picsum.photos/seed/ad${System.nanoTime()}/600/400",
                    clickUrl = null,
                    weight = 1.0
                )
            }
        }

        return output
    }

    // ---------------------------------------------------------
    // RESET PAGINATION (called on refresh / initial load)
    // ---------------------------------------------------------
    fun resetPagination() {
        lastPostSnapshot = null
        lastReelSnapshot = null
    }
}
