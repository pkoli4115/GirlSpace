package com.girlspace.app.ui.feed

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.girlspace.app.data.feed.Post
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    navController: NavController
) {
    val firestore = remember { FirebaseFirestore.getInstance() }

    var isLoading by remember { mutableStateOf(true) }
    var post by remember { mutableStateOf<Post?>(null) }
    var ownerName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        try {
            val doc = firestore.collection("posts")
                .document(postId)
                .get()
                .await()

            if (!doc.exists()) {
                post = null
                isLoading = false
                return@LaunchedEffect
            }

            val loaded = Post(
                postId = doc.id,
                uid = doc.getString("uid")
                    ?: doc.getString("authorId")
                    ?: "",
                text = doc.getString("text") ?: "",
                imageUrls = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it as? String }
                    ?: emptyList(),
                videoUrls = (doc.get("videoUrls") as? List<*>)?.mapNotNull { it as? String }
                    ?: emptyList(),
                createdAt = doc.getTimestamp("createdAt"),
                likeCount = (doc.getLong("likeCount") ?: 0L).toInt(),
                likedBy = (doc.get("likedBy") as? List<*>)?.mapNotNull { it as? String }
                    ?: emptyList(),
                commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                authorName = doc.getString("authorName"),
                authorPhoto = doc.getString("authorPhoto"),
                isAuthorPremium = doc.getBoolean("isAuthorPremium") ?: false
            )

            post = loaded
            ownerName = loaded.authorName
        } catch (e: Exception) {
            Log.e("PostDetailScreen", "Failed to load post $postId", e)
            post = null
        } finally {
            isLoading = false
        }
    }

    val titleText = ownerName?.let { "Post by $it" } ?: "Post"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                post == null -> {
                    Text(
                        text = "Post not found",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    )
                }

                else -> {
                    val p = post!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (p.imageUrls.isNotEmpty()) {
                            AsyncImage(
                                model = p.imageUrls.first(),
                                contentDescription = "Post image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp)
                            )
                        }

                        if (p.text.isNotBlank()) {
                            Text(
                                text = p.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${p.likeCount} likes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${p.commentsCount} comments",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
