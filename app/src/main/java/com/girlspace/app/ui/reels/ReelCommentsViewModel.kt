package com.girlspace.app.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.ReelComment
import com.girlspace.app.data.reels.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelCommentsViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    private val _comments = MutableStateFlow<List<ReelComment>>(emptyList())
    val comments: StateFlow<List<ReelComment>> = _comments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore

    private var currentReelId: String? = null

    // ✅ FIX: use the new composite cursor type
    private var cursor: ReelsRepository.CommentsCursor? = null

    /**
     * Call when opening comments for a reel (fresh).
     */
    fun open(reelId: String) {
        if (currentReelId != reelId) {
            currentReelId = reelId
            reset()
        }
        loadMore(reelId)
    }

    fun reset() {
        _comments.value = emptyList()
        _isLoading.value = false
        _canLoadMore.value = true
        cursor = null
    }

    fun loadMore(reelId: String) {
        if (_isLoading.value || !_canLoadMore.value) return

        // If caller passes a different reelId, reset automatically
        if (currentReelId != reelId) {
            currentReelId = reelId
            reset()
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val (items, next) = repo.loadCommentsPage(
                    reelId = reelId,
                    pageSize = 30,
                    cursor = cursor
                )

                // avoid duplicates if paging overlaps (rare but safe)
                val merged = (_comments.value + items).distinctBy { it.id }
                _comments.value = merged

                cursor = next

                if (items.isEmpty() || next == null) {
                    _canLoadMore.value = false
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addComment(reelId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            runCatching {
                repo.addComment(reelId, trimmed)
            }.onSuccess { newComment ->
                // ✅ insert immediately at top (createdAt desc)
                _comments.value = listOf(newComment) + _comments.value
            }
        }
    }
}
