package com.girlspace.app.ui.feed
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.girlspace.app.ui.video.SharedPlayerView
import com.girlspace.app.ui.video.VideoPlaybackViewModel

/**
 * Inline player host for the SINGLE shared ExoPlayer held by VideoPlaybackViewModel.
 * This composable never creates/releases the player. It only attaches/detaches the PlayerView.
 */
@Composable
fun InlineVideoPlayer(
    videoVm: VideoPlaybackViewModel,
    postId: String,
    url: String,
    modifier: Modifier = Modifier,
    showController: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive by videoVm.activePostId.collectAsState(initial = null)

    val active = isActive == postId

    // If this cell becomes active, ensure VM is pointing to its media.
    LaunchedEffect(active, postId, url) {
        if (active && url.isNotBlank()) {
            // Don't force autoplay here; FeedScreen controls autoplay.
            // If FeedScreen already requestedPlay with autoplay=true, player will already be playing.
            videoVm.requestPlay(postId, url, autoplay = true)
        }
    }

    // Pause when app goes background (only if this cell is active)
    DisposableEffect(lifecycleOwner, active) {
        val obs = LifecycleEventObserver { _, event ->
            if (!active) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> videoVm.pauseActive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    SharedPlayerView(
        player = videoVm.player,
        modifier = modifier,
        showController = showController
    )
}
