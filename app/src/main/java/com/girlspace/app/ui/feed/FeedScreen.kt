package com.girlspace.app.ui.feed
import com.girlspace.app.core.SoftLaunchPrefs
import kotlinx.coroutines.flow.first

import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import com.girlspace.app.ui.reels.ReportReason
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import com.girlspace.app.ui.feed.InlineVideoPlayer
import androidx.compose.material.icons.filled.PlayArrow
import com.girlspace.app.ui.video.VideoPlaybackViewModel
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.girlspace.app.core.plan.PlanLimitsRepository
import com.girlspace.app.data.feed.Comment
import com.girlspace.app.data.feed.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FeedScreen(
    isCreatePostOpen: Boolean,
    onDismissCreatePost: () -> Unit,
    onOpenCreatePost: () -> Unit,
    navController: NavHostController,
) {
    val videoVm: VideoPlaybackViewModel = hiltViewModel()
    val vm: FeedViewModel = hiltViewModel()
    val styleVm: FeedStyleViewModel = hiltViewModel()
    val feedItems by vm.feedItems.collectAsState()
    val isInitialLoading by vm.isInitialLoading.collectAsState()
    val isPaging by vm.isPaging.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val hiddenTargetIds by vm.hiddenTargetIds.collectAsState()

    LaunchedEffect(Unit) {
        Log.e("FEED_RELEASE", "FeedScreen entered (LaunchedEffect)")
    }

    // Track which videos have been “entered” at least once so UI doesn’t revert to thumbnail
    var startedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val enableAutoPlay = true

    val premiumRequired by vm.premiumRequired.collectAsState()
    val maxImages by vm.currentMaxImages.collectAsState()

    val feedVibe by styleVm.currentVibe.collectAsState()
    val quickVibes = styleVm.quickVibes

    // Active video according to feed visibility logic
    var activeVideoPostId by remember { mutableStateOf<String?>(null) }

    val planLimits by PlanLimitsRepository.planLimits.collectAsState()
    val maxImagesAllowed = planLimits.maxImagesPerPost

    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val firestore = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Show the "gradually opening features" message once (first upload attempt)
    LaunchedEffect(isCreatePostOpen) {
        if (!isCreatePostOpen) return@LaunchedEffect
        val seen = SoftLaunchPrefs.seenUploadBannerFlow(context).first()
        if (!seen) {
            snackbarHostState.showSnackbar(
                message =
                    "🌸 Togetherly is opening features gradually to keep the community safe.\n" +
                            "Short videos & limited photos are available now.\n" +
                            "More features unlock as the community grows 💗"
            )
            SoftLaunchPrefs.markUploadBannerSeen(context)
        }
    }


    val isMuted by videoVm.muted.collectAsState()
    val activeId by videoVm.activePostId.collectAsState()

    // ✅ preload native ads on feed entry (safe)
    LaunchedEffect(Unit) { vm.ensureAdsLoaded(context) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Saved posts for this user
    var savedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(currentUser?.uid) {
        var registration: ListenerRegistration? = null
        val uid = currentUser?.uid
        if (uid != null) {
            registration = firestore.collection("users")
                .document(uid)
                .collection("savedPosts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val ids = snapshot?.documents
                        ?.mapNotNull { it.getString("postId") }
                        ?.toSet()
                        ?: emptySet()
                    savedPostIds = ids
                }
        }
        onDispose { registration?.remove() }
    }

    // Following set for this user – from users/{uid}.following array
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(currentUser?.uid) {
        var registration: ListenerRegistration? = null
        val uid = currentUser?.uid
        if (uid != null) {
            registration = firestore.collection("users")
                .document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val arr = snapshot?.get("following") as? List<*>
                    followingIds = arr
                        ?.filterIsInstance<String>()
                        ?.toSet()
                        ?: emptySet()
                }
        }
        onDispose { registration?.remove() }
    }

    // Infinite scroll trigger (paging at bottom)
    LaunchedEffect(listState, feedItems.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= feedItems.size - 3) {
                    vm.loadNextPageIfNeeded()
                }
            }
    }

    /**
     * ✅ AUTOPLAY (VISIBLE FIX):
     * We already pick the “most visible” video post.
     * The missing part earlier was UI: PostCard showed thumbnail unless user tapped.
     * Now: when a post becomes active candidate, we mark it started so the player renders.
     */
    LaunchedEffect(listState, feedItems, enableAutoPlay) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .collect { info ->
                if (!enableAutoPlay) return@collect

                val viewportStart = info.viewportStartOffset
                val viewportEnd = info.viewportEndOffset

                var bestPostId: String? = null
                var bestFraction = 0f
                var bestUrl: String? = null

                info.visibleItemsInfo.forEach { vi ->
                    val key = vi.key as? String ?: return@forEach
                    if (!key.startsWith("post_")) return@forEach

                    val postId = key.removePrefix("post_")
                    val postItem = feedItems
                        .asSequence()
                        .filterIsInstance<FeedItem.PostItem>()
                        .firstOrNull { it.post.postId == postId }
                        ?: return@forEach

                    val url = postItem.post.videoUrls.firstOrNull() ?: return@forEach

                    val itemTop = vi.offset
                    val itemBottom = vi.offset + vi.size
                    val visibleTop = maxOf(itemTop, viewportStart)
                    val visibleBottom = minOf(itemBottom, viewportEnd)
                    val visiblePx = (visibleBottom - visibleTop).coerceAtLeast(0)
                    val fraction = if (vi.size > 0) visiblePx.toFloat() / vi.size.toFloat() else 0f

                    if (fraction > bestFraction) {
                        bestFraction = fraction
                        bestPostId = postId
                        bestUrl = url
                    }
                }

                val candidateId = if (bestFraction >= 0.60f) bestPostId else null
                val candidateUrl = bestUrl

                if (candidateId != null && !candidateUrl.isNullOrBlank()) {
                    // ✅ critical: ensure UI switches from thumbnail → player automatically
                    startedVideoIds = startedVideoIds + candidateId

                    if (activeVideoPostId != candidateId) {
                        activeVideoPostId = candidateId
                        videoVm.requestPlay(candidateId, candidateUrl, autoplay = true)
                    }
                } else {
                    if (activeVideoPostId != null) {
                        activeVideoPostId = null
                        videoVm.pauseActive()
                    }
                }
            }
    }

    // Media prefetch for hero/top images
    LaunchedEffect(feedItems) {
        val urls = feedItems.take(12).flatMap { item ->
            when (item) {
                is FeedItem.PostItem -> item.post.imageUrls
                is FeedItem.ReelItem -> listOfNotNull(item.thumbnailUrl)
                else -> emptyList()
            }
        }
        MediaPrefetcher.prefetchImages(context, urls)
    }

    // New posts badge state
    var latestTopKey by remember { mutableStateOf<String?>(null) }
    var showNewPostsBadge by remember { mutableStateOf(false) }
    var newPostsCount by remember { mutableStateOf(0) }

    LaunchedEffect(feedItems) {
        val newTopKey = feedItems.firstOrNull()?.key
        if (newTopKey != null) {
            if (latestTopKey == null) {
                latestTopKey = newTopKey
            } else if (newTopKey != latestTopKey) {
                val isAtTop =
                    listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0

                val oldIndex = feedItems.indexOfFirst { it.key == latestTopKey }
                if (!isAtTop && oldIndex != -1) {
                    showNewPostsBadge = true
                    newPostsCount = oldIndex
                } else {
                    latestTopKey = newTopKey
                    showNewPostsBadge = false
                    newPostsCount = 0
                }
            }
        }
    }

    var showScrollToTop by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                showScrollToTop = index > 6
                if (index == 0) {
                    latestTopKey = feedItems.firstOrNull()?.key
                    showNewPostsBadge = false
                    newPostsCount = 0
                }
            }
    }
// 🔒 hide reported content for THIS user only
    val visibleFeedItems = remember(feedItems, hiddenTargetIds) {
        feedItems.filterNot { item ->
            item is FeedItem.PostItem && hiddenTargetIds.contains(item.post.postId)
        }
    }

    var mediaViewerState by remember { mutableStateOf<MediaViewerState?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(feedVibe.backgroundBrush)
    ) {
        when {
            isInitialLoading && feedItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            !isInitialLoading && feedItems.isEmpty() -> {
                EmptyFeedStateCard(
                    vibe = feedVibe,
                    onCreateFirstPost = onOpenCreatePost
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item(key = "vibe_toggle") {
                        VibeToggleRow(
                            currentVibeKey = feedVibe.key,
                            quickVibes = quickVibes,
                            onVibeSelected = { styleVm.setVibe(it) }
                        )
                    }

                    item(key = "composer_prompt")
                    {
                        WhatsOnYourMindRow(
                            userName = currentUser?.displayName,
                            onClick = onOpenCreatePost,
                            vibe = feedVibe
                        )
                        Divider(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        )
                    }
                    item(key = "reels_strip") {

                        // Take reels already present in feedItems (seed reels / reel items)
                        val reels = feedItems
                            .asSequence()
                            .filterIsInstance<FeedItem.ReelItem>()
                            .take(20)
                            .toList()

                        if (reels.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = reels,
                                    key = { _, r -> "strip_${r.key}" }
                                ) { _, r ->
                                    Card(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(220.dp)
                                            .clickable {
                                                // open reels viewer starting from this reel
                                                navController.navigate("reelsViewer/${r.id}")
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = r.thumbnailUrl,
                                                contentDescription = "Reel",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            // soft caption overlay (safe)
                                            val caption = r.caption.trim()
                                            if (caption.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.35f))
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = caption,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                        }
                                    }
                                }
                            }

                            Divider(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                        }
                    }


                    itemsIndexed(
                        items = visibleFeedItems,
                        key = { index, item ->
                            when (item) {
                                is FeedItem.TopPicks -> "top_picks_$index"
                                is FeedItem.EvergreenCard -> "evergreen_${item.key}_$index"
                                is FeedItem.PostItem -> "post_${item.post.postId}"
                                is FeedItem.ReelItem -> "reel_${item.key}_$index"
                                is FeedItem.AdItem -> "ad_${item.adId}"
                                is FeedItem.LoadingBlock -> "loading_$index"
                                is FeedItem.ErrorBlock -> "error_${item.message}_$index"
                            }
                        }
                    ) { _, item ->
                        when (item) {
                            is FeedItem.TopPicks -> {
                                TopPicksRow(picks = item.picks, vibe = feedVibe)
                                Divider(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                            }

                            is FeedItem.EvergreenCard -> {
                                EvergreenCard(item, feedVibe)
                                Divider(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                            }

                            is FeedItem.PostItem -> {
                                val post = item.post
                                val authorUid = post.uid
                                val isSaved = savedPostIds.contains(post.postId)
                                val isFollowing = currentUser?.uid != null && followingIds.contains(authorUid)

                                PostCard(
                                    post = post,
                                    onAuthorClick = { authorId -> navController.navigate("profile/$authorId") },
                                    currentUserId = currentUser?.uid,
                                    isSaved = isSaved,
                                    isFollowing = isFollowing,
                                    onToggleFollow = {
                                        val uid = currentUser?.uid ?: return@PostCard
                                        if (authorUid.isBlank() || authorUid == uid) return@PostCard
                                        val userRef = firestore.collection("users").document(uid)
                                        val currentlyFollowing = followingIds.contains(authorUid)

                                        if (currentlyFollowing) {
                                            userRef.update("following", FieldValue.arrayRemove(authorUid))
                                                .addOnFailureListener {
                                                    Toast.makeText(context, "Failed to unfollow", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            userRef.update("following", FieldValue.arrayUnion(authorUid))
                                                .addOnFailureListener {
                                                    Toast.makeText(context, "Failed to follow", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    },
                                    onLike = { vm.toggleLike(post) },
                                    onDelete = { vm.deletePost(post) { } },
                                    onToggleSave = {
                                        val uid = currentUser?.uid ?: return@PostCard
                                        val savedRef = firestore.collection("users")
                                            .document(uid)
                                            .collection("savedPosts")
                                            .document(post.postId)

                                        if (isSaved) {
                                            savedRef.delete()
                                                .addOnFailureListener {
                                                    Toast.makeText(context, "Failed to unsave", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            val previewImage = post.imageUrls.firstOrNull()
                                            val data = mapOf(
                                                "postId" to post.postId,
                                                "authorId" to post.uid,
                                                "savedAt" to FieldValue.serverTimestamp(),
                                                "previewText" to post.text.take(140),
                                                "previewImage" to previewImage
                                            )
                                            savedRef.set(data, SetOptions.merge())
                                                .addOnFailureListener {
                                                    Toast.makeText(context, "Failed to save post", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    },
                                    onComment = { text -> vm.addComment(post.postId, text) },
                                    onOpenMedia = { urls, index -> mediaViewerState = MediaViewerState(urls, index) },

                                    // ✅ video wiring
                                    videoVm = videoVm,
                                    activeVideoPostId = activeVideoPostId,
                                    startedVideoIds = startedVideoIds,
                                    onRequestPlayVideo = { postId ->
                                        activeVideoPostId = postId
                                        startedVideoIds = startedVideoIds + postId
                                        val url = post.videoUrls.firstOrNull().orEmpty()
                                        if (url.isNotBlank()) videoVm.requestPlay(postId, url, autoplay = true)
                                    },
                                    onStopVideo = { postId ->
                                        if (activeVideoPostId == postId) activeVideoPostId = null
                                        videoVm.stop(postId)
                                    },
                                    onOpenReelsViewer = { reelId ->
                                        navController.navigate("reelsViewer/$reelId")
                                    }
                                    ,
                                    feedVibe = feedVibe
                                )
                            }

                            is FeedItem.ReelItem -> {
                                ReelCard(
                                    reel = item,
                                    vibe = feedVibe,
                                    onOpenReel = { reelId ->
                                        navController.navigate("reelsViewer/$reelId")
                                    }
                                )
                            }


                            is FeedItem.AdItem -> {
                                if (item.nativeAd != null) {
                                    NativeAdCard(nativeAd = item.nativeAd)
                                } else {
                                    AdCard(item, feedVibe)
                                }
                            }

                            is FeedItem.LoadingBlock -> LoadingRow()
                            is FeedItem.ErrorBlock -> ErrorRow(item.message)
                        }
                    }

                    if (isPaging) item { LoadingRow() }
                }
            }
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { vm.clearError() },
                confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } },
                title = { Text("Error") },
                text = { Text(errorMessage ?: "") }
            )
        }

        LaunchedEffect(premiumRequired, maxImages) {
            if (premiumRequired) {
                snackbarHostState.showSnackbar(
                    "🔒 Safe Launch Mode\n" +
                            "You can add up to $maxImages photos per post right now.\n" +
                            "Thanks for helping keep Togetherly safe 🌷"
                )
                vm.clearPremiumRequired()
            }
        }


        if (isCreatePostOpen) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                CreatePostScreen(
                    maxImagesAllowed = maxImagesAllowed,
                    onClose = onDismissCreatePost,
                    onPost = { text, imageUris ->
                        vm.createPost(
                            context = context,
                            text = text,
                            imageUris = imageUris,
                            onSuccessClose = onDismissCreatePost
                        )
                    }
                )
            }
        }

        mediaViewerState?.let { state ->
            MediaViewerOverlay(
                urls = state.urls,
                initialIndex = state.initialIndex,
                onClose = { mediaViewerState = null }
            )
        }

        if (showNewPostsBadge && newPostsCount > 0) {
            NewPostsBadge(
                count = newPostsCount,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                        latestTopKey = feedItems.firstOrNull()?.key
                        showNewPostsBadge = false
                        newPostsCount = 0
                    }
                }
            )
        }

        if (showScrollToTop) {
            SmallFloatingActionButton(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = "Scroll to top")
            }
        }
        // ✅ SnackbarHost (must be rendered once per screen)
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp) // keeps it above bottom FAB / nav
        )

    }
}

/* ============================================================================

   TOP SECTION / VIBE TOGGLE / EMPTY STATE / EVERGREEN / ADS / REELS / LOADING

   ========================================================================== */

@Composable
private fun VibeToggleRow(
    currentVibeKey: String,
    quickVibes: List<QuickVibe>,
    onVibeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Vibe",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        quickVibes.forEach { vibe ->
            val selected = vibe.key == currentVibeKey
            val bg = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = vibe.label,
                modifier = Modifier
                    .background(color = bg, shape = RoundedCornerShape(999.dp))
                    .clickable { onVibeSelected(vibe.key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun WhatsOnYourMindRow(
    userName: String?,
    onClick: () -> Unit,
    vibe: FeedVibe
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = vibe.cardShape,
        colors = CardDefaults.cardColors(containerColor = vibe.composerBackground),
        elevation = CardDefaults.cardElevation(vibe.cardElevation),
        border = BorderStroke(width = 1.dp, color = vibe.composerBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (userName?.firstOrNull()?.uppercase() ?: "U"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "What's on your mind today?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "Share a thought, photo, or moment",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip("Share a thought", onClick)
                SuggestionChip("Add a photo", onClick)
                SuggestionChip("Ask for advice", onClick)
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyFeedStateCard(vibe: FeedVibe, onCreateFirstPost: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = vibe.cardShape,
            elevation = CardDefaults.cardElevation(vibe.cardElevation),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Your space is quiet 🌙",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Follow a few people or share your first post to start the vibe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCreateFirstPost) { Text("Create your first post") }
            }
        }
    }
}

@Composable
fun TopPicksRow(picks: List<TopPickData>, vibe: FeedVibe) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(picks) { _, pick ->
            Card(
                modifier = Modifier
                    .height(150.dp)
                    .fillParentMaxWidth(0.8f),
                shape = vibe.cardShape,
                elevation = CardDefaults.cardElevation(vibe.cardElevation)
            ) {
                Box {
                    AsyncImage(
                        model = pick.imageUrl,
                        contentDescription = pick.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = pick.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EvergreenCard(card: FeedItem.EvergreenCard, vibe: FeedVibe) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = vibe.cardShape,
        elevation = CardDefaults.cardElevation(vibe.cardElevation)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                card.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun AdCard(ad: FeedItem.AdItem, vibe: FeedVibe) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = vibe.cardShape,
        elevation = CardDefaults.cardElevation(vibe.cardElevation)
    ) {
        Column {
            AsyncImage(
                model = ad.imageUrl,
                contentDescription = "Sponsored",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sponsored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ReelCard(
    reel: FeedItem.ReelItem,
    vibe: FeedVibe,
    onOpenReel: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onOpenReel(reel.key) },
        shape = vibe.cardShape,
        elevation = CardDefaults.cardElevation(vibe.cardElevation)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            // Thumbnail
            if (reel.thumbnailUrl != null) {
                AsyncImage(
                    model = reel.thumbnailUrl,
                    contentDescription = "Reel preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // ▶️ Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play reel",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 🌈 Bottom gradient overlay (caption-ready)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Watch reel", // placeholder-safe
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorRow(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/* ============================================================================

   POST CARD + CREATE POST

   ========================================================================== */

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PostCard(
    post: Post,
    onAuthorClick: (String) -> Unit,
    currentUserId: String?,
    isSaved: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onToggleSave: () -> Unit,
    onComment: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,

    // video
    activeVideoPostId: String?,
    startedVideoIds: Set<String>,
    onRequestPlayVideo: (String) -> Unit,
    onStopVideo: (String) -> Unit,
    onOpenReelsViewer: (String) -> Unit,
    videoVm: VideoPlaybackViewModel,
    feedVibe: FeedVibe
) {
    val context = LocalContext.current
    val isMuted by videoVm.muted.collectAsState()
    val firestore = remember { FirebaseFirestore.getInstance() }
    var showReportPost by remember(post.postId) { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }
    val dateText = remember(post.createdAt) { post.createdAt?.toDate()?.let { sdf.format(it) } ?: "" }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showCommentBox by remember(post.postId) { mutableStateOf(false) }
    var commentText by remember(post.postId) { mutableStateOf("") }
    var comments by remember(post.postId) { mutableStateOf<List<Comment>>(emptyList()) }

    // Comments listener when box is open
    DisposableEffect(showCommentBox, post.postId) {
        if (!showCommentBox) {
            comments = emptyList()
            return@DisposableEffect onDispose { }
        }

        val reg = firestore.collection("posts")
            .document(post.postId)
            .collection("comments")
            .orderBy("createdAt")
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { it.toObject(Comment::class.java) } ?: emptyList()
                comments = list
            }

        onDispose { reg.remove() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = feedVibe.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(feedVibe.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAuthorClick(post.uid) }
                ) {
                    if (!post.authorPhoto.isNullOrBlank()) {
                        AsyncImage(
                            model = post.authorPhoto,
                            contentDescription = "Author photo",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (post.authorName?.firstOrNull()?.uppercase()) ?: "?",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = post.authorName ?: "GirlSpace User",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (dateText.isNotBlank()) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (currentUserId != null && currentUserId != post.uid) {
                    TextButton(onClick = onToggleFollow) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isSaved) "Unsave" else "Save") },
                            onClick = { showMoreMenu = false; onToggleSave() }
                        )
                        if (currentUserId != null && currentUserId == post.uid) {
                            DropdownMenuItem(
                                text = { Text("Delete post") },
                                onClick = { showMoreMenu = false; onDelete() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report") },
                            onClick = {
                                showMoreMenu = false
                                showReportPost = true
                            }
                        )

                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val displayImages = if (post.videoUrls.isNotEmpty()) post.imageUrls.drop(1) else post.imageUrls

            if (displayImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(displayImages) { index, url ->
                        Card(
                            modifier = Modifier
                                .height(220.dp)
                                .fillParentMaxWidth(0.8f)
                                .clickable { onOpenMedia(displayImages, index) },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Post image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ✅ Inline video (AUTOPLAY FIXED VISUALLY)
            if (post.videoUrls.isNotEmpty()) {
                val videoUrl = post.videoUrls.first()
                val thumbUrl = post.imageUrls.firstOrNull()
                val isActive = activeVideoPostId == post.postId

                // ✅ started becomes true either when user taps OR when feed marks it active
                var started by remember(post.postId) { mutableStateOf(false) }
                LaunchedEffect(isActive, startedVideoIds) {
                    if (isActive || startedVideoIds.contains(post.postId)) started = true
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        if (!started) {
                            if (!thumbUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = "Video thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        started = true
                                        onRequestPlayVideo(post.postId)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(color = Color.Black.copy(alpha = 0.35f), shape = CircleShape) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.padding(18.dp).size(42.dp)
                                    )
                                }
                            }
                        } else {
                            // ✅ If Feed made it active, ensure this post owns the shared player
                            LaunchedEffect(isActive, videoUrl) {
                                if (isActive && videoUrl.isNotBlank()) {
                                    videoVm.requestPlay(post.postId, videoUrl, autoplay = true)
                                }
                            }

                            // Tap video opens full-screen reels viewer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onOpenReelsViewer(post.postId) }
                            ) {
                                InlineVideoPlayer(
                                    videoVm = videoVm,
                                    postId = post.postId,
                                    url = videoUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    showController = false
                                )
                            }
                        }

                        // Mute toggle
                        IconButton(
                            onClick = { videoVm.toggleMute() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = "Toggle audio",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isLiked = currentUserId != null && post.likedBy.contains(currentUserId)
                val likeColor by animateColorAsState(
                    targetValue = if (isLiked) feedVibe.likeAccent else MaterialTheme.colorScheme.onSurface,
                    label = "likeColor"
                )
                val likeScale by animateFloatAsState(
                    targetValue = if (isLiked) 1.15f else 1f,
                    label = "likeScale"
                )

                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .shadow(elevation = if (isLiked) 6.dp else 0.dp, shape = CircleShape)
                        .background(
                            color = if (isLiked) feedVibe.likeAccent.copy(alpha = 0.22f) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = likeColor,
                        modifier = Modifier.graphicsLayer { scaleX = likeScale; scaleY = likeScale }
                    )
                }

                Text(
                    text = "${post.likeCount} likes",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "${post.commentsCount} comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showCommentBox = !showCommentBox }
                )

                IconButton(onClick = { showCommentBox = !showCommentBox }) {
                    Icon(imageVector = Icons.Filled.ChatBubbleOutline, contentDescription = "View comments")
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        val shareText = buildString {
                            if (post.text.isNotBlank()) append(post.text.trim()) else append("Check out this post on Togetherly!")
                            append("\n\n")
                            append("Open in Togetherly: https://togetherly.app/post/${post.postId}")
                        }

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }

                        try {
                            context.startActivity(Intent.createChooser(intent, "Share post via"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share post")
                }
            }

            if (currentUserId != null && currentUserId == post.uid) {
                val interactions = (post.likeCount ?: 0) + (post.commentsCount ?: 0)
                if (interactions > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Engagement: $interactions interactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCommentBox) {
                if (comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        comments.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .padding(end = 6.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (c.authorName?.firstOrNull()?.uppercase() ?: "U"),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.authorName ?: "User",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(text = c.text, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add a comment...") },
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val trimmed = commentText.trim()
                            if (trimmed.isNotEmpty()) {
                                onComment(trimmed)
                                commentText = ""
                            }
                        }
                    ) { Text("Post") }
                }
            }
        }
    }
    if (showReportPost) {
        ReportPostDialog(
            postId = post.postId,
            postOwnerId = post.uid,
            onDismiss = { showReportPost = false },
            onSubmitted = {
                showReportPost = false
                Toast.makeText(context, "Thanks — report submitted.", Toast.LENGTH_SHORT).show()
            }
        )
    }

}

@Composable
fun CreatePostScreen(
    maxImagesAllowed: Int,
    onClose: () -> Unit,
    onPost: (String, List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var limitWarning by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.size > maxImagesAllowed) {
            selectedImages = uris.take(maxImagesAllowed)
            limitWarning =
                "🔒 Safe Launch Mode\n" +
                        "You can add up to $maxImagesAllowed photos per post right now.\n" +
                        "Thanks for helping keep Togetherly safe 🌷"
        } else {
            selectedImages = uris
            limitWarning = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Create post", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                value = text,
                onValueChange = { text = it },
                label = { Text("What's on your mind?") },
                maxLines = 5
            )

            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(selectedImages) { _, uri ->
                        Card(
                            modifier = Modifier
                                .size(90.dp)
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = uri
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            if (limitWarning != null) {
                Text(text = limitWarning ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { imagePickerLauncher.launch("image/*") }) { Text("Add photos") }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onClose) { Text("Cancel") }

                TextButton(
                    onClick = {
                        if (selectedImages.size > maxImagesAllowed) {
                            limitWarning =
                                "🔒 Safe Launch Mode\n" +
                                        "You can add up to $maxImagesAllowed photos per post right now.\n" +
                                        "Extra photos were ignored. Thanks for helping keep Togetherly safe 🌷"
                        } else {
                            onPost(text, selectedImages)
                        }
                    }
                ) { Text("Post") }
            }

            Text(
                text = "🔒 Safe Launch Mode: Up to $maxImagesAllowed photos per post during launch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Fullscreen media viewer
// ---------------------------------------------------------------------------

data class MediaViewerState(
    val urls: List<String>,
    val initialIndex: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerOverlay(
    urls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { urls.size }
    )

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val url = urls[page]
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Close", color = Color.White)
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${urls.size}",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/* ---------------------------------------------------------------------------
   New posts badge
   ------------------------------------------------------------------------ */

@Composable
private fun NewPostsBadge(
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "↑ $count new posts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportPostDialog(
    postId: String,
    postOwnerId: String,
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
        title = { Text("Report Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Text("Select a reason:", style = MaterialTheme.typography.bodyMedium)

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
                    if (postId.isBlank() || postOwnerId.isBlank()) {
                        error = "Invalid post."
                        return@TextButton
                    }

                    submitting = true
                    error = null

                    val reportId = UUID.randomUUID().toString()
                    val data = hashMapOf(
                        "type" to "POST",
                        "postId" to postId,
                        "targetId" to postId,
                        "reporterUserId" to uid,
                        "reportedUserId" to postOwnerId,
                        "reason" to selected.wire,
                        "note" to note.text.trim().takeIf { it.isNotBlank() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "status" to "OPEN"
                    )

                    if (data["note"] == null) data.remove("note")

                    firestore.collection("reports")
                        .document(reportId)
                        .set(data)
                        .addOnSuccessListener {
                            // ✅ hide for reporting user only (wait for completion)
                            firestore.collection("users")
                                .document(uid)
                                .collection("hiddenContent")
                                .document(postId)
                                .set(
                                    mapOf(
                                        "targetId" to postId,
                                        "type" to "POST",
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
                                    error = e.message ?: "Report saved, but failed to hide this post."
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
