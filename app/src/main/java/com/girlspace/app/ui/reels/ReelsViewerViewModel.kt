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
class ReelsViewerViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Comments state
    private val _comments = MutableStateFlow<List<ReelComment>>(emptyList())
    val comments: StateFlow<List<ReelComment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    var showCommentsForReelId: String? = null
        private set

    private var commentsCursor: ReelsRepository.CommentsCursor? = null
    private var commentsEnd = false

    fun loadForViewer(startReelId: String) {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Simple + safe for v1: load newest N.
                // Pager starts at the requested reel id.
                _reels.value = repo.loadTopReels(limit = 60)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(reel: Reel) {
        viewModelScope.launch { repo.toggleLike(reel) }
    }

    fun logShare(reelId: String) {
        viewModelScope.launch { repo.logShare(reelId) }
    }

    fun openComments(reelId: String) {
        showCommentsForReelId = reelId
        _comments.value = emptyList()
        commentsCursor = null
        commentsEnd = false
        loadMoreComments()
    }

    fun closeComments() {
        showCommentsForReelId = null
    }

    fun loadMoreComments() {
        val reelId = showCommentsForReelId ?: return
        if (_commentsLoading.value || commentsEnd) return

        _commentsLoading.value = true
        viewModelScope.launch {
            try {
                val (list, next) = repo.loadCommentsPage(
                    reelId = reelId,
                    pageSize = 25,
                    cursor = commentsCursor
                )
                _comments.value = (_comments.value + list).distinctBy { it.id }
                commentsCursor = next
                if (list.isEmpty()) commentsEnd = true
            } finally {
                _commentsLoading.value = false
            }
        }
    }

    fun addComment(reelId: String, text: String) {
        viewModelScope.launch {
            repo.addComment(reelId, text)
            openComments(reelId)
        }
    }
}
