package com.girlspace.app.ui.reels
import com.girlspace.app.ui.reels.ReelCommentsSheet
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.girlspace.app.data.reels.Reel
import com.girlspace.app.ui.common.glowPulse
import com.girlspace.app.ui.video.SharedPlayerView
import com.girlspace.app.ui.video.VideoPlaybackViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

    val commentsForReelId by vm.commentsForReelId.collectAsState()
    val comments by vm.comments.collectAsState()
    val commentsLoading by vm.commentsLoading.collectAsState()
    val commentsCanLoadMore by vm.commentsCanLoadMore.collectAsState()

    val activeVideoId by videoVm.activePostId.collectAsState()


    LaunchedEffect(Unit) { vm.loadInitial() }

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

    // ▶️ Autoplay logic
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

                    // LazyVerticalGrid provides IntOffset + IntSize
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
                    videoVm.requestPlay(bestId, bestUrl, autoplay = true)
                } else {
                    videoVm.pauseActive()
                }
            }
    }

    Box(Modifier.fillMaxSize()) {

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
                            onOpen = {
                                navController.navigate("reelsViewer/${reel.id}")
                            }
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

        commentsForReelId?.let { reelId ->
            ReelCommentsSheet(
                title = "Comments",
                comments = comments,
                isLoading = commentsLoading,
                onLoadMore = { if (commentsCanLoadMore) vm.loadMoreComments() },
                onDismiss = { vm.closeComments() },
                onSend = { text -> vm.addComment(reelId, text) }
            )
        }
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

        // 🔇 mute
        IconButton(
            onClick = { videoVm.toggleMute() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeOff,
                contentDescription = "Mute",
                tint = Color.White
            )
        }

        // ❤️ Likes / 👁️ Views / 💬 Comments
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (likes > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = Color.White
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onComments)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = "Comments",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
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
