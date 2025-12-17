package com.girlspace.app.ui.video

import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

@HiltViewModel
class VideoPlaybackViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = false
        volume = 0f
    }

    private val _activePostId = MutableStateFlow<String?>(null)
    val activePostId: StateFlow<String?> = _activePostId

    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted

    // Playback position cache per post
    private val positionCache = mutableMapOf<String, Long>()

    fun requestPlay(postId: String, url: String, autoplay: Boolean) {
        if (_activePostId.value == postId) {
            if (autoplay) player.play()
            return
        }

        _activePostId.value?.let { prev ->
            positionCache[prev] = player.currentPosition
        }

        _activePostId.value = postId

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()

        positionCache[postId]?.let { pos ->
            player.seekTo(pos)
        }

        player.playWhenReady = autoplay
    }

    fun pauseActive() {
        _activePostId.value?.let {
            positionCache[it] = player.currentPosition
        }
        player.pause()
    }

    fun stop(postId: String) {
        if (_activePostId.value == postId) {
            positionCache[postId] = player.currentPosition
            player.pause()
            _activePostId.value = null
        }
    }

    fun toggleMute() {
        _muted.value = !_muted.value
        player.volume = if (_muted.value) 0f else 1f
    }

    override fun onCleared() {
        try {
            player.release()
        } catch (_: Throwable) { }
        super.onCleared()
    }
}
