package com.girlspace.app.data.reels
import com.google.firebase.storage.OnProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.WriteBatch

@Singleton
class ReelsRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private val reelsCol get() = firestore.collection("reels")

    // ---------- Paging ----------
    data class Page(
        val items: List<Reel>,
        val nextCursorCreatedAt: Timestamp?,
        val nextCursorId: String?,
        val likedByMeIds: Set<String> = emptySet()
    )

    data class CommentsCursor(
        val createdAt: Timestamp,
        val id: String
    )

    suspend fun getReelById(reelId: String): Reel? {
        val doc = reelsCol.document(reelId).get().await()
        return doc.toObject(Reel::class.java)?.copy(docId = doc.id)
    }
    /**
     * Delete a reel owned by the current user.
     * Deletes:
     * - subcollections: comments, likes, shares (best effort)
     * - firestore doc
     * - storage files: reels/{authorId}/{reelId}.mp4 and .jpg (best effort)
     */
    suspend fun deleteReel(reelId: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")

        val reelRef = reelsCol.document(reelId)
        val snap = reelRef.get().await()
        if (!snap.exists()) return

        val authorId = snap.getString("authorId") ?: ""
        if (authorId != uid) {
            throw SecurityException("You can delete only your own reels.")
        }

        // 1) Delete subcollections (best effort)
        runCatching { deleteSubcollection(reelRef, "comments") }
        runCatching { deleteSubcollection(reelRef, "likes") }
        runCatching { deleteSubcollection(reelRef, "shares") }

        // 2) Delete Firestore doc
        reelRef.delete().await()

        // 3) Delete storage objects (best effort)
        // We know exact paths because upload uses reels/$uid/$reelId.mp4 and .jpg
        val videoPath = "reels/$uid/$reelId.mp4"
        val thumbPath = "reels/$uid/$reelId.jpg"

        runCatching { storage.reference.child(videoPath).delete().await() }
        runCatching { storage.reference.child(thumbPath).delete().await() }
    }

    /**
     * Deletes a subcollection in batches to avoid limits.
     * Firestore client doesn't do recursive delete, so we page and batch-delete.
     */
    private suspend fun deleteSubcollection(
        parent: DocumentReference,
        subcollection: String,
        batchSize: Int = 100
    ) {
        while (true) {
            val snap = parent.collection(subcollection)
                .limit(batchSize.toLong())
                .get()
                .await()

            if (snap.isEmpty) break

            firestore.runBatch { batch: WriteBatch ->
                snap.documents.forEach { batch.delete(it.reference) }
            }.await()

            // loop until empty
        }
    }

    suspend fun incrementView(reelId: String) {
        val reelRef = reelsCol.document(reelId)
        firestore.runTransaction { tx ->
            val snap = tx.get(reelRef)
            val metrics = (snap.get("metrics") as? Map<*, *>) ?: emptyMap<Any, Any>()
            val current = (metrics["views"] as? Number)?.toLong() ?: 0L
            tx.update(reelRef, "metrics.views", current + 1)
            null
        }.await()
    }

    suspend fun loadPage(
        pageSize: Long,
        cursorCreatedAt: Timestamp?,
        cursorId: String?
    ): Page {
        var q = reelsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .orderBy("__name__", Query.Direction.DESCENDING)
            .limit(pageSize)

        if (cursorCreatedAt != null && !cursorId.isNullOrBlank()) {
            q = q.startAfter(cursorCreatedAt, cursorId)
        }

        val snap = q.get().await()
        val reels = snap.documents.mapNotNull { it.toObject(Reel::class.java)?.copy(docId = it.id) }

        val last = snap.documents.lastOrNull()
        val lastCreated = last?.getTimestamp("createdAt")
        val lastId = last?.id

        // ✅ also load liked-by-me state for this page (works on all SDKs; no getAll())
        val uid = auth.currentUser?.uid
        val likedIds: Set<String> = if (uid.isNullOrBlank() || reels.isEmpty()) {
            emptySet()
        } else {
            coroutineScope {
                reels.map { r ->
                    async {
                        val likeSnap = reelsCol.document(r.id)
                            .collection("likes")
                            .document(uid)
                            .get()
                            .await()
                        if (likeSnap.exists()) r.id else null
                    }
                }.awaitAll().filterNotNull().toSet()
            }
        }

        return Page(
            items = reels,
            nextCursorCreatedAt = lastCreated,
            nextCursorId = lastId,
            likedByMeIds = likedIds
        )
    }

    suspend fun loadTopReels(limit: Long = 15): List<Reel> {
        val snap = reelsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()

        return snap.documents.mapNotNull { it.toObject(Reel::class.java)?.copy(docId = it.id) }
    }

    // ---------- Likes ----------
    suspend fun toggleLike(reel: Reel) {
        val uid = auth.currentUser?.uid ?: return
        val reelRef = reelsCol.document(reel.id)
        val likeRef = reelRef.collection("likes").document(uid)

        firestore.runTransaction { tx ->
            val likeSnap = tx.get(likeRef)
            val reelSnap = tx.get(reelRef)
            val metrics = (reelSnap.get("metrics") as? Map<*, *>) ?: emptyMap<Any, Any>()
            val currentLikes = (metrics["likes"] as? Number)?.toLong() ?: 0L

            val authorId = reelSnap.getString("authorId") ?: ""

            if (likeSnap.exists()) {
                tx.delete(likeRef)
                tx.update(reelRef, "metrics.likes", (currentLikes - 1).coerceAtLeast(0))
            } else {
                tx.set(likeRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(reelRef, "metrics.likes", currentLikes + 1)

                // create notification (no push here; your app already reads /users/{uid}/notifications)
                if (authorId.isNotBlank() && authorId != uid) {
                    val notifRef = firestore.collection("users")
                        .document(authorId)
                        .collection("notifications")
                        .document()

                    tx.set(
                        notifRef,
                        mapOf(
                            "id" to notifRef.id,
                            "title" to (auth.currentUser?.displayName ?: "Someone"),
                            "body" to "liked your reel",
                            "category" to "REEL",
                            "type" to "reel_like",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "read" to false,
                            "senderId" to uid,
                            "entityId" to reelRef.id,
                            "deepLink" to "togetherly://reels/${reelRef.id}",
                            "importance" to "NORMAL"
                        )
                    )
                }
            }
            null
        }.await()
    }

    // ---------- Comments ----------
    /**
     * ✅ IMPORTANT:
     * - We DO NOT write "id" field in comment document.
     * - ReelComment uses @DocumentId to populate id.
     */
    suspend fun addComment(reelId: String, text: String): ReelComment {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
        val authorName = auth.currentUser?.displayName ?: "User"

        val reelRef = reelsCol.document(reelId)
        val commentRef = reelRef.collection("comments").document()

        firestore.runTransaction { tx ->
            val reelSnap = tx.get(reelRef)
            val ownerId = reelSnap.getString("authorId") ?: ""

            // ✅ NO "id" FIELD HERE
            tx.set(
                commentRef,
                mapOf(
                    "reelId" to reelId,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "text" to text,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            // notify owner
            if (ownerId.isNotBlank() && ownerId != uid) {
                val notifRef = firestore.collection("users")
                    .document(ownerId)
                    .collection("notifications")
                    .document()

                tx.set(
                    notifRef,
                    mapOf(
                        "id" to notifRef.id,
                        "title" to authorName,
                        "body" to "commented: ${text.take(80)}",
                        "category" to "REEL",
                        "type" to "reel_comment",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "read" to false,
                        "senderId" to uid,
                        "entityId" to reelId,
                        "deepLink" to "togetherly://reels/$reelId",
                        "importance" to "NORMAL"
                    )
                )
            }
            null
        }.await()

        // Return a usable local object for immediate UI insert
        return ReelComment(
            id = commentRef.id,
            reelId = reelId,
            authorId = uid,
            authorName = authorName,
            text = text,
            createdAt = Timestamp.now()
        )
    }

    suspend fun loadCommentsPage(
        reelId: String,
        pageSize: Long,
        cursor: CommentsCursor?
    ): Pair<List<ReelComment>, CommentsCursor?> {
        var q = reelsCol.document(reelId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .orderBy("__name__", Query.Direction.DESCENDING)
            .limit(pageSize)

        if (cursor != null) {
            q = q.startAfter(cursor.createdAt, cursor.id)
        }

        val snap = q.get().await()
        val list = snap.documents.mapNotNull { it.toObject(ReelComment::class.java) }

        val last = snap.documents.lastOrNull()
        val lastCreated = last?.getTimestamp("createdAt")
        val lastId = last?.id

        val nextCursor = if (lastCreated != null && !lastId.isNullOrBlank()) {
            CommentsCursor(createdAt = lastCreated, id = lastId)
        } else null

        return list to nextCursor
    }

    // ---------- Shares ----------
    suspend fun logShare(reelId: String) {
        val uid = auth.currentUser?.uid ?: return
        val reelRef = reelsCol.document(reelId)
        val shareRef = reelRef.collection("shares").document()

        firestore.runTransaction { tx ->
            val reelSnap = tx.get(reelRef)
            val metrics = (reelSnap.get("metrics") as? Map<*, *>) ?: emptyMap<Any, Any>()
            val currentShares = (metrics["shares"] as? Number)?.toLong() ?: 0L

            tx.set(
                shareRef,
                mapOf(
                    "id" to shareRef.id,
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            tx.update(reelRef, "metrics.shares", currentShares + 1)
            null
        }.await()
    }

    // ---------- Upload (user reels) ----------
    data class VideoInfo(val durationSec: Int, val width: Int, val height: Int)

    suspend fun readVideoInfo(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val durMs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            val w = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0)
            val h = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
            VideoInfo(durationSec = (durMs / 1000L).toInt(), width = w, height = h)
        } finally {
            try { r.release() } catch (_: Throwable) {}
        }
    }

    suspend fun generateThumbnailJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val bmp: Bitmap = r.getFrameAtTime(0) ?: throw IllegalStateException("No frame")
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } finally {
            try { r.release() } catch (_: Throwable) {}
        }
    }
    data class UploadProgress(
        val stage: String,
        val progress: Float,        // 0f..1f
        val done: Boolean = false,
        val reelId: String? = null,
        val error: String? = null
    )

    fun uploadReelWithProgress(
        context: Context,
        videoUri: Uri,
        caption: String,
        tags: List<String>,
        visibility: String = "PUBLIC"
    ): Flow<UploadProgress> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            trySend(UploadProgress("Error", 0f, error = "Not logged in"))
            close()
            return@callbackFlow
        }

        val authorName = auth.currentUser?.displayName ?: "User"

        try {
            val info = readVideoInfo(context, videoUri)
            if (info.durationSec !in 20..180) {
                trySend(UploadProgress("Error", 0f, error = "Video must be between 20 and 180 seconds."))
                close()
                return@callbackFlow
            }

            val reelId = UUID.randomUUID().toString()
            val videoRef = storage.reference.child("reels/$uid/$reelId.mp4")
            val thumbRef = storage.reference.child("reels/$uid/$reelId.jpg")

            // -------------------------
            // Stage 1: Video upload (0..0.75)
            // -------------------------
            trySend(UploadProgress("Uploading video", 0f))

            val videoTask: UploadTask = videoRef.putFile(videoUri)

            val videoListener = OnProgressListener<UploadTask.TaskSnapshot> { snap: UploadTask.TaskSnapshot ->
                val total = snap.totalByteCount
                val transferred = snap.bytesTransferred
                val pct = if (total > 0L) transferred.toFloat() / total.toFloat() else 0f
                trySend(UploadProgress("Uploading video", (pct * 0.75f).coerceIn(0f, 0.75f)))
            }

            videoTask.addOnProgressListener(videoListener)
            videoTask.await()
            videoTask.removeOnProgressListener(videoListener)

            val videoUrl = videoRef.downloadUrl.await().toString()

            // -------------------------
            // Stage 2: Thumbnail upload (0.75..0.92)
            // -------------------------
            trySend(UploadProgress("Uploading thumbnail", 0.76f))

            val thumbBytes = generateThumbnailJpeg(context, videoUri)
            val thumbTask = thumbRef.putBytes(thumbBytes)

            val thumbListener = OnProgressListener<UploadTask.TaskSnapshot> { snap: UploadTask.TaskSnapshot ->
                val total = snap.totalByteCount
                val transferred = snap.bytesTransferred
                val pct = if (total > 0L) transferred.toFloat() / total.toFloat() else 0f
                val mapped = 0.75f + (pct * 0.17f)
                trySend(UploadProgress("Uploading thumbnail", mapped.coerceIn(0.75f, 0.92f)))
            }

            thumbTask.addOnProgressListener(thumbListener)
            thumbTask.await()
            thumbTask.removeOnProgressListener(thumbListener)

            val thumbUrl = thumbRef.downloadUrl.await().toString()

            // -------------------------
            // Stage 3: Save Firestore (0.92..1.0)
            // -------------------------
            trySend(UploadProgress("Saving reel", 0.93f))

            reelsCol.document(reelId).set(
                mapOf(
                    "videoUrl" to videoUrl,
                    "thumbnailUrl" to thumbUrl,
                    "durationSec" to info.durationSec,
                    "caption" to caption,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "visibility" to visibility,
                    "tags" to tags,
                    "createdAt" to Timestamp.now(),
                    "createdAtServer" to FieldValue.serverTimestamp(),
                    "source" to mapOf("provider" to "user_upload"),
                    "metrics" to mapOf("views" to 0, "likes" to 0, "shares" to 0)
                )
            ).await()

            trySend(UploadProgress("File uploaded successfully ✅", 1f, done = true, reelId = reelId))
            close()
        } catch (t: Throwable) {
            trySend(UploadProgress("Upload failed", 0f, error = t.message ?: "Unknown error"))
            close()
        }

        awaitClose { }
    }
    suspend fun uploadReel(
        context: Context,
        videoUri: Uri,
        caption: String,
        tags: List<String>,
        visibility: String = "PUBLIC"
    ): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
        val authorName = auth.currentUser?.displayName ?: "User"

        val info = readVideoInfo(context, videoUri)

        // ✅ Locked rules
        if (info.durationSec !in 2..180) {
            throw IllegalArgumentException("Video must be between 2 and 180 seconds.")
        }


        val reelId = UUID.randomUUID().toString()
        val videoRef = storage.reference.child("reels/$uid/$reelId.mp4")
        val thumbRef = storage.reference.child("reels/$uid/$reelId.jpg")

        // upload video
        videoRef.putFile(videoUri).await()
        val videoUrl = videoRef.downloadUrl.await().toString()

        // upload thumbnail
        val thumbBytes = generateThumbnailJpeg(context, videoUri)
        thumbRef.putBytes(thumbBytes).await()
        val thumbUrl = thumbRef.downloadUrl.await().toString()

        // create doc
        val docRef = reelsCol.document(reelId)
        docRef.set(
            mapOf(
                "videoUrl" to videoUrl,
                "thumbnailUrl" to thumbUrl,
                "durationSec" to info.durationSec,
                "caption" to caption,
                "authorId" to uid,
                "authorName" to authorName,
                "visibility" to visibility,
                "tags" to tags,
                "createdAt" to FieldValue.serverTimestamp(),

                // ✅ keep your existing model contract
                "source" to mapOf("provider" to "user_upload"),
                "metrics" to mapOf("views" to 0, "likes" to 0, "shares" to 0)
            )
        ).await()

        return reelId
    }
}
