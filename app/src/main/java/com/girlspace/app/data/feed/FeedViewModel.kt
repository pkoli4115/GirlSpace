package com.girlspace.app.ui.feed

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.feed.CreatePostResult
import com.girlspace.app.data.feed.FeedRepository
import com.girlspace.app.data.feed.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedViewModel : ViewModel() {

    private val repo = FeedRepository(
        auth = FirebaseAuth.getInstance(),
        firestore = FirebaseFirestore.getInstance(),
        storage = FirebaseStorage.getInstance()
    )

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating

    private val _premiumRequired = MutableStateFlow(false)
    val premiumRequired: StateFlow<Boolean> = _premiumRequired

    // For dialog text ("your plan allows up to X images")
    private val _currentMaxImages = MutableStateFlow(1)
    val currentMaxImages: StateFlow<Int> = _currentMaxImages

    private var feedListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        observeFeed()
    }

    private fun observeFeed() {
        _isLoading.value = true
        feedListener = repo.observeFeed { list ->
            _posts.value = list
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPremiumRequired() {
        _premiumRequired.value = false
    }

    fun toggleLike(post: Post) {
        repo.toggleLike(post)
    }

    fun deletePost(post: Post, onDone: (Boolean) -> Unit) {
        _isLoading.value = true
        repo.deletePost(post) { ok ->
            _isLoading.value = false
            onDone(ok)
        }
    }

    /**
     * 1) Load user's plan from /users/{uid}
     * 2) Decide max images based on plan (free/basic/premium)
     * 3) If within limit → decode bitmaps and create post
     */
    fun createPost(
        context: Context,
        text: String,
        imageUris: List<Uri>,
        onSuccessClose: () -> Unit
    ) {
        if (text.isBlank() && imageUris.isEmpty()) {
            _errorMessage.value = "Write something or select at least one image"
            return
        }

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            _errorMessage.value = "Not logged in"
            return
        }

        val uid = user.uid
        _isCreating.value = true
        _errorMessage.value = null
        _premiumRequired.value = false

        // Step 1: get user's plan from Firestore (no coroutines needed)
        val usersRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)

        usersRef.get()
            .addOnSuccessListener { snapshot ->
                val isPremiumFlag = snapshot.getBoolean("isPremium") ?: false
                val planRaw = snapshot.getString("plan")
                val plan = (planRaw ?: if (isPremiumFlag) "premium" else "free")
                    .lowercase()

                val maxImages = when (plan) {
                    "basic" -> 5         // Basic: up to 5 images
                    "premium" -> 10      // Premium: up to 10 images
                    else -> 1            // Free (ads): 1 image
                }
                _currentMaxImages.value = maxImages

                // If user tries to go beyond plan limit → show premium dialog
                if (imageUris.size > maxImages) {
                    _isCreating.value = false
                    _premiumRequired.value = true
                    return@addOnSuccessListener
                }

                // Step 2: decode images + create post as before
                viewModelScope.launch {
                    try {
                        val bitmaps = withContext(Dispatchers.IO) {
                            imageUris.mapNotNull { uri ->
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        BitmapFactory.decodeStream(input)
                                    }
                                } catch (e: Exception) {
                                    Log.e("FeedViewModel", "Failed to decode image: $uri", e)
                                    null
                                }
                            }
                        }

                        repo.createPost(
                            text = text.trim(),
                            bitmaps = bitmaps
                        ) { result ->
                            _isCreating.value = false
                            when (result) {
                                is CreatePostResult.Success -> {
                                    onSuccessClose()
                                }

                                is CreatePostResult.PremiumRequired -> {
                                    _premiumRequired.value = true
                                }

                                is CreatePostResult.Error -> {
                                    _errorMessage.value = result.message ?: "Failed to create post"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _isCreating.value = false
                        _errorMessage.value = e.localizedMessage ?: "Unexpected error"
                    }
                }
            }
            .addOnFailureListener { e ->
                _isCreating.value = false
                _errorMessage.value = e.localizedMessage ?: "Failed to load plan"
            }
    }

    override fun onCleared() {
        super.onCleared()
        feedListener?.remove()
    }
}
