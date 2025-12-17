package com.girlspace.app.ui.reels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.data.reels.ReelComment
import com.girlspace.app.data.reels.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.Timestamp

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cursorCreatedAt: Timestamp? = null
    private var cursorId: String? = null
    private var endReached = false

    fun loadInitial() {
        if (_reels.value.isNotEmpty()) return
        loadNext()
    }

    fun loadNext() {
        if (_isLoading.value || endReached) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val page = repo.loadPage(
                    pageSize = 8,
                    cursorCreatedAt = cursorCreatedAt,
                    cursorId = cursorId
                )
                val merged = (_reels.value + page.items).distinctBy { it.id }
                _reels.value = merged

                cursorCreatedAt = page.nextCursorCreatedAt
                cursorId = page.nextCursorId
                if (page.items.isEmpty()) endReached = true
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

    // ---- comments paging for currently opened reel ----
    private val _comments = MutableStateFlow<List<ReelComment>>(emptyList())
    val comments: StateFlow<List<ReelComment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    private var commentsCursor: Timestamp? = null
    private var commentsEnd = false
    private var currentCommentsReelId: String? = null

    fun openComments(reelId: String) {
        currentCommentsReelId = reelId
        _comments.value = emptyList()
        commentsCursor = null
        commentsEnd = false
        loadMoreComments()
    }

    fun loadMoreComments() {
        val reelId = currentCommentsReelId ?: return
        if (_commentsLoading.value || commentsEnd) return

        _commentsLoading.value = true
        viewModelScope.launch {
            try {
                val (list, next) = repo.loadCommentsPage(reelId, pageSize = 20, cursor = commentsCursor)
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
            // refresh first page cheaply by reopening comments
            openComments(reelId)
        }
    }

    // ---- upload ----
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    sealed class UploadState {
        data object Idle : UploadState()
        data object Uploading : UploadState()
        data class Done(val reelId: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }

    fun uploadReel(context: Context, videoUri: Uri, caption: String, tags: List<String>) {
        _uploadState.value = UploadState.Uploading
        viewModelScope.launch {
            try {
                val id = repo.uploadReel(context, videoUri, caption, tags)
                _uploadState.value = UploadState.Done(id)
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }
}
