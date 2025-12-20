@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.girlspace.app.ui.reels
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import java.util.Date
import android.os.Environment
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.ui.common.glowPulse
import com.girlspace.app.ui.reels.CreateReelEntrySheet
import com.girlspace.app.ui.video.SharedPlayerView
import com.girlspace.app.ui.video.VideoPlaybackViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
private enum class PendingCamAction { NONE, VIDEO, PHOTO }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelsScreen(
    navController: NavHostController
) {
    val vm: ReelsViewModel = hiltViewModel()
    val videoVm: VideoPlaybackViewModel = hiltViewModel()

    val reels by vm.reels.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val canLoadMore by vm.canLoadMore.collectAsState()
    val activeVideoId by videoVm.activePostId.collectAsState()

    val commentsForReelId by vm.commentsForReelId.collectAsState()
    val comments by vm.comments.collectAsState()
    val commentsLoading by vm.commentsLoading.collectAsState()
    val commentsCanLoadMore by vm.commentsCanLoadMore.collectAsState()
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(Unit) {
        vm.loadInitial()
    }
    val context = LocalContext.current

    // What the user intended when we ask for permission
    var pendingCamAction by remember { mutableStateOf(PendingCamAction.NONE) }

// Launch system camera intents
    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val uri = result.data?.data
        if (uri == null) {
            Toast.makeText(context, "Video captured, but camera app returned no URI.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        val encoded = Uri.encode(uri.toString())
        navController.navigate("reelCreate/$encoded") { launchSingleTop = true }
    }



    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val uri = pendingPhotoUri
        if (uri == null) {
            Toast.makeText(context, "Photo captured, but file URI missing.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        // ✅ For now: we captured the photo successfully.
        // Upload flow for photos is not wired yet (since reels upload expects video).
        Toast.makeText(context, "Photo captured successfully.", Toast.LENGTH_SHORT).show()
    }

// Request CAMERA permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_LONG).show()
            pendingCamAction = PendingCamAction.NONE
            return@rememberLauncherForActivityResult
        }

        when (pendingCamAction) {
            PendingCamAction.VIDEO -> {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_DURATION_LIMIT, 180)
                    putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                }
                cameraVideoLauncher.launch(intent)
            }
            PendingCamAction.PHOTO -> {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraPhotoLauncher.launch(intent)
            }
            PendingCamAction.NONE -> Unit
        }
        pendingCamAction = PendingCamAction.NONE
    }

    // Helper function: checks permission then launches
    fun ensureCameraPermissionThen(action: PendingCamAction) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            // Launch directly if already allowed
            when (action) {
                PendingCamAction.VIDEO -> {
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_DURATION_LIMIT, 180)
                        putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                    }
                    cameraVideoLauncher.launch(intent)
                }
                PendingCamAction.PHOTO -> {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val imageFile = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "IMG_$timeStamp.jpg"
                    )

                    val uri = FileProvider.getUriForFile(
                        context,
                        "com.girlspace.app.fileprovider",
                        imageFile
                    )
                    pendingPhotoUri = uri

                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    cameraPhotoLauncher.launch(intent)
                }

                PendingCamAction.NONE -> Unit
            }
        } else {
            pendingCamAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ✅ Single refresh bus collector (you had two; that can double-refresh)
    LaunchedEffect(Unit) {
        ReelsRefreshBus.events.collect {
            vm.refresh()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val gridState = rememberLazyGridState()

    // 🔁 Paging
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .map { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                lastVisible to total
            }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 6) {
                    vm.loadNext()
                }
            }
    }

    // ▶️ Autoplay logic (do not touch playback viewmodel / player; kept as-is)
    LaunchedEffect(gridState, reels) {
        snapshotFlow { gridState.layoutInfo }
            .collect { info ->
                val viewportStartY = info.viewportStartOffset
                val viewportEndY = info.viewportEndOffset

                var bestId: String? = null
                var bestUrl: String? = null
                var bestFraction = 0f

                info.visibleItemsInfo.forEach { item ->
                    val reel = reels.getOrNull(item.index) ?: return@forEach

                    val itemTopY = item.offset.y
                    val itemBottomY = item.offset.y + item.size.height

                    val visibleTopY = maxOf(itemTopY, viewportStartY)
                    val visibleBottomY = minOf(itemBottomY, viewportEndY)
                    val visiblePx = (visibleBottomY - visibleTopY).coerceAtLeast(0)

                    val height = item.size.height.coerceAtLeast(1)
                    val fraction = visiblePx.toFloat() / height.toFloat()

                    if (fraction > bestFraction) {
                        bestFraction = fraction
                        bestId = reel.id
                        bestUrl = reel.videoUrl
                    }
                }

                if (bestFraction >= 0.6f && bestId != null && bestUrl != null) {
                    videoVm.requestPlay(bestId!!, bestUrl!!, autoplay = true)
                } else {
                    videoVm.pauseActive()
                }
            }
    }

    var showCreateSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize()) {
            ReelsCreateTopBar(
                onCamera = { showCreateSheet = true }, // ✅ open sheet to choose photo/video
                onGallery = { navController.navigate("reelGalleryPick") },
                onPlus = { showCreateSheet = true }
            )




            when {
                reels.isEmpty() && isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                reels.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No reels yet")
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reels, key = { it.id }) { reel ->
                            ReelGridTile(
                                reel = reel,
                                isActive = activeVideoId == reel.id,
                                videoVm = videoVm,
                                onLike = { vm.toggleLike(reel) },
                                onComments = { vm.openComments(reel.id) },
                                onOpen = { navController.navigate("reelsViewer/${reel.id}") }
                            )
                        }

                        if (isLoading && canLoadMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

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

        if (showCreateSheet) {
            CreateReelEntrySheet(
                onDismiss = { showCreateSheet = false },

                // 🎥 System video camera (with permission)
                onRecordVideo = {
                    showCreateSheet = false
                    ensureCameraPermissionThen(PendingCamAction.VIDEO)
                },

                // 📸 System photo camera (with permission)
                onTakePhoto = {
                    showCreateSheet = false
                    ensureCameraPermissionThen(PendingCamAction.PHOTO)
                },

                // 🖼 Gallery (unchanged)
                onPickFromGallery = {
                    showCreateSheet = false
                    navController.navigate("reelGalleryPick")
                }
            )
        }
    }
}

@Composable
private fun ReelsCreateTopBar(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPlus: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Create",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp)
            )

            Spacer(Modifier.weight(1f))

            GlowActionIcon(
                icon = Icons.Filled.CameraAlt,
                label = "Camera",
                glowColor = Color(0xFF00E5FF),
                onClick = onCamera
            )

            GlowActionIcon(
                icon = Icons.Filled.PhotoLibrary,
                label = "Gallery",
                glowColor = Color(0xFF7C4DFF),
                onClick = onGallery
            )

            GlowActionIcon(
                icon = Icons.Filled.Add,
                label = "More",
                glowColor = Color(0xFFFFD600),
                onClick = onPlus
            )
        }
    }
}

@Composable
private fun GlowActionIcon(
    icon: ImageVector,
    label: String,
    glowColor: Color,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "glow")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.08f))
            .glowPulse(color = glowColor, enabled = true)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = glowColor,
            modifier = Modifier
                .scale(pulse)
                .alpha(alpha)
                .size(22.dp)
        )
    }
}

@Composable
private fun ReelGridTile(
    reel: Reel,
    isActive: Boolean,
    videoVm: VideoPlaybackViewModel,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpen: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val likes = reel.likeCount()
    val views = reel.viewCount()

    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .glowPulse(
                color = MaterialTheme.colorScheme.primary,
                enabled = isActive
            )
            .clickable(onClick = onOpen)
    ) {
        if (isActive) {
            SharedPlayerView(
                player = videoVm.player,
                modifier = Modifier.fillMaxSize(),
                showController = false
            )
        } else {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = "Reel thumbnail",
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = { videoVm.toggleMute() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeOff,
                contentDescription = "Mute",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLike,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (likes > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = likes.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Views",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = views.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onComments,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Comments",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (reel.caption.isNotBlank()) {
            Text(
                text = reel.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            )
        }
    }
}
