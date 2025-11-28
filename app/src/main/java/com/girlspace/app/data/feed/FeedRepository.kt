package com.girlspace.app.data.feed

import android.graphics.Bitmap
import android.util.Log
import com.girlspace.app.data.user.UserSummary
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed class CreatePostResult {
    object Success : CreatePostResult()
    object PremiumRequired : CreatePostResult()
    data class Error(val message: String?) : CreatePostResult()
}

class FeedRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    companion object {
        private const val TAG = "FeedRepository"

        // Non-premium: max 1 image
        private const val MAX_IMAGES_FREE = 1

        // Premium users can attach more images per post
        private const val MAX_IMAGES_PREMIUM = 10
    }

    /**
     * Listen to all posts in reverse chronological order.
     */
    fun observeFeed(onPosts: (List<Post>) -> Unit): ListenerRegistration {
        return firestore.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeFeed: error", error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents?.map { doc ->
                    Post(
                        postId = doc.id,
                        uid = doc.getString("uid") ?: "",
                        text = doc.getString("text") ?: "",
                        imageUrls = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        createdAt = doc.getTimestamp("createdAt"),
                        likeCount = (doc.getLong("likeCount") ?: 0L).toInt(),
                        likedBy = (doc.get("likedBy") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                        authorName = doc.getString("authorName"),
                        authorPhoto = doc.getString("authorPhoto")
                    )
                } ?: emptyList()

                onPosts(posts)
            }
    }

    /**
     * Create a post with text + optional multiple images.
     * Applies subscription rule:
     *  - Free user: max 1 image
     *  - Premium user: up to MAX_IMAGES_PREMIUM
     */
    fun createPost(
        text: String,
        bitmaps: List<Bitmap>,
        onResult: (CreatePostResult) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(CreatePostResult.Error("Not logged in"))
            return
        }

        // Step 1: load user profile to check premium flag + name/photo
        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                val isPremium = doc.getBoolean("isPremium") ?: false

                val allowedMax = if (isPremium) MAX_IMAGES_PREMIUM else MAX_IMAGES_FREE
                if (!isPremium && bitmaps.size > allowedMax) {
                    // 🚫 Free user trying to attach more than 1 image
                    onResult(CreatePostResult.PremiumRequired)
                    return@addOnSuccessListener
                }

                val userSummary = UserSummary(
                    uid = currentUser.uid,
                    name = doc.getString("name") ?: (currentUser.displayName ?: ""),
                    photoUrl = doc.getString("photoUrl") ?: currentUser.photoUrl?.toString()
                )

                // Step 2: actually create the post document + upload images
                internalCreatePostWithUser(
                    text = text,
                    bitmaps = bitmaps.take(allowedMax),
                    user = userSummary,
                    onResult = onResult
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "createPost: failed to load user profile", e)
                onResult(CreatePostResult.Error("Failed to load profile"))
            }
    }

    private fun internalCreatePostWithUser(
        text: String,
        bitmaps: List<Bitmap>,
        user: UserSummary,
        onResult: (CreatePostResult) -> Unit
    ) {
        val postId = UUID.randomUUID().toString()

        // If no images, directly write the post
        if (bitmaps.isEmpty()) {
            val postData = mapOf(
                "uid" to user.uid,
                "text" to text,
                "imageUrls" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "likeCount" to 0,
                "likedBy" to emptyList<String>(),
                "commentsCount" to 0,
                "authorName" to user.name,
                "authorPhoto" to user.photoUrl
            )

            firestore.collection("posts")
                .document(postId)
                .set(postData)
                .addOnSuccessListener {
                    onResult(CreatePostResult.Success)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "internalCreatePostWithUser: failed to create post", e)
                    onResult(CreatePostResult.Error("Failed to create post"))
                }

            return
        }

        // If there are images, upload them sequentially then save Firestore doc
        uploadImagesSequentially(
            postId = postId,
            bitmaps = bitmaps,
            onResult = { uploadResult ->
                when (uploadResult) {
                    is UploadImagesResult.Success -> {
                        val postData = mapOf(
                            "uid" to user.uid,
                            "text" to text,
                            "imageUrls" to uploadResult.imageUrls,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "likeCount" to 0,
                            "likedBy" to emptyList<String>(),
                            "commentsCount" to 0,
                            "authorName" to user.name,
                            "authorPhoto" to user.photoUrl
                        )

                        firestore.collection("posts")
                            .document(postId)
                            .set(postData)
                            .addOnSuccessListener {
                                onResult(CreatePostResult.Success)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "internalCreatePostWithUser: failed to create post", e)
                                onResult(CreatePostResult.Error("Failed to create post"))
                            }
                    }

                    is UploadImagesResult.Error -> {
                        onResult(CreatePostResult.Error(uploadResult.message))
                    }
                }
            }
        )
    }

    private sealed class UploadImagesResult {
        data class Success(val imageUrls: List<String>) : UploadImagesResult()
        data class Error(val message: String?) : UploadImagesResult()
    }

    /**
     * Compress and upload each bitmap as JPEG ~75% to:
     *   /posts/{postId}/image_{index}.jpg
     */
    private fun uploadImagesSequentially(
        postId: String,
        bitmaps: List<Bitmap>,
        onResult: (UploadImagesResult) -> Unit
    ) {
        if (bitmaps.isEmpty()) {
            onResult(UploadImagesResult.Success(emptyList()))
            return
        }

        val imageUrls = mutableListOf<String>()
        fun uploadNext(index: Int) {
            if (index >= bitmaps.size) {
                onResult(UploadImagesResult.Success(imageUrls))
                return
            }

            val bitmap = bitmaps[index]
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos) // 👈 compression
            val data = baos.toByteArray()

            val ref = storage.reference
                .child("posts")
                .child(postId)
                .child("image_$index.jpg")

            ref.putBytes(data)
                .addOnSuccessListener {
                    ref.downloadUrl
                        .addOnSuccessListener { uri ->
                            imageUrls.add(uri.toString())
                            uploadNext(index + 1)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "uploadImagesSequentially: failed to get downloadUrl", e)
                            onResult(UploadImagesResult.Error("Failed to upload image"))
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "uploadImagesSequentially: upload failed", e)
                    onResult(UploadImagesResult.Error("Failed to upload image"))
                }
        }

        uploadNext(0)
    }

    /**
     * Toggle like for a post.
     * If currently liked, remove. Otherwise add.
     */
    fun toggleLike(post: Post) {
        val currentUser = auth.currentUser ?: return
        val docRef = firestore.collection("posts").document(post.postId)

        firestore.runTransaction { tx ->
            val snapshot = tx.get(docRef)
            val likedBy = (snapshot.get("likedBy") as? List<*>)?.mapNotNull { it as? String }
                ?: emptyList()
            val likeCount = snapshot.getLong("likeCount") ?: 0L

            val newLikedBy: List<String>
            val newLikeCount: Long

            if (likedBy.contains(currentUser.uid)) {
                newLikedBy = likedBy.filter { it != currentUser.uid }
                newLikeCount = (likeCount - 1).coerceAtLeast(0)
            } else {
                newLikedBy = likedBy + currentUser.uid
                newLikeCount = likeCount + 1
            }

            tx.update(docRef, mapOf(
                "likedBy" to newLikedBy,
                "likeCount" to newLikeCount
            ))
        }.addOnFailureListener { e ->
            Log.e(TAG, "toggleLike: transaction failed", e)
        }
    }

    /**
     * Delete post (and optionally, its images).
     * IMPORTANT: should only be called if currentUser.uid == post.uid
     */
    fun deletePost(
        post: Post,
        onResult: (Boolean) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != post.uid) {
            onResult(false)
            return
        }

        val docRef = firestore.collection("posts").document(post.postId)
        docRef.delete()
            .addOnSuccessListener {
                // Fire-and-forget delete of images (if any)
                val folderRef = storage.reference
                    .child("posts")
                    .child(post.postId)

                folderRef.listAll()
                    .addOnSuccessListener { listResult ->
                        listResult.items.forEach { item ->
                            item.delete()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "deletePost: failed to delete some images", e)
                    }

                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "deletePost: failed to delete post", e)
                onResult(false)
            }
    }
}
