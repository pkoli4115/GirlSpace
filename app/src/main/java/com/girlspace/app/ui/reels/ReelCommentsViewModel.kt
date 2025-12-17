package com.girlspace.app.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.ReelComment
import com.girlspace.app.data.reels.ReelsRepository
import com.google.firebase.Timestamp
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

    private var cursor: Timestamp? = null

    fun load(reelId: String) {
        viewModelScope.launch {
            val (items, next) = repo.loadCommentsPage(reelId, 30, cursor)
            _comments.value = _comments.value + items
            cursor = next
        }
    }

    fun addComment(reelId: String, text: String) {
        viewModelScope.launch {
            repo.addComment(reelId, text)
            cursor = null
            _comments.value = emptyList()
            load(reelId)
        }
    }
}
