package com.girlspace.app.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.data.reels.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelsStripViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    fun load() {
        if (_reels.value.isNotEmpty()) return
        viewModelScope.launch { _reels.value = repo.loadTopReels(15) }
    }
}
