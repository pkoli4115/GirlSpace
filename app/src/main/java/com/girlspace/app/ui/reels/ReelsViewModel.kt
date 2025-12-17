package com.girlspace.app.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.data.reels.ReelComment
import com.girlspace.app.data.reels.ReelsRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    // ---------------- Reels grid paging ----------------
    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore

    private var cursorCreatedAt: Timestamp? = null
    private var cursorId: String? = null

    fun loadInitial() {
        if (_reels.value.isNotEmpty()) return
        loadNext()
    }

    fun refreshNow() {
        cursorCreatedAt = null
        cursorId = null
        _canLoadMore.value = true
        _reels.value = emptyList()
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

    fun toggleLike(reel: Reel) {
        viewModelScope.launch {
            try {
                repo.toggleLike(reel)
                // Optional: you can refresh the grid item counts later via listener/pull
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------- Comments bottom sheet ----------------
    private val _commentsForReelId = MutableStateFlow<String?>(null)
    val commentsForReelId: StateFlow<String?> = _commentsForReelId

    private val _comments = MutableStateFlow<List<ReelComment>>(emptyList())
    val comments: StateFlow<List<ReelComment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    private val _commentsCanLoadMore = MutableStateFlow(true)
    val commentsCanLoadMore: StateFlow<Boolean> = _commentsCanLoadMore

    private var commentsCursor: Timestamp? = null

    fun openComments(reelId: String) {
        // Open sheet + load first page
        _commentsForReelId.value = reelId
        _comments.value = emptyList()
        _commentsCanLoadMore.value = true
        commentsCursor = null
        loadMoreComments()
    }

    fun closeComments() {
        _commentsForReelId.value = null
        _comments.value = emptyList()
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

                val merged = (_comments.value + page).distinctBy { it.id }
                _comments.value = merged

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
        val t = text.trim()
        if (t.isBlank()) return

        viewModelScope.launch {
            try {
                repo.addComment(reelId, t)
                // Refresh from top so the new comment appears (serverTimestamp)
                if (_commentsForReelId.value == reelId) {
                    _comments.value = emptyList()
                    _commentsCanLoadMore.value = true
                    commentsCursor = null
                    loadMoreComments()
                }
            } catch (_: Throwable) {
            }
        }
    }
}
