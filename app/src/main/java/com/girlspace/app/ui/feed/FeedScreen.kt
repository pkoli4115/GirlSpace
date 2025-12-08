package com.girlspace.app.ui.feed

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.girlspace.app.core.plan.PlanLimitsRepository
import com.girlspace.app.data.feed.Comment
import com.girlspace.app.ui.feed.FeedItem
import com.girlspace.app.ui.feed.MediaPrefetcher
import com.girlspace.app.data.feed.Post
import com.girlspace.app.ui.feed.TopPicksRow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FeedScreen(
    isCreatePostOpen: Boolean,
    onDismissCreatePost: () -> Unit,
    onOpenCreatePost: () -> Unit
) {
    val vm: FeedViewModel = viewModel()
    val feedItems by vm.feedItems.collectAsState()
    val isInitialLoading by vm.isInitialLoading.collectAsState()
    val isPaging by vm.isPaging.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val premiumRequired by vm.premiumRequired.collectAsState()
    val maxImages by vm.currentMaxImages.collectAsState()

    val planLimits by PlanLimitsRepository.planLimits.collectAsState()
    val maxImagesAllowed = planLimits.maxImagesPerPost

    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val firestore = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    val listState = rememberLazyListState()

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

    // Following set for this user
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(currentUser?.uid) {
        var registration: ListenerRegistration? = null
        val uid = currentUser?.uid
        if (uid != null) {
            registration = firestore.collection("users")
                .document(uid)
                .collection("following")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val ids = snapshot?.documents
                        ?.map { it.id }
                        ?.toSet()
                        ?: emptySet()
                    followingIds = ids
                }
        }
        onDispose { registration?.remove() }
    }

    // Infinite scroll trigger
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

    // Full-screen media viewer
    var mediaViewerState by remember { mutableStateOf<MediaViewerState?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        if (isInitialLoading && feedItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Top composer card
                item(key = "composer_prompt") {
                    WhatsOnYourMindRow(
                        userName = currentUser?.displayName,
                        onClick = onOpenCreatePost
                    )
                    Divider()
                }

                itemsIndexed(
                    items = feedItems,
                    key = { _, item -> item.key }
                ) { _, item ->
                    when (item) {
                        is FeedItem.TopPicks -> {
                            TopPicksRow(item.picks)
                            Divider()
                        }

                        is FeedItem.EvergreenCard -> {
                            EvergreenCard(item)
                            Divider()
                        }

                        is FeedItem.PostItem -> {
                            val post = item.post
                            val authorUid = post.uid
                            val isSaved = savedPostIds.contains(post.postId)
                            val isFollowingAuthor =
                                currentUser?.uid != null &&
                                        authorUid.isNotBlank() &&
                                        authorUid != currentUser.uid &&
                                        followingIds.contains(authorUid)

                            PostCard(
                                post = post,
                                currentUserId = currentUser?.uid,
                                isSaved = isSaved,
                                isFollowing = isFollowingAuthor,
                                onToggleFollow = {
                                    val uid = currentUser?.uid
                                    val targetUid = authorUid
                                    if (uid == null || targetUid.isBlank() || uid == targetUid) return@PostCard

                                    val followingRef = firestore.collection("users")
                                        .document(uid)
                                        .collection("following")
                                        .document(targetUid)

                                    val followersRef = firestore.collection("users")
                                        .document(targetUid)
                                        .collection("followers")
                                        .document(uid)

                                    val userDoc = firestore.collection("users").document(uid)
                                    val targetDoc = firestore.collection("users").document(targetUid)

                                    val currentlyFollowing = followingIds.contains(targetUid)

                                    if (currentlyFollowing) {
                                        // Unfollow
                                        followingRef.delete()
                                        followersRef.delete()
                                        userDoc.update("followingCount", FieldValue.increment(-1))
                                        targetDoc.update("followersCount", FieldValue.increment(-1))
                                    } else {
                                        // Follow
                                        val data = mapOf(
                                            "userId" to uid,
                                            "targetId" to targetUid,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                        followingRef.set(data, SetOptions.merge())
                                        followersRef.set(data, SetOptions.merge())
                                        userDoc.update("followingCount", FieldValue.increment(1))
                                        targetDoc.update("followersCount", FieldValue.increment(1))
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
                                                Toast.makeText(
                                                    context,
                                                    "Failed to unsave",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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
                                                Toast.makeText(
                                                    context,
                                                    "Failed to save post",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                },
                                onComment = { text ->
                                    vm.addComment(post.postId, text)
                                },
                                onOpenMedia = { urls, index ->
                                    mediaViewerState = MediaViewerState(urls, index)
                                }
                            )
                            Divider()
                        }

                        is FeedItem.ReelItem -> {
                            ReelCard(item)
                            Divider()
                        }

                        is FeedItem.AdItem -> {
                            AdCard(item)
                            Divider()
                        }

                        is FeedItem.LoadingBlock -> {
                            LoadingRow()
                        }

                        is FeedItem.ErrorBlock -> {
                            ErrorRow(item.message)
                        }
                    }
                }

                if (isPaging) {
                    item { LoadingRow() }
                }
            }
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { vm.clearError() },
                confirmButton = {
                    TextButton(onClick = { vm.clearError() }) {
                        Text("OK")
                    }
                },
                title = { Text("Error") },
                text = { Text(errorMessage ?: "") }
            )
        }

        if (premiumRequired) {
            AlertDialog(
                onDismissRequest = { vm.clearPremiumRequired() },
                confirmButton = {
                    TextButton(onClick = { vm.clearPremiumRequired() }) {
                        Text("OK")
                    }
                },
                title = { Text("Premium feature") },
                text = {
                    Text(
                        "Your current plan allows up to $maxImages images per post. " +
                                "Please upgrade your plan in Menu → Manage Subscriptions " +
                                "to attach more images."
                    )
                }
            )
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
    }
}

/* ============================================================================
   TOP SECTION / EVERGREEN / ADS / REELS / LOADING / ERROR
   ========================================================================== */

@Composable
private fun WhatsOnYourMindRow(
    userName: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                Text(
                    text = "Share a thought, photo, or moment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TopPicksRow(picks: List<TopPickData>) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(picks) { _, pick ->
            Card(
                modifier = Modifier
                    .height(140.dp)
                    .fillParentMaxWidth(0.8f)
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
fun EvergreenCard(card: FeedItem.EvergreenCard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(card.caption, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AdCard(ad: FeedItem.AdItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
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
fun ReelCard(reel: FeedItem.ReelItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (reel.thumbnailUrl != null) {
                AsyncImage(
                    model = reel.thumbnailUrl,
                    contentDescription = "Reel preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reel",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp
        )
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/* ============================================================================
   POST CARD + CREATE POST
   ========================================================================== */

@Composable
fun PostCard(
    post: Post,
    currentUserId: String?,
    isSaved: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onToggleSave: () -> Unit,
    onComment: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }

    val sdf = remember {
        SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    }
    val dateText = remember(post.createdAt) {
        post.createdAt?.toDate()?.let { sdf.format(it) } ?: ""
    }

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
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(Comment::class.java) }
                    ?: emptyList()
                comments = list
            }

        onDispose { reg.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // Header: avatar + name + time + follow + 3-dots
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!post.authorPhoto.isNullOrBlank()) {
                AsyncImage(
                    model = post.authorPhoto,
                    contentDescription = "Author photo",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (post.authorName?.firstOrNull()?.uppercase()) ?: "?",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorName ?: "GirlSpace User",
                    style = MaterialTheme.typography.titleMedium
                )
                if (dateText.isNotBlank()) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions"
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isSaved) "Unsave" else "Save") },
                        onClick = {
                            showMoreMenu = false
                            onToggleSave()
                        }
                    )
                    if (currentUserId != null && currentUserId == post.uid) {
                        DropdownMenuItem(
                            text = { Text("Delete post") },
                            onClick = {
                                showMoreMenu = false
                                onDelete()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Report") },
                        onClick = {
                            showMoreMenu = false
                            Toast.makeText(
                                context,
                                "Thanks, we’ve received your report.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (post.text.isNotBlank()) {
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (post.imageUrls.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(post.imageUrls) { index, url ->
                    Card(
                        modifier = Modifier
                            .height(220.dp)
                            .fillParentMaxWidth(0.8f)
                            .clickable {
                                onOpenMedia(post.imageUrls, index)
                            }
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

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isLiked = currentUserId != null && post.likedBy.contains(currentUserId)

            IconButton(onClick = onLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like"
                )
            }

            Text(
                text = "${post.likeCount} likes",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "${post.commentsCount} comments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showCommentBox = !showCommentBox }
            )

            IconButton(onClick = { showCommentBox = !showCommentBox }) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = "View comments"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    val shareText = buildString {
                        if (post.text.isNotBlank()) {
                            append(post.text.trim())
                        } else {
                            append("Check out this post on GirlSpace!")
                        }
                        append("\n\n")
                        append("Open in GirlSpace: https://girlspace.app/post/${post.postId}")
                    }

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }

                    try {
                        context.startActivity(
                            Intent.createChooser(intent, "Share post via")
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "No app available to share.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share post"
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
                                shape = MaterialTheme.shapes.small
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
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                ) {
                    Text("Post")
                }
            }
        }
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
                "Your current plan allows up to $maxImagesAllowed images per post. Extra images were ignored."
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
            Text(
                text = "Create post",
                style = MaterialTheme.typography.headlineSmall
            )

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
                                        Toast.makeText(
                                            context,
                                            "Cannot open image",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                Text(
                    text = limitWarning ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Text("Add photos")
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onClose) {
                    Text("Cancel")
                }

                TextButton(
                    onClick = {
                        if (selectedImages.size > maxImagesAllowed) {
                            limitWarning =
                                "Your current plan allows up to $maxImagesAllowed images per post."
                        } else {
                            onPost(text, selectedImages)
                        }
                    }
                ) {
                    Text("Post")
                }
            }

            Text(
                text = "Your current plan allows up to $maxImagesAllowed images per post.",
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
