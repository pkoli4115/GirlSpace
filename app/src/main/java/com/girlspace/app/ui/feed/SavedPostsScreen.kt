package com.girlspace.app.ui.feed
import androidx.compose.foundation.layout.width
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.girlspace.app.data.feed.Post
import com.girlspace.app.data.feed.Comment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

data class SavedPostPreview(
    val postId: String = "",
    val authorId: String? = null,
    val previewText: String? = null,
    val previewImage: String? = null,
    val savedAtMillis: Long? = null
)

/**
 * Saved posts screen:
 * - Shows list of saved post previews
 * - Tapping a row opens a full-screen PostCard with full interactions
 */
@Composable
fun SavedPostsScreen(
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val firestore = remember { FirebaseFirestore.getInstance() }

    var items by remember { mutableStateOf<List<SavedPostPreview>>(emptyList()) }

    // For full-screen post view
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var selectedIsSaved by remember { mutableStateOf(false) }
    var isLoadingPost by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }

    val feedVm: FeedViewModel = viewModel()

    // Listen to saved posts collection
    DisposableEffect(user?.uid) {
        if (user == null) {
            Log.w("SavedPosts", "No user logged in")
            return@DisposableEffect onDispose { }
        }
        val uid = user.uid

        val reg: ListenerRegistration = firestore.collection("users")
            .document(uid)
            .collection("savedPosts")
            .orderBy("savedAt")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("SavedPosts", "Listen failed", err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc ->
                    val ts = doc.getTimestamp("savedAt")
                    SavedPostPreview(
                        postId = doc.getString("postId") ?: doc.id,
                        authorId = doc.getString("authorId"),
                        previewText = doc.getString("previewText"),
                        previewImage = doc.getString("previewImage"),
                        savedAtMillis = ts?.toDate()?.time
                    )
                } ?: emptyList()
                items = list.sortedByDescending { it.savedAtMillis ?: 0L }
            }

        onDispose { reg.remove() }
    }

    // When a specific saved row is tapped, listen to that post + its saved status
    DisposableEffect(selectedPostId, user?.uid) {
        val postId = selectedPostId
        val uid = user?.uid
        if (postId == null || uid == null) {
            selectedPost = null
            postError = null
            isLoadingPost = false
            selectedIsSaved = false
            return@DisposableEffect onDispose { }
        }

        isLoadingPost = true
        postError = null

        val postRef = firestore.collection("posts").document(postId)
        val savedRef = firestore.collection("users")
            .document(uid)
            .collection("savedPosts")
            .document(postId)

        var postReg: ListenerRegistration? = null
        var savedReg: ListenerRegistration? = null

        postReg = postRef.addSnapshotListener { snap, err ->
            if (err != null) {
                postError = "Failed to load post."
                isLoadingPost = false
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                val p = snap.toObject(Post::class.java)
                if (p != null) {
                    // Ensure postId field is filled
                    selectedPost = p.copy(postId = snap.id)
                    postError = null
                } else {
                    postError = "Post not found."
                    selectedPost = null
                }
            } else {
                postError = "Post not found."
                selectedPost = null
            }
            isLoadingPost = false
        }

        savedReg = savedRef.addSnapshotListener { snap, _ ->
            selectedIsSaved = snap?.exists() == true
        }

        onDispose {
            postReg?.remove()
            savedReg?.remove()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Saved posts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (user == null) {
                Text(
                    text = "Please sign in to see saved posts.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (items.isEmpty()) {
                Text(
                    text = "You haven’t saved any posts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.postId }) { saved ->
                        SavedPostRow(
                            saved = saved,
                            onClick = { selectedPostId = saved.postId }
                        )
                    }
                }
            }

            // Full-screen post view overlay when a saved post is selected
            selectedPostId?.let { _ ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BackHandler {
                            selectedPostId = null
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            // Mini top bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedPostId = null }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack,
                                        contentDescription = "Close post"
                                    )
                                }
                                Text(
                                    text = "Post",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                            ) {
                                when {
                                    isLoadingPost -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.CircularProgressIndicator()
                                        }
                                    }

                                    postError != null -> {
                                        Text(
                                            text = postError ?: "Error",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }

                                    selectedPost != null -> {
                                        // Reuse the main feed PostCard UI
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            item {
                                                PostCard(
                                                    post = selectedPost!!,
                                                    currentUserId = user?.uid,          // ✅ user is nullable, PostCard accepts String?
                                                    isSaved = selectedIsSaved,
                                                    onLike = { feedVm.toggleLike(selectedPost!!) },
                                                    onDelete = {
                                                        feedVm.deletePost(selectedPost!!) {
                                                            // After delete, close and refresh list automatically
                                                            selectedPostId = null
                                                        }
                                                    },
                                                    onToggleSave = {
                                                        val uid = user?.uid ?: return@PostCard   // ✅ early return if user is null
                                                        val postId = selectedPost!!.postId
                                                        val savedRef = firestore.collection("users")
                                                            .document(uid)
                                                            .collection("savedPosts")
                                                            .document(postId)

                                                        if (selectedIsSaved) {
                                                            savedRef.delete()
                                                                .addOnFailureListener {
                                                                    Log.e("SavedPosts", "Failed to unsave", it)
                                                                }
                                                        } else {
                                                            val previewImage = selectedPost!!.imageUrls.firstOrNull()
                                                            val data = mapOf(
                                                                "postId" to postId,
                                                                "authorId" to selectedPost!!.uid,
                                                                "savedAt" to FieldValue.serverTimestamp(),
                                                                "previewText" to selectedPost!!.text.take(140),
                                                                "previewImage" to previewImage
                                                            )
                                                            savedRef.set(data, SetOptions.merge())
                                                                .addOnFailureListener {
                                                                    Log.e("SavedPosts", "Failed to save post", it)
                                                                }
                                                        }
                                                    },
                                                    onComment = { text ->
                                                        feedVm.addComment(selectedPost!!.postId, text)
                                                    },
                                                    onOpenMedia = { _, _ ->
                                                        // For now we rely on Feed-style overlay; you can plug a media viewer here later if you want.
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPostRow(
    saved: SavedPostPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!saved.previewImage.isNullOrBlank()) {
            AsyncImage(
                model = saved.previewImage,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = saved.previewText?.ifBlank { "Photo post" } ?: "Photo post",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Tap to view",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
