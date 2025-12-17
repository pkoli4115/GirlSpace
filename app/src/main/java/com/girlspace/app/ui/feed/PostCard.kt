package com.girlspace.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.girlspace.app.data.feed.Post
import com.girlspace.app.ui.feed.InlineVideoPlayer
import com.girlspace.app.ui.video.VideoPlaybackViewModel

enum class VideoSize { SMALL, MEDIUM, LARGE }

@Composable
fun PostCard(
    post: Post,
    videoSize: VideoSize,
    videoVm: VideoPlaybackViewModel,
    isActive: Boolean,
    onOpenReels: () -> Unit,
    feedVibe: FeedVibe
) {
    val isMuted by videoVm.muted.collectAsState()

    val height = when (videoSize) {
        VideoSize.SMALL -> 220.dp
        VideoSize.MEDIUM -> 300.dp
        VideoSize.LARGE -> 420.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {

        if (post.videoUrls.isNotEmpty()) {
            val url = post.videoUrls.first()
            val thumb = post.imageUrls.firstOrNull()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clickable { onOpenReels() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Black
            ) {
                Box {

                    if (!isActive) {
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.4f)
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(18.dp).size(44.dp)
                                )
                            }
                        }
                    } else {
                        InlineVideoPlayer(
                            videoVm = videoVm,
                            postId = post.postId,
                            url = url,
                            modifier = Modifier.fillMaxSize(),
                            showController = false
                        )
                    }

                    IconButton(
                        onClick = { videoVm.toggleMute() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted)
                                Icons.Filled.VolumeOff
                            else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
