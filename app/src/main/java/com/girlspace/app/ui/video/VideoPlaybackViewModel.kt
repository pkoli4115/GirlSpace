package com.girlspace.app.ui.video
import androidx.media3.common.BuildConfig
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@UnstableApi
@HiltViewModel
class VideoPlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : androidx.lifecycle.ViewModel() {

    private val TAG = "VideoPlaybackVM"
    private var activeUrl: String? = null
    private var lastRequestKey: String? = null
    private var lastRequestAtMs: Long = 0L
    private val DUPLICATE_WINDOW_MS = 700L

    // Keep cancel handles for neighbor prefetch
    private var nextPrefetch: VideoCache.PrefetchHandle? = null
    private var prevPrefetch: VideoCache.PrefetchHandle? = null
    private var neighborPrefetchJob: Job? = null

    // Neighbor urls for byte prefetch
    private var nextUrl: String? = null
    private var prevUrl: String? = null

    // 🔑 Track currently loaded URL (critical to avoid re-prepare storms)
    private var currentUrl: String? = null

    // Build ExoPlayer WITH cache-backed MediaSourceFactory
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(VideoCache.cacheDataSourceFactory(context))
        )
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            volume = 0f
        }

    private val _activePostId = MutableStateFlow<String?>(null)
    val activePostId: StateFlow<String?> = _activePostId

    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted

    private val _hasFirstFrame = MutableStateFlow(false)
    val hasFirstFrame: StateFlow<Boolean> = _hasFirstFrame
    private val _firstFramePostId = MutableStateFlow<String?>(null)
    val firstFramePostId: StateFlow<String?> = _firstFramePostId

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    // Playback position cache per post
    private val positionCache = mutableMapOf<String, Long>()

    init {
        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {
                Log.d("REELS_DEBUG", "state=$state buffering=${state == Player.STATE_BUFFERING}")
                _isBuffering.value = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    _hasFirstFrame.value = false
                }
            }

            override fun onRenderedFirstFrame() {
                Log.d("REELS_DEBUG", "FIRST FRAME RENDERED")
                _hasFirstFrame.value = true
                _firstFramePostId.value = _activePostId.value
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _isBuffering.value = false
                _hasFirstFrame.value = false

                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    val code = cause.responseCode
                    if (BuildConfig.DEBUG) {
                        VideoDebugStats.onHttpError(
                            player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(),
                            code
                        )
                    }
                    Log.w(TAG, "Player HTTP error code=$code msg=${cause.message}")
                } else {
                    Log.w(
                        TAG,
                        "Player error: ${error.errorCodeName} cause=${cause?.javaClass?.simpleName}:${cause?.message}"
                    )
                }
            }
        })
    }

    /**
     * Called from ReelsViewerScreen for ACTIVE page.
     * Byte-level prefetch only (no playlist tricks).
     */
    fun setNeighbors(next: String?, prev: String?) {
        nextUrl = next
        prevUrl = prev

        neighborPrefetchJob?.cancel()
        neighborPrefetchJob = viewModelScope.launch {
            delay(120)
            prefetchNeighbors()
        }
    }

    private fun prefetchNeighbors() {
        nextPrefetch?.cancel?.invoke()
        prevPrefetch?.cancel?.invoke()
        nextPrefetch = null
        prevPrefetch = null

        val n = nextUrl?.takeIf { it.isNotBlank() }
        val p = prevUrl?.takeIf { it.isNotBlank() }

        if (BuildConfig.DEBUG) {
            if (!n.isNullOrBlank() && VideoDebugStats.isKnownBadUrl(n)) {
                Log.d(TAG, "Skip prefetch (known bad) next=$n")
            }
            if (!p.isNullOrBlank() && VideoDebugStats.isKnownBadUrl(p)) {
                Log.d(TAG, "Skip prefetch (known bad) prev=$p")
            }
        }

        if (!n.isNullOrBlank() && !(BuildConfig.DEBUG && VideoDebugStats.isKnownBadUrl(n))) {
            nextPrefetch = VideoCache.prefetch(
                context = context,
                url = n,
                onHttpError = { code, _ ->
                    if (BuildConfig.DEBUG) VideoDebugStats.onHttpError(n, code)
                }
            )
        }
        if (!p.isNullOrBlank() && !(BuildConfig.DEBUG && VideoDebugStats.isKnownBadUrl(p))) {
            prevPrefetch = VideoCache.prefetch(
                context = context,
                url = p,
                onHttpError = { code, _ ->
                    if (BuildConfig.DEBUG) VideoDebugStats.onHttpError(p, code)
                }
            )
        }
    }

    fun requestPlay(postId: String, url: String, autoplay: Boolean) {
        Log.d(
            "REELS_DEBUG",
            "requestPlay postId=$postId autoplay=$autoplay url=${url.take(40)}"
        )

        if (url.isBlank()) return

        val now = android.os.SystemClock.elapsedRealtime()
        val key = "$postId|$url|$autoplay"

        // ✅ Hard de-dupe (same request spam)
        if (lastRequestKey == key && (now - lastRequestAtMs) < DUPLICATE_WINDOW_MS) {
            Log.d(
                "REELS_DEBUG",
                "requestPlay IGNORED duplicate within ${DUPLICATE_WINDOW_MS}ms postId=$postId"
            )
            return
        }
        lastRequestKey = key
        lastRequestAtMs = now

        // ✅ SAME reel + SAME url already prepared → just resume/pause
        if (
            _activePostId.value == postId &&
            currentUrl == url &&
            player.currentMediaItem != null
        ) {
            if (autoplay && !player.isPlaying) {
                player.play()
            } else if (!autoplay && player.isPlaying) {
                player.pause()
            }
            return
        }

        // ❌ Known bad URL guard (debug only)
        if (BuildConfig.DEBUG && VideoDebugStats.isKnownBadUrl(url)) {
            Log.w(TAG, "requestPlay blocked (known bad url) postId=$postId")
            _isBuffering.value = false
            _hasFirstFrame.value = false
            player.pause()
            return
        }

        // ✅ Save previous reel position
        _activePostId.value?.let { prev ->
            positionCache[prev] = player.currentPosition
        }

        // ✅ Reset UI state for NEW reel
        _hasFirstFrame.value = false
        _firstFramePostId.value = null
        _isBuffering.value = true

        // ✅ Mark new active reel
        _activePostId.value = postId
        currentUrl = url

        if (BuildConfig.DEBUG) {
            VideoDebugStats.onRequestPlayCalled()
        }

        // ✅ REAL playback setup (this was being skipped earlier)
        player.setMediaItem(MediaItem.fromUri(url))
        if (BuildConfig.DEBUG) {
            VideoDebugStats.onPrepareCalled()
        }
        player.prepare()

        // ✅ Restore last known position if any
        positionCache[postId]?.let { pos ->
            if (pos > 0) {
                // If we saved position near the end, restart from 0 so it doesn't instantly END and auto-advance.
                val dur = player.duration
                val nearEnd = dur > 0 && (dur - pos) < 1500L
                player.seekTo(if (nearEnd) 0L else pos)
            }
        }


        player.playWhenReady = autoplay

        // ✅ Start neighbor prefetch (non-blocking)
        prefetchNeighbors()
    }

    fun onPageSelectedPending() {
        // Hide old frame immediately (important when debounce is used)
        _hasFirstFrame.value = false
        _firstFramePostId.value = null
        _isBuffering.value = true

        // Stop the previous reel from continuing while we debounce
        player.pause()
    }

    fun pauseActive() {
        _activePostId.value?.let { positionCache[it] = player.currentPosition }
        player.pause()
    }

    fun stop(postId: String) {
        if (_activePostId.value == postId) {
            positionCache[postId] = player.currentPosition
            player.pause()
            _activePostId.value = null
            _hasFirstFrame.value = false
            _isBuffering.value = false
            currentUrl = null
        }
    }

    fun toggleMute() {
        _muted.value = !_muted.value
        player.volume = if (_muted.value) 0f else 1f
    }

    fun clearBufferingUiForYoutube(postId: String) {
        _activePostId.value = postId
        _isBuffering.value = false
        _hasFirstFrame.value = true
        _firstFramePostId.value = postId
    }
    override fun onCleared() {
        try {
            nextPrefetch?.cancel?.invoke()
            prevPrefetch?.cancel?.invoke()
            neighborPrefetchJob?.cancel()
        } catch (_: Throwable) {}

        try { player.release() } catch (_: Throwable) {}
        super.onCleared()
    }
}
