package com.girlspace.app.ui.feed
import kotlinx.coroutines.flow.asStateFlow

import com.girlspace.app.ads.AdConfig
import com.girlspace.app.ads.NativeAdLoader
import com.google.android.gms.ads.nativead.NativeAd
import com.girlspace.app.moderation.ImageModerationResult
import com.girlspace.app.moderation.ModerationManager
import com.girlspace.app.moderation.ImageModerator
import com.girlspace.app.moderation.ContentKind
import com.google.firebase.firestore.FieldValue
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.core.plan.PlanLimitsRepository
import com.girlspace.app.data.feed.CreatePostResult
import com.girlspace.app.data.feed.FeedRepository
import com.girlspace.app.data.feed.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedViewModel : ViewModel() {
    private val imageModerator = ImageModerator()

    private val firestore = FirebaseFirestore.getInstance()
    private val repo = FeedRepository(
        auth = FirebaseAuth.getInstance(),
        firestore = firestore,
        storage = FirebaseStorage.getInstance()
    )
    private val engine = FeedEngine(firestore)
    private val moderationManager = ModerationManager()
    // ------------------------------------------------------------------------
// ADS (Native AdMob) state
// ------------------------------------------------------------------------
    private val loadedAds = mutableListOf<NativeAd>()
    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> = _adsReady

    private var isAdLoading = false
    private val _hiddenTargetIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTargetIds: StateFlow<Set<String>> = _hiddenTargetIds.asStateFlow()

    // Mixed feed items (posts + reels + ads + top picks + evergreen)
    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems

    // For compatibility (if any other place still uses posts)
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading
    private val _isPaging = MutableStateFlow(false)
    val isPaging: StateFlow<Boolean> = _isPaging
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // feed items backing state

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating

    private val _premiumRequired = MutableStateFlow(false)
    val premiumRequired: StateFlow<Boolean> = _premiumRequired

    // For dialog text ("your plan allows up to X images")
    private val _currentMaxImages = MutableStateFlow(1)
    val currentMaxImages: StateFlow<Int> = _currentMaxImages

    init {
        Log.e("FEED_RELEASE", "FeedViewModel initialized – calling refresh()")
        listenHiddenContent()
        refresh()
    }



    // ------------------------------------------------------------------------
    // FEED LOADING
    // ------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            _isInitialLoading.value = true
            _hasMore.value = true
            engine.resetPagination()
            try {
                val items = engine.loadInitialPage()
                applyNewFeedItems(items)
            } catch (e: Exception) {
                Log.e("FeedViewModel", "refresh: failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to load feed"
                _feedItems.value = emptyList()
                _posts.value = emptyList()
            } finally {
                _isInitialLoading.value = false
            }
        }
    }
    private fun listenHiddenContent() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("hiddenContent")
            .addSnapshotListener { snap, _ ->
                val ids = snap?.documents
                    ?.mapNotNull { it.getString("targetId") }
                    ?.toSet()
                    ?: emptySet()

                _hiddenTargetIds.value = ids
                // ✅ re-apply filter to current feed immediately
                _feedItems.value = filterHidden(_feedItems.value)
                _posts.value = _feedItems.value
                    .filterIsInstance<FeedItem.PostItem>()
                    .map { it.post }

            }
    }
    private fun filterHidden(items: List<FeedItem>): List<FeedItem> {
        val hidden = _hiddenTargetIds.value
        if (hidden.isEmpty()) return items

        return items.filterNot { item ->
            when (item) {
                is FeedItem.PostItem -> hidden.contains(item.post.postId)
                is FeedItem.ReelItem -> hidden.contains(item.id)
                else -> false
            }
        }
    }

    fun loadNextPageIfNeeded() {
        if (_isPaging.value || !_hasMore.value) return

        viewModelScope.launch {
            _isPaging.value = true
            try {
                val next = engine.loadNextPage()
                if (next.isEmpty()) {
                    _hasMore.value = false
                } else {
                    val combined = _feedItems.value + next
                    val deduped = dedupeByKey(combined)
                    applyNewFeedItems(injectAds(deduped))
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "loadNextPage: failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to load more"
            } finally {
                _isPaging.value = false
            }
        }
    }

    private fun dedupeByKey(list: List<FeedItem>): List<FeedItem> {
        val seen = mutableSetOf<String>()
        return list.filter { seen.add(it.key) }
    }

    private fun applyNewFeedItems(items: List<FeedItem>) {
        val filtered = filterHidden(items)
        _feedItems.value = filtered
        _posts.value = filtered
            .filterIsInstance<FeedItem.PostItem>()
            .map { it.post }
    }

// ------------------------------------------------------------------------
// ADS (Native AdMob) - Load + Inject
// ------------------------------------------------------------------------

    /**
     * Call once from UI (FeedScreen) to preload a few native ads.
     * Safe: if ads fail, feed still works.
     */
    fun ensureAdsLoaded(context: Context) {
        if (isAdLoading || loadedAds.size >= 3) return

        isAdLoading = true

        val loader = NativeAdLoader(
            context = context.applicationContext,
            adUnitId = "ca-app-pub-3940256099942544/2247696110" // ✅ TEST native ad unit
        )

        // Load 3 ads (enough for many scrolls)
        var loadedCount = 0
        repeat(3) {
            loader.load(
                onLoaded = { ad ->
                    loadedAds.add(ad)
                    loadedCount++
                    if (loadedCount >= 1) {
                        _adsReady.value = true
                    }
                    // stop loading flag once we've tried enough
                    if (loadedAds.size >= 3) {
                        isAdLoading = false
                    }
                },
                onFailed = {
                    // fail-open: never break feed
                    // Once all attempts are done, drop flag
                    loadedCount++
                    if (loadedCount >= 3) {
                        isAdLoading = false
                    }
                }
            )
        }
    }

    /**
     * Inserts already-loaded ads into feed after every N posts.
     * Safe: if no ads, returns original list.
     */
    private fun injectAds(items: List<FeedItem>): List<FeedItem> {
        val clean = items.filterNot { it is FeedItem.AdItem } // ✅ prevents duplicates

        val result = mutableListOf<FeedItem>()
        var postCount = 0
        var adIndex = 0

        clean.forEach { item ->
            result.add(item)

            if (item is FeedItem.PostItem) {
                postCount++
                if (postCount % 5 == 0 && adIndex < loadedAds.size) {
                    result.add(
                        FeedItem.AdItem(
                            adId = "ad_${java.util.UUID.randomUUID()}",
                            adPosition = postCount,
                            nativeAd = loadedAds[adIndex],
                            clickUrl = "",     // only if required by your data class
                            imageUrl = "",     // only if required by your data class
                            weight = 1         // only if required by your data class
                        )
                    )
                    adIndex++
                }
            }
        }
        return result
    }



    // ------------------------------------------------------------------------
    // UI FLAGS
    // ------------------------------------------------------------------------

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPremiumRequired() {
        _premiumRequired.value = false
    }

    // ------------------------------------------------------------------------
    // ACTIONS
    // ------------------------------------------------------------------------

    fun toggleLike(post: Post) {
        val uid = auth.currentUser?.uid ?: return
        val postId = post.postId

        // 1) Optimistic UI update – instant local change
        val currentlyLiked = post.likedBy.contains(uid)

        _feedItems.value = _feedItems.value.map { item ->
            if (item is FeedItem.PostItem && item.post.postId == postId) {
                val newLikedBy = if (currentlyLiked) {
                    item.post.likedBy.filterNot { it == uid }
                } else {
                    item.post.likedBy + uid
                }

                val newLikeCount = (item.post.likeCount ?: 0) +
                        if (currentlyLiked) -1 else 1

                item.copy(
                    post = item.post.copy(
                        likedBy = newLikedBy,
                        likeCount = newLikeCount.coerceAtLeast(0)
                    )
                )
            } else {
                item
            }
        }

        // 2) Firestore update in background – real source of truth
        val postRef = firestore.collection("posts").document(postId)

        val likeUpdate = if (currentlyLiked) {
            mapOf(
                "likedBy" to FieldValue.arrayRemove(uid),
                "likeCount" to FieldValue.increment(-1)
            )
        } else {
            mapOf(
                "likedBy" to FieldValue.arrayUnion(uid),
                "likeCount" to FieldValue.increment(1)
            )
        }

        postRef.update(likeUpdate)
            .addOnFailureListener { e ->
                // Optional: roll back optimistic change on failure
                // or just log for now
                e.printStackTrace()
            }
    }


    fun deletePost(post: Post, onDone: (Boolean) -> Unit) {
        _isLoading.value = true
        repo.deletePost(post) { ok ->
            _isLoading.value = false
            if (ok) {
                // Re-load feed to reflect deletion
                refresh()
            }
            onDone(ok)
        }
    }

    /**
     * Uses central plan limits:
     *  - maxImagesPerPost from PlanLimitsRepository.planLimits
     *  - If user exceeds → show premium dialog
     *  - Otherwise → decode bitmaps and create post.
     */
    fun createPost(
        context: Context,
        text: String,
        imageUris: List<Uri>,
        onSuccessClose: () -> Unit
    ) {
        if (text.isBlank() && imageUris.isEmpty()) {
            _errorMessage.value = "Write something or select at least one image"
            return
        }

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            _errorMessage.value = "Not logged in"
            return
        }

        _isCreating.value = true
        _errorMessage.value = null
        _premiumRequired.value = false

        // 1) Read current plan limits
        val limits = PlanLimitsRepository.planLimits.value
        val maxImages = limits.maxImagesPerPost
        _currentMaxImages.value = maxImages

        // 2) Check image count against plan
        if (imageUris.size > maxImages) {
            _isCreating.value = false
            _premiumRequired.value = true
            return
        }

        // 3) Decode images + IMAGE MODERATION + create post
        viewModelScope.launch {
            try {
                val trimmedText = text.trim()

                // Decode images first
                val bitmaps = withContext(Dispatchers.IO) {
                    imageUris.mapNotNull { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        } catch (e: Exception) {
                            Log.e("FeedViewModel", "Failed to decode image: $uri", e)
                            null
                        }
                    }
                }

                // 🔍 Image moderation: only if there are bitmaps
                if (bitmaps.isNotEmpty()) {
                    val imageResult: ImageModerationResult =
                        imageModerator.checkBitmaps(bitmaps)

                    if (!imageResult.isSafe) {
                        _isCreating.value = false
                        _errorMessage.value =
                            "One or more images may contain nudity or violence. " +
                                    "Please choose different images."
                        return@launch
                    }
                }

                // ✅ Create post as before
                repo.createPost(
                    text = trimmedText,
                    bitmaps = bitmaps
                ) { result ->
                    _isCreating.value = false
                    when (result) {
                        is CreatePostResult.Success -> {
                            onSuccessClose()
                            // Reload feed to include new post
                            refresh()
                        }

                        is CreatePostResult.PremiumRequired -> {
                            _premiumRequired.value = true
                        }

                        is CreatePostResult.Error -> {
                            _errorMessage.value =
                                result.message ?: "Failed to create post"
                        }
                    }
                }
            } catch (e: Exception) {
                _isCreating.value = false
                _errorMessage.value = e.localizedMessage ?: "Unexpected error"
            }
        }
    }
    private fun injectAds(
        context: Context,
        items: List<FeedItem>
    ): List<FeedItem> {
        if (loadedAds.isEmpty()) return items

        val result = mutableListOf<FeedItem>()
        var postCount = 0
        var adIndex = 0

        items.forEach { item ->
            result.add(item)

            if (item is FeedItem.PostItem) {
                postCount++

                // 🔥 Every 5 posts → insert ad
                if (postCount % 5 == 0 && adIndex < loadedAds.size) {
                    result.add(
                        FeedItem.AdItem(
                            adId = java.util.UUID.randomUUID().toString(), // ✅ always unique
                            adPosition = postCount,
                            nativeAd = loadedAds[adIndex]
                        )



                    )
                    adIndex++
                }
            }
        }
        return result
    }

    fun addComment(postId: String, text: String) {
        if (text.isBlank()) return

        val trimmed = text.trim()

        viewModelScope.launch {
            _isLoading.value = true

            // 1) Moderation
            try {
                val moderationResult = moderationManager.submitTextForModeration(
                    rawText = trimmed,
                    kind = ContentKind.FEED_POST,
                    contextId = postId
                )


                if (moderationResult.blockedLocally) {
                    _isLoading.value = false
                    _errorMessage.value =
                        moderationResult.message ?: "Comment blocked by community guidelines."
                    return@launch
                }
                // If success or failure but not blockedLocally:
                //  - success → logged to pending_content
                //  - failure → log and still allow comment to go through
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Moderation failed for comment", e)
                // Don't fully break comments just because moderation logging failed
            }

            // 2) Actual Firestore write (same as before)
            try {
                repo.addComment(postId, trimmed) { ok ->
                    _isLoading.value = false
                    if (!ok) {
                        _errorMessage.value = "Failed to add comment"
                    } else {
                        // Reload feed so commentsCount updates
                        refresh()
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value =
                    e.localizedMessage ?: "Failed to add comment"
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        engine.resetPagination()
    }
}
