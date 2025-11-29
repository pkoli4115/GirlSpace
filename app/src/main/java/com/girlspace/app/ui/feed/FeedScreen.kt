package com.girlspace.app.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.girlspace.app.core.plan.PlanLimitsRepository
import com.girlspace.app.data.feed.Post
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FeedScreen(
    isCreatePostOpen: Boolean,
    onDismissCreatePost: () -> Unit
) {
    val vm: FeedViewModel = viewModel()
    val posts by vm.posts.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val premiumRequired by vm.premiumRequired.collectAsState()
    val maxImages by vm.currentMaxImages.collectAsState()

    val planLimits by PlanLimitsRepository.planLimits.collectAsState()
    val maxImagesAllowed = planLimits.maxImagesPerPost

    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(posts) { post ->
                    PostCard(
                        post = post,
                        currentUserId = currentUser?.uid,
                        onLike = { vm.toggleLike(post) },
                        onDelete = {
                            vm.deletePost(post) { /* optional toast later */ }
                        }
                    )
                    Divider()
                }
            }
        }

        // Error dialog
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

        // Premium required dialog (uses current plan's max images)
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

        // Create post overlay
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
    }
}

@Composable
private fun PostCard(
    post: Post,
    currentUserId: String?,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember {
        SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    }
    val dateText = remember(post.createdAt) {
        post.createdAt?.toDate()?.let { sdf.format(it) } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // Top: Author row
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
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (post.authorName?.firstOrNull()?.uppercase())
                                ?: "?",
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

            if (currentUserId != null && currentUserId == post.uid) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete post"
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
                items(post.imageUrls) { url ->
                    Card(
                        modifier = Modifier
                            .height(220.dp)
                            .fillParentMaxWidth(0.8f)
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

        // Actions row
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Create Post UI with:
 *  - image picker
 *  - inline image-limit warning based on current plan
 */
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
                modifier = Modifier.fillMaxWidth(),
                value = text,
                onValueChange = { text = it },
                label = { Text("What's on your mind?") },
                maxLines = 5
            )

            // Selected images preview
            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedImages) { uri ->
                        Card(
                            modifier = Modifier
                                .size(90.dp)
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

            // Inline plan warning, if user tried to exceed limit
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
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Text("Add photos")
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onClose) {
                    Text("Cancel")
                }

                TextButton(
                    onClick = {
                        // Extra safety check
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
