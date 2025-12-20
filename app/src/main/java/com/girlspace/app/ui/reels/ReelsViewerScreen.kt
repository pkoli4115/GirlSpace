@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.girlspace.app.ui.reels
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.ui.video.SharedPlayerView
import com.girlspace.app.ui.video.VideoPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.TextFieldValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

@OptIn(UnstableApi::class)
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

    val isBuffering by videoVm.isBuffering.collectAsState()
    val firstFramePostId by videoVm.firstFramePostId.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteReel by remember { mutableStateOf<Reel?>(null) }
    // --- Report state ---
    var showReportReel by remember { mutableStateOf(false) }

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

    val isMuted by videoVm.muted.collectAsState()
    val latestMuted by rememberUpdatedState(isMuted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (latestMuted) videoVm.toggleMute()
                        false
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        scope.launch {
                            delay(60)
                            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (vol <= 0 && !latestMuted) {
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
            // Top bar
            TopBackBar(
                onBack = {
                    videoVm.pauseActive()
                    onBack()
                },
                fillScreen = fillScreen,
                onToggleFitFill = { fillScreen = !fillScreen },
                canDelete = false,
                onDelete = { /* no-op */ },
                canReport = false,
                onReport = { /* no-op */ }
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
                onToggleFitFill = { fillScreen = !fillScreen },
                canDelete = false,
                onDelete = { /* no-op */ },
                canReport = false,
                onReport = { /* no-op */ }
            )


            return@Box
        }

        val startIndex = remember(reels, startReelId) {
            reels.indexOfFirst { it.id == startReelId }.coerceAtLeast(0)
        }

        var suppressAutoAdvanceUntilMs by remember { mutableStateOf(0L) }

        val pagerState = rememberPagerState(
            initialPage = startIndex,
            pageCount = { reels.size }
        )

        // ✅ Auto-advance to next reel when native video ends
        val autoAdvanceScope = rememberCoroutineScope()
        DisposableEffect(videoVm.player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        val now = SystemClock.elapsedRealtime()
                        if (now < suppressAutoAdvanceUntilMs) return

                        autoAdvanceScope.launch {
                            val nextPage = pagerState.currentPage + 1
                            if (nextPage < pagerState.pageCount) {
                                pagerState.animateScrollToPage(nextPage)
                            }
                        }
                    }
                }
            }
            videoVm.player.addListener(listener)
            onDispose { videoVm.player.removeListener(listener) }
        }

        // keep latest reels without restarting the effect
        val reelsLatest by rememberUpdatedState(newValue = reels)

        // ✅ Page change: YouTube pauses ExoPlayer, native reels requestPlay
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    val currentList = reelsLatest
                    val reel = currentList.getOrNull(page) ?: return@collectLatest

                    suppressAutoAdvanceUntilMs = SystemClock.elapsedRealtime() + 900L

                    // hide old frame immediately
                    videoVm.onPageSelectedPending()

                    // debounce
                    delay(300)

                    // (safe even for native reels)
                    videoVm.clearBufferingUiForYoutube(reel.id)

                    val provider = (reel.source["provider"] as? String).orEmpty()
                    val isYoutube = provider.equals("youtube_url", ignoreCase = true)

                    vm.onReelViewed(reel.id)

                    if (isYoutube) {
                        // 🔴 IMPORTANT: YouTube reels must NEVER touch ExoPlayer
                        videoVm.pauseActive()
                    } else {
                        val url = reel.videoUrl
                        if (url.isNotBlank()) {
                            videoVm.requestPlay(
                                postId = reel.id,
                                url = url,
                                autoplay = true
                            )
                        } else {
                            videoVm.pauseActive()
                        }

                        // neighbors only useful for native
                        val nextUrl = currentList.getOrNull(page + 1)?.videoUrl
                        val prevUrl = currentList.getOrNull(page - 1)?.videoUrl
                        videoVm.setNeighbors(next = nextUrl, prev = prevUrl)
                    }
                }
        }
// ✅ Current active reel (outer scope, usable for dialogs + actions)
        val currentReel = reels.getOrNull(pagerState.currentPage)
        val currentIsYoutube = (currentReel?.source?.get("provider") as? String)
            .orEmpty()
            .equals("youtube_url", ignoreCase = true)

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val reel = reels[page]
            val provider = (reel.source["provider"] as? String).orEmpty()
            val isPageActive = pagerState.currentPage == page
            val isYoutube = provider.equals("youtube_url", ignoreCase = true)

            val youtubeUrl = (reel.source["youtubeUrl"] as? String).orEmpty()

            // ✅ FIXED: correct fallback order (source -> top-level -> extract from url)
            val youtubeId =
                (reel.source["youtubeVideoId"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: reel.youtubeVideoId
                        ?.takeIf { it.isNotBlank() }
                    ?: extractYoutubeVideoId(youtubeUrl).orEmpty()

                    Box(Modifier.fillMaxSize()) {

                // ✅ Native reels: ExoPlayer surface only on active page
                if (isPageActive && !isYoutube) {
                    SharedPlayerView(
                        player = videoVm.player,
                        modifier = Modifier.fillMaxSize(),
                        showController = false,
                        playerViewFactory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                this.resizeMode = resizeMode
                            }
                        }
                    )
                }

                // ✅ Thumbnail until first frame for NON-YouTube reels
                val shouldShowThumb = isPageActive && !isYoutube && (firstFramePostId != reel.id)
                if (shouldShowThumb) {
                    Image(
                        painter = rememberAsyncImagePainter(reel.thumbnailUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }
                }

                // ✅ YouTube: always show thumb as background (player is WebView)
                if (isPageActive && isYoutube) {
                    Image(
                        painter = rememberAsyncImagePainter(reel.thumbnailUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // ✅ YouTube player overlay (only for active page)
                if (isYoutube && isPageActive) {
                    if (youtubeId.isNotBlank()) {
                        YoutubeIFramePlayer(
                            videoId = youtubeId,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        YoutubeLinkOutOverlay(reel = reel)
                    }
                }

                // ✅ Caption overlay (always on top)
                if (isPageActive) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .fillMaxWidth(0.85f)
                    ) {
                        Text(
                            text = reel.authorName.ifBlank { "User" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (reel.caption.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = reel.caption,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

        // Top bar
                // Top bar (in-page, active reel only)
                TopBackBar(
                    onBack = {
                        videoVm.pauseActive()
                        onBack()
                    },
                    fillScreen = fillScreen,
                    onToggleFitFill = { fillScreen = !fillScreen },
                    canDelete = (currentReel?.authorId == myUid) && !currentIsYoutube,
                    onDelete = {
                        pendingDeleteReel = currentReel
                        showDeleteConfirm = true
                    },
                    canReport = (currentReel != null) && (currentReel.authorId != myUid),
                    onReport = { showReportReel = true }
                )



                // Right actions (use current active reel)
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

                    // COMMENT
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
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Mute chip (still shown for YouTube too, but it only affects ExoPlayer)
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

                // Bottom playback controls only for native reels (don’t confuse users on YouTube)
                if (!currentIsYoutube) {
                    ReelsPlaybackControls(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 18.dp),
                        videoVm = videoVm
                    )
                }
            }
        }

        // Comments sheet
        val rid = commentsForReelId
        if (!rid.isNullOrBlank()) {
            ReelCommentsSheet(
                title = "Comments",
                reelId = rid,
                comments = comments,
                isLoading = commentsLoading,
                onLoadMore = { if (commentsCanLoadMore) vm.loadMoreComments() },
                onDismiss = { vm.closeComments() },
                onSend = { text -> vm.addComment(rid, text) }
            )

        }
        // Report dialog (use currentReel already computed)
        if (showReportReel && currentReel != null) {
            ReportReelDialog(
                reel = currentReel,
                onDismiss = { showReportReel = false },
                onSubmitted = { showReportReel = false }
            )
        }

    }

    // Delete confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                pendingDeleteReel = null
            },
            title = { Text("Delete reel?") },
            text = { Text("This will permanently delete this reel (video + thumbnail).") },
            confirmButton = {
                TextButton(onClick = {
                    val r = pendingDeleteReel
                    showDeleteConfirm = false
                    pendingDeleteReel = null

                    if (r != null) {
                        // Stop playback first
                        videoVm.pauseActive()

                        // Delete (you already added repo + vm delete logic)
                        vm.deleteReel(r) { /* ignore snackbar for now */ }

                        // Exit viewer safely (so pager doesn't point to deleted item)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    pendingDeleteReel = null
                }) { Text("Cancel") }
            }
        )
    }

}

@Composable
private fun TopBackBar(
    onBack: () -> Unit,
    fillScreen: Boolean,
    onToggleFitFill: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    canReport: Boolean,
    onReport: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

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

        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }

        IconButton(onClick = onToggleFitFill) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = if (fillScreen) "Fit" else "Fill",
                tint = Color.White
            )
        }

        // ⋮ menu
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Report Reel") },
                    enabled = canReport,
                    onClick = {
                        menuOpen = false
                        onReport()
                    }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
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
                onClick = { if (player.isPlaying) player.pause() else player.play() }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportReelDialog(
    reel: Reel,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var selected by remember { mutableStateOf(ReportReason.SPAM) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Report Reel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Text("Select a reason:", style = MaterialTheme.typography.bodyMedium)

                // Simple radio list (stable, no fancy UI)
                ReportReason.values().forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !submitting) { selected = r }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selected == r),
                            onClick = { if (!submitting) selected = r }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(r.label)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = !submitting,
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (uid.isNullOrBlank()) {
                        error = "Please sign in to report."
                        return@TextButton
                    }
                    submitting = true
                    error = null

                    val reportId = UUID.randomUUID().toString()
                    val data = hashMapOf(
                        "type" to "REEL",
                        "reelId" to reel.id,
                        "targetId" to reel.id,
                        "reporterUserId" to uid,
                        "reportedUserId" to reel.authorId,
                        "reason" to selected.wire,
                        "note" to note.text.trim().takeIf { it.isNotBlank() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "status" to "OPEN"
                    )

                    // Remove null note to keep doc clean
                    if (data["note"] == null) data.remove("note")

                    firestore.collection("reports")
                        .document(reportId)
                        .set(data)
                        .addOnSuccessListener {
                            // ✅ hide for reporting user only (single write)
                            firestore.collection("users")
                                .document(uid)
                                .collection("hiddenContent")
                                .document(reel.id)
                                .set(
                                    mapOf(
                                        "type" to "REEL",
                                        "targetId" to reel.id,
                                        "reportId" to reportId,
                                        "hiddenAt" to FieldValue.serverTimestamp()
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                                .addOnSuccessListener {
                                    submitting = false
                                    onSubmitted()
                                }
                                .addOnFailureListener { e ->
                                    submitting = false
                                    error = e.message ?: "Report saved, but failed to hide this reel."
                                }
                        }

                        .addOnFailureListener { e ->
                            submitting = false
                            error = e.message ?: "Failed to submit report"
                        }

                }
            ) { Text(if (submitting) "Submitting…" else "Submit") }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun extractYoutubeVideoId(input: String): String? {
    if (input.isBlank()) return null
    return try {
        val uri = android.net.Uri.parse(input)
        val host = (uri.host ?: "").lowercase()
        when {
            host.contains("youtu.be") -> uri.pathSegments.firstOrNull()
            host.contains("youtube.com") -> {
                uri.getQueryParameter("v")
                    ?: run {
                        val seg = uri.pathSegments
                        val i = seg.indexOfFirst { it.equals("shorts", true) }
                        if (i >= 0) seg.getOrNull(i + 1) else null
                    }
            }
            else -> null
        }?.takeIf { it.length in 8..20 }
    } catch (_: Throwable) { null }
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
            Text(
                "YouTube link",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
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