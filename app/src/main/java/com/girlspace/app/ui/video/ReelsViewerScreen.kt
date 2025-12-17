@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.girlspace.app.ui.video
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
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
import com.girlspace.app.ui.feed.FeedItem
import com.girlspace.app.ui.feed.FeedViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun ReelsViewerScreen(
    startPostId: String,
    onBack: () -> Unit
) {
    val feedVm: FeedViewModel = hiltViewModel()
    val videoVm: VideoPlaybackViewModel = hiltViewModel()

    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid

    // ⚠️ IMPORTANT: set this to your actual posts collection name.
    // In your Firestore left panel screenshot you have a top-level "posts" collection.
    val POSTS_COL = "posts"

    val feedItems by feedVm.feedItems.collectAsState()
    val reels = remember(feedItems) {
        feedItems
            .filterIsInstance<FeedItem.PostItem>()
            .map { it.post }
            .filter { it.videoUrls.isNotEmpty() }
    }

    val startIndex = remember(reels, startPostId) {
        reels.indexOfFirst { it.postId == startPostId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { reels.size })
    val isMuted by videoVm.muted.collectAsState()
    val activeId by videoVm.activePostId.collectAsState()

    // ✅ Fit/Fill toggle
    var fillScreen by remember { mutableStateOf(true) }
    val resizeMode = if (fillScreen) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM   // Fill (crop)
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT    // Fit (letterbox)
    }

    // Comments UI state
    var commentsForPostId by remember { mutableStateOf<String?>(null) }

    BackHandler {
        videoVm.pauseActive()
        onBack()
    }

    // Ensure this composable can receive key events (volume keys)
    LaunchedEffect(Unit) {
        view.isFocusableInTouchMode = true
        view.requestFocus()
    }

    // Safety: if no reels, just show black + close
    if (reels.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            IconButton(
                onClick = {
                    videoVm.pauseActive()
                    onBack()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "No reels yet",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ✅ Volume keys: let system change volume, but we sync mute/unmute state
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (isMuted) videoVm.toggleMute()
                        false
                    }

                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        scope.launch {
                            delay(60)
                            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (vol <= 0 && !isMuted) {
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

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val post = reels[page]
            val url = post.videoUrls.first()

            // Request play when this page becomes current
            val isPageActive = pagerState.currentPage == page
            LaunchedEffect(isPageActive, post.postId, url) {
                if (isPageActive) {
                    videoVm.requestPlay(post.postId, url, autoplay = true)
                }
            }

            SharedPlayerView(
                player = videoVm.player,
                modifier = Modifier.fillMaxSize(),
                showController = false,
                resizeMode = resizeMode
            )
        }

        // ---------------------------
        // Top controls: Back + Fit/Fill
        // ---------------------------
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 10.dp, start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                videoVm.pauseActive()
                onBack()
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Back", tint = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { fillScreen = !fillScreen }) {
                Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = if (fillScreen) "Fit" else "Fill",
                    tint = Color.White
                )
            }
        }

        // ---------------------------
        // Right actions: Like / Comment / Share
        // ---------------------------
        val currentPost = reels.getOrNull(pagerState.currentPage)

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // LIKE
            IconButton(
                onClick = {
                    val p = currentPost ?: return@IconButton
                    val uid = myUid ?: return@IconButton
                    togglePostLikeAndNotify(
                        firestore = firestore,
                        postsCol = POSTS_COL,
                        postId = p.postId,
                        postOwnerId = p.uid,
                        senderId = uid,
                        senderName = auth.currentUser?.displayName ?: "Someone"
                    )
                }
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = Color.White)
            }

            // COMMENT
            IconButton(
                onClick = {
                    val p = currentPost ?: return@IconButton
                    commentsForPostId = p.postId
                }
            ) {
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Comment", tint = Color.White)
            }

            // SHARE
            IconButton(
                onClick = {
                    val p = currentPost ?: return@IconButton
                    val text = buildString {
                        append("Check this reel on Togetherly")
                        append("\n\n")
                        append("Open: togetherly://reels/${p.postId}")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share reel via"))
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
            }
        }

        // ---------------------------
        // ✅ Visible Mute/Sound overlay (bottom-right, above seekbar)
        // ---------------------------
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

        // ---------------------------
        // Bottom controls: Play/Pause + Seek bar + time
        // ---------------------------
        ReelsPlaybackControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            videoVm = videoVm,
            isActive = activeId != null
        )
    }

    // ---------------------------
    // Comments sheet
    // ---------------------------
    if (commentsForPostId != null) {
        ReelsCommentsSheet(
            postId = commentsForPostId!!,
            onDismiss = { commentsForPostId = null },
            firestore = FirebaseFirestore.getInstance(),
            postsCol = "posts",
            myUid = FirebaseAuth.getInstance().currentUser?.uid,
            myName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Someone"
        )
    }
}

@Composable
private fun ReelsPlaybackControls(
    modifier: Modifier,
    videoVm: VideoPlaybackViewModel,
    isActive: Boolean
) {
    val player = videoVm.player

    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    // poll player state smoothly (simple + reliable)
    LaunchedEffect(isActive) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
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

/**
 * Like/unlike using /posts/{postId}/likes/{uid}
 * + create notification in /users/{ownerId}/notifications
 */
private fun togglePostLikeAndNotify(
    firestore: FirebaseFirestore,
    postsCol: String,
    postId: String,
    postOwnerId: String,
    senderId: String,
    senderName: String
) {
    if (postId.isBlank()) return
    if (postOwnerId.isBlank()) return

    val postRef = firestore.collection(postsCol).document(postId)
    val likeRef = postRef.collection("likes").document(senderId)

    firestore.runTransaction { tx ->
        val likeSnap = tx.get(likeRef)
        if (likeSnap.exists()) {
            tx.delete(likeRef)
        } else {
            tx.set(likeRef, mapOf(
                "userId" to senderId,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            // notify owner (don’t notify yourself)
            if (postOwnerId != senderId) {
                val notifRef = firestore.collection("users")
                    .document(postOwnerId)
                    .collection("notifications")
                    .document()

                tx.set(notifRef, mapOf(
                    "id" to notifRef.id,
                    "title" to senderName,
                    "body" to "liked your reel",
                    "category" to "REEL",
                    "type" to "reel_like",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "read" to false,
                    "senderId" to senderId,
                    "entityId" to postId,
                    "deepLink" to "togetherly://reels/$postId",
                    "importance" to "NORMAL"
                ))
            }
        }
        null
    }
}

/**
 * Simple comments sheet using /posts/{postId}/comments
 * + creates notification on comment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelsCommentsSheet(
    postId: String,
    onDismiss: () -> Unit,
    firestore: FirebaseFirestore,
    postsCol: String,
    myUid: String?,
    myName: String
) {
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var comments by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    val postRef = remember(postId) { firestore.collection(postsCol).document(postId) }

    LaunchedEffect(postId) {
        isLoading = true
        postRef.collection("comments")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(40)
            .get()
            .addOnSuccessListener { snap ->
                comments = snap.documents.map { it.data.orEmpty() }
                isLoading = false
            }
            .addOnFailureListener {
                comments = emptyList()
                isLoading = false
            }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Comments", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                comments.forEach { c ->
                    val name = (c["authorName"] as? String).orEmpty().ifBlank { "User" }
                    val body = (c["text"] as? String).orEmpty()
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(name, style = MaterialTheme.typography.labelMedium)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                    Divider()
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a comment…") },
                    maxLines = 2
                )
                TextButton(
                    onClick = {
                        val uid = myUid ?: return@TextButton
                        val t = text.trim()
                        if (t.isBlank()) return@TextButton

                        val commentId = UUID.randomUUID().toString()
                        val commentRef = postRef.collection("comments").document(commentId)

                        firestore.runTransaction { tx ->
                            // write comment
                            tx.set(commentRef, mapOf(
                                "id" to commentId,
                                "postId" to postId,
                                "authorId" to uid,
                                "authorName" to myName,
                                "text" to t,
                                "createdAt" to FieldValue.serverTimestamp()
                            ))

                            // notify post owner
                            val postSnap = tx.get(postRef)
                            val ownerId = postSnap.getString("userId").orEmpty()
                            if (ownerId.isNotBlank() && ownerId != uid) {
                                val notifRef = firestore.collection("users")
                                    .document(ownerId)
                                    .collection("notifications")
                                    .document()

                                tx.set(notifRef, mapOf(
                                    "id" to notifRef.id,
                                    "title" to myName,
                                    "body" to "commented: ${t.take(80)}",
                                    "category" to "REEL",
                                    "type" to "reel_comment",
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "read" to false,
                                    "senderId" to uid,
                                    "entityId" to postId,
                                    "deepLink" to "togetherly://reels/$postId",
                                    "importance" to "NORMAL"
                                ))
                            }
                            null
                        }.addOnSuccessListener {
                            text = ""
                            // refresh quickly
                            postRef.collection("comments")
                                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                .limit(40)
                                .get()
                                .addOnSuccessListener { snap ->
                                    comments = snap.documents.map { it.data.orEmpty() }
                                }
                        }
                    }
                ) { Text("Post") }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
