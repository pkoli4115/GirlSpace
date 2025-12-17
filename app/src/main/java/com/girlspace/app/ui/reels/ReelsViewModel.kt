package com.girlspace.app.ui.reels
import com.google.firebase.Timestamp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.data.reels.ReelComment
import com.girlspace.app.data.reels.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    // -------------------------
    // Reels paging state
    // -------------------------
    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore

    private var cursorCreatedAt: Timestamp? = null
    private var cursorId: String? = null

    // -------------------------
    // Optimistic UI: likes + views
    // -------------------------
    private val _likedByMe = MutableStateFlow<Set<String>>(emptySet())
    val likedByMe: StateFlow<Set<String>> = _likedByMe

    // Track views fired in this session (avoid spamming)
    private val viewedThisSession = mutableSetOf<String>()

    // -------------------------
    // Comments sheet state
    // -------------------------
    private val _commentsForReelId = MutableStateFlow<String?>(null)
    val commentsForReelId: StateFlow<String?> = _commentsForReelId

    private val _comments = MutableStateFlow<List<ReelComment>>(emptyList())
    val comments: StateFlow<List<ReelComment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    private val _commentsCanLoadMore = MutableStateFlow(true)
    val commentsCanLoadMore: StateFlow<Boolean> = _commentsCanLoadMore

    private var commentsCursor: ReelsRepository.CommentsCursor? = null

    fun logShare(reelId: String) {
        viewModelScope.launch {
            runCatching { repo.logShare(reelId) }
        }
    }

    // -------------------------
    // Public API
    // -------------------------
    fun refresh() = loadInitial(force = true)
    fun loadInitial(force: Boolean = false) {
        if (!force && _reels.value.isNotEmpty()) return
        resetPaging()
        loadNext()
    }


    fun loadNext() {
        if (_isLoading.value || !_canLoadMore.value) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val page = repo.loadPage(
                    pageSize = 18,
                    cursorCreatedAt = cursorCreatedAt,
                    cursorId = cursorId
                )

                val merged = (_reels.value + page.items).distinctBy { it.id }
                _reels.value = merged

                // ✅ hydrate liked state so viewer/grid shows correct icon immediately
                if (page.likedByMeIds.isNotEmpty()) {
                    _likedByMe.update { it + page.likedByMeIds }
                }

                cursorCreatedAt = page.nextCursorCreatedAt
                cursorId = page.nextCursorId

                if (page.items.isEmpty() || cursorCreatedAt == null || cursorId.isNullOrBlank()) {
                    _canLoadMore.value = false
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Call this after uploadReel() returns reelId.
     * Inserts new reel at top (if fetch succeeds) and also keeps paging stable.
     */
    fun onUploadCompleted(reelId: String) {
        viewModelScope.launch {
            val reel = repo.getReelById(reelId) ?: run {
                refresh()
                return@launch
            }
            _reels.update { list ->
                listOf(reel) + list.filterNot { it.id == reelId }
            }
            // keep canLoadMore true (grid can continue to paginate)
            _canLoadMore.value = true
        }
    }

    /**
     * Ensure a reel exists in the current list (deep link / open from outside grid).
     * If missing, fetch and prepend.
     */
    fun ensureReelInList(reelId: String) {
        if (_reels.value.any { it.id == reelId }) return

        viewModelScope.launch {
            val reel = repo.getReelById(reelId) ?: return@launch
            _reels.update { list ->
                listOf(reel) + list.filterNot { it.id == reelId }
            }
        }
    }

    fun isLiked(reelId: String): Boolean = _likedByMe.value.contains(reelId)

    fun toggleLike(reel: Reel) {
        val reelId = reel.id
        val wasLiked = isLiked(reelId)

        // ✅ optimistic: update UI immediately
        _likedByMe.update { set ->
            if (wasLiked) set - reelId else set + reelId
        }
        updateReelMetric(reelId, "likes", delta = if (wasLiked) -1 else 1)

        viewModelScope.launch {
            try {
                repo.toggleLike(reel)
            } catch (_: Throwable) {
                // ❌ rollback on failure
                _likedByMe.update { set ->
                    if (wasLiked) set + reelId else set - reelId
                }
                updateReelMetric(reelId, "likes", delta = if (wasLiked) +1 else -1)
            }
        }
    }

    /**
     * Call when a reel becomes "active" in the Viewer (or autoplay).
     * - optimistic local increment
     * - Firestore increment (best effort)
     */
    fun onReelViewed(reelId: String) {
        if (!viewedThisSession.add(reelId)) return

        updateReelMetric(reelId, "views", delta = 1)

        viewModelScope.launch {
            runCatching { repo.incrementView(reelId) }
        }
    }

    fun openComments(reelId: String) {
        _commentsForReelId.value = reelId
        _comments.value = emptyList()
        _commentsCanLoadMore.value = true
        commentsCursor = null
        loadMoreComments()
    }

    fun closeComments() {
        _commentsForReelId.value = null
        _comments.value = emptyList()
        _commentsLoading.value = false
        _commentsCanLoadMore.value = true
        commentsCursor = null
    }

    fun loadMoreComments() {
        val reelId = _commentsForReelId.value ?: return
        if (_commentsLoading.value || !_commentsCanLoadMore.value) return

        _commentsLoading.value = true
        viewModelScope.launch {
            try {
                val (page, nextCursor) = repo.loadCommentsPage(
                    reelId = reelId,
                    pageSize = 20,
                    cursor = commentsCursor
                )

                _comments.update { old -> (old + page).distinctBy { it.id } }
                commentsCursor = nextCursor

                if (page.isEmpty() || nextCursor == null) {
                    _commentsCanLoadMore.value = false
                }
            } finally {
                _commentsLoading.value = false
            }
        }
    }

    fun addComment(reelId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            try {
                val newComment = repo.addComment(reelId, trimmed)
                // ✅ insert immediately; no full refresh / no scroll jump
                _comments.update { list ->
                    listOf(newComment) + list.filterNot { it.id == newComment.id }
                }
            } catch (_: Throwable) {
                // ignore for now (add snackbar later)
            }
        }
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun resetPaging() {
        _reels.value = emptyList()
        _isLoading.value = false
        _canLoadMore.value = true
        cursorCreatedAt = null
        cursorId = null
    }

    private fun updateReelMetric(reelId: String, key: String, delta: Int) {
        _reels.update { list ->
            list.map { r ->
                if (r.id != reelId) r else {
                    val current = r.metricLong(key)
                    r.withMetric(key, (current + delta).coerceAtLeast(0).toLong())
                }
            }
        }
    }
}

/**
 * These helpers keep you safe no matter how your Reel model stores metrics.
 * - If metrics is a Map -> it works
 * - If metrics is null -> works
 */
private fun Reel.metricLong(key: String): Long {
    val m = this.metrics
    return when (m) {
        is Map<*, *> -> (m[key] as? Number)?.toLong() ?: 0L
        else -> 0L
    }
}

private fun Reel.withMetric(key: String, value: Long): Reel {
    val m = this.metrics
    val newMap: Map<String, Any> = when (m) {
        is Map<*, *> -> {
            val base = m.entries.associate { it.key.toString() to (it.value as Any) }.toMutableMap()
            base[key] = value
            base
        }
        else -> mapOf(key to value)
    }

    return this.copy(metrics = newMap)
}
