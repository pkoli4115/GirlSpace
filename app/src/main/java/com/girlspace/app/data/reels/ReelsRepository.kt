package com.girlspace.app.data.reels

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
        val nextCursorId: String?
    )

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
            // composite cursor for stable paging
            q = q.startAfter(cursorCreatedAt, cursorId)
        }

        val snap = q.get().await()
        val reels = snap.documents.mapNotNull { it.toObject(Reel::class.java)?.copy(id = it.id) }

        val last = snap.documents.lastOrNull()
        val lastCreated = last?.getTimestamp("createdAt")
        val lastId = last?.id

        return Page(reels, lastCreated, lastId)
    }

    suspend fun loadTopReels(limit: Long = 15): List<Reel> {
        val snap = reelsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()

        return snap.documents.mapNotNull { it.toObject(Reel::class.java)?.copy(id = it.id) }
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

                    tx.set(notifRef, mapOf(
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
                    ))
                }
            }
            null
        }.await()
    }

    // ---------- Comments ----------
    suspend fun addComment(reelId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val authorName = auth.currentUser?.displayName ?: "User"

        val reelRef = reelsCol.document(reelId)
        val commentRef = reelRef.collection("comments").document()

        firestore.runTransaction { tx ->
            val reelSnap = tx.get(reelRef)
            val ownerId = reelSnap.getString("authorId") ?: ""

            tx.set(commentRef, mapOf(
                "id" to commentRef.id,
                "reelId" to reelId,
                "authorId" to uid,
                "authorName" to authorName,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            // notify owner
            if (ownerId.isNotBlank() && ownerId != uid) {
                val notifRef = firestore.collection("users")
                    .document(ownerId)
                    .collection("notifications")
                    .document()

                tx.set(notifRef, mapOf(
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
                ))
            }
            null
        }.await()
    }

    suspend fun loadCommentsPage(reelId: String, pageSize: Long, cursor: Timestamp?): Pair<List<ReelComment>, Timestamp?> {
        var q = reelsCol.document(reelId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)

        if (cursor != null) q = q.startAfter(cursor)

        val snap = q.get().await()
        val list = snap.documents.mapNotNull { it.toObject(ReelComment::class.java)?.copy(id = it.id) }
        val next = snap.documents.lastOrNull()?.getTimestamp("createdAt")
        return list to next
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

            tx.set(shareRef, mapOf(
                "id" to shareRef.id,
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            ))
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
        if (info.durationSec !in 20..60) throw IllegalArgumentException("Reels must be 20–60 seconds.")
        // You asked vertical-only for seeded; for user uploads we allow but can enforce:
        // if (info.height <= info.width) throw IllegalArgumentException("Please upload a vertical video.")

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
        docRef.set(mapOf(
            "videoUrl" to videoUrl,
            "thumbnailUrl" to thumbUrl,
            "durationSec" to info.durationSec,
            "caption" to caption,
            "authorId" to uid,
            "authorName" to authorName,
            "visibility" to visibility,
            "tags" to tags,
            "createdAt" to FieldValue.serverTimestamp(),
            "source" to mapOf("provider" to "user_upload"),
            "metrics" to mapOf("views" to 0, "likes" to 0, "shares" to 0)
        )).await()

        return reelId
    }
}
