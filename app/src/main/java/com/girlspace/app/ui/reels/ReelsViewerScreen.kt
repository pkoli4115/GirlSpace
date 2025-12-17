@file:OptIn(ExperimentalMaterial3Api::class)

package com.girlspace.app.ui.reels

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.ui.video.SharedPlayerView
import com.girlspace.app.ui.video.VideoPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ReelsViewerScreen(
    startReelId: String,
    onBack: () -> Unit
) {
    val vm: ReelsViewModel = hiltViewModel()
    val videoVm: VideoPlaybackViewModel = hiltViewModel()

    val reels by vm.reels.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    val comments by vm.comments.collectAsState()
    val commentsLoading by vm.commentsLoading.collectAsState()
    val commentsCanLoadMore by vm.commentsCanLoadMore.collectAsState()
    val commentsForReelId by vm.commentsForReelId.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    LaunchedEffect(startReelId) {
        vm.loadInitial()
        vm.ensureReelInList(startReelId)
    }

    BackHandler {
        videoVm.pauseActive()
        onBack()
    }

    LaunchedEffect(Unit) {
        view.isFocusableInTouchMode = true
        view.requestFocus()
    }

    var fillScreen by remember { mutableStateOf(true) }
    val resizeMode = if (fillScreen) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (videoVm.muted.value) videoVm.toggleMute()
                        false
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        scope.launch {
                            delay(60)
                            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (vol <= 0 && !videoVm.muted.value) {
                                videoVm.toggleMute()
                            }
                        }
                        false
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // Loading/empty safety
        if (reels.isEmpty() && isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            TopBackBar(
                onBack = {
                    videoVm.pauseActive()
                    onBack()
                },
                fillScreen = fillScreen,
                onToggleFitFill = { fillScreen = !fillScreen }
            )
            return@Box
        }

        if (reels.isEmpty()) {
            Text("No reels yet", color = Color.White, modifier = Modifier.align(Alignment.Center))
            TopBackBar(
                onBack = {
                    videoVm.pauseActive()
                    onBack()
                },
                fillScreen = fillScreen,
                onToggleFitFill = { fillScreen = !fillScreen }
            )
            return@Box
        }

        val startIndex = remember(reels, startReelId) {
            reels.indexOfFirst { it.id == startReelId }.coerceAtLeast(0)
        }

        val pagerState = rememberPagerState(
            initialPage = startIndex,
            pageCount = { reels.size }
        )

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val reel = reels[page]
            val provider = (reel.source["provider"] as? String).orEmpty()
            val isPageActive = pagerState.currentPage == page

            LaunchedEffect(isPageActive, reel.id, provider, reel.videoUrl) {
                if (!isPageActive) return@LaunchedEffect
                vm.onReelViewed(reel.id)

                if (provider.equals("youtube_url", ignoreCase = true)) {
                    videoVm.pauseActive()
                } else {
                    val url = reel.videoUrl
                    if (url.isNotBlank()) {
                        videoVm.requestPlay(reel.id, url, autoplay = true)
                    } else {
                        videoVm.pauseActive()
                    }
                }
            }

            SharedPlayerView(
                player = videoVm.player,
                modifier = Modifier.fillMaxSize(),
                showController = false,
                resizeMode = resizeMode
            )

            if (provider.equals("youtube_url", ignoreCase = true)) {
                YoutubeLinkOutOverlay(reel = reel)
            }
        }

        val currentReel = reels.getOrNull(pagerState.currentPage)

        // Top: back + fit/fill
        TopBackBar(
            onBack = {
                videoVm.pauseActive()
                onBack()
            },
            fillScreen = fillScreen,
            onToggleFitFill = { fillScreen = !fillScreen }
        )

        // Right actions: Like / Comment / Share (bigger targets + counts)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val r = currentReel
            val reelId = r?.id.orEmpty()

            // LIKE
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { if (r != null) vm.toggleLike(r) },
                    modifier = Modifier.size(56.dp)
                ) {
                    val liked = r?.let { vm.isLiked(it.id) } == true
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = (r?.likeCount() ?: 0L).toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // COMMENT (bigger tap)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { if (reelId.isNotBlank()) vm.openComments(reelId) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Comments",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // SHARE
            IconButton(
                onClick = {
                    val rr = currentReel ?: return@IconButton
                    val deepLink = "togetherly://reels/${rr.id}"
                    val label = if (rr.durationSec <= 60) "Reel" else "Video"
                    val text = "Check this $label on Togetherly\n\n$deepLink"

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                    scope.launch { vm.logShare(rr.id) }
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Mute chip
        val isMuted by videoVm.muted.collectAsState()
        val chipShape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
                .background(Color.Black.copy(alpha = 0.55f), shape = chipShape)
                .border(1.dp, Color.White.copy(alpha = 0.65f), chipShape)
                .clickable { videoVm.toggleMute() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "Toggle audio",
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isMuted) "Muted" else "Sound",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Bottom playback controls (kept)
        ReelsPlaybackControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            videoVm = videoVm
        )
    }

    // Comments sheet
    val rid = commentsForReelId
    if (!rid.isNullOrBlank()) {
        ReelCommentsSheet(
            title = "Comments",
            comments = comments,
            isLoading = commentsLoading,
            onLoadMore = { if (commentsCanLoadMore) vm.loadMoreComments() },
            onDismiss = { vm.closeComments() },
            onSend = { text -> vm.addComment(rid, text) }
        )
    }
}

@Composable
private fun TopBackBar(
    onBack: () -> Unit,
    fillScreen: Boolean,
    onToggleFitFill: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onToggleFitFill) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = if (fillScreen) "Fit" else "Fill",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ReelsPlaybackControls(
    modifier: Modifier,
    videoVm: VideoPlaybackViewModel
) {
    val player = videoVm.player

    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            durationMs = max(0L, player.duration)
            positionMs = max(0L, player.currentPosition)
            isPlaying = player.isPlaying
            delay(250)
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val safeDuration = max(1L, durationMs)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                color = Color.White
            )
        }

        Slider(
            value = if (dragging) dragValue else progress,
            onValueChange = { v ->
                dragging = true
                dragValue = v
            },
            onValueChangeFinished = {
                val seekTo = (safeDuration.toFloat() * dragValue).toLong()
                player.seekTo(seekTo)
                dragging = false
            }
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${sec.toString().padStart(2, '0')}"
}

@Composable
private fun YoutubeLinkOutOverlay(reel: Reel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        val url = (reel.source["youtubeUrl"] as? String).orEmpty()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YouTube link", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "This is link-out only (no in-app playback).",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (url.isBlank()) "URL missing" else url,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
