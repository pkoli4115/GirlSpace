package com.girlspace.app.moderation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

data class ImageModerationResult(
    val isSafe: Boolean,
    val flaggedLabels: List<String> = emptyList()
)

/**
 * Simple on-device image moderation using ML Kit Image Labeling.
 * We approximate "nudity / violence" by checking suspicious labels.
 */
class ImageModerator {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.DEFAULT_OPTIONS
        )
    }

    // Keywords that indicate possible nudity/violence etc.
    private val unsafeKeywords = listOf(
        // Possible nudity / sexual context
        "underwear", "bikini", "lingerie", "bra", "panties",
        "nude", "nudity", "naked",

        // Weapons / violence
        "gun", "handgun", "weapon", "knife", "sword",
        "blood", "violence", "fight", "fighting", "gore"
    )

    /**
     * Check a single bitmap. Returns first-level decision.
     */
    suspend fun checkBitmap(bitmap: Bitmap): ImageModerationResult {
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = try {
            labeler.process(image).await()
        } catch (e: Exception) {
            // If ML Kit fails, we treat as safe (you can flip this if you want fail-closed)
            return ImageModerationResult(isSafe = true)
        }

        val flagged = labels
            .filter { it.confidence >= 0.7f } // high-confidence labels only
            .map { it.text.lowercase() }
            .filter { labelText ->
                unsafeKeywords.any { kw -> labelText.contains(kw) }
            }

        return if (flagged.isEmpty()) {
            ImageModerationResult(isSafe = true)
        } else {
            ImageModerationResult(
                isSafe = false,
                flaggedLabels = flagged.distinct()
            )
        }
    }

    /**
     * Check multiple bitmaps. If any one is unsafe, result is unsafe.
     */
    suspend fun checkBitmaps(bitmaps: List<Bitmap>): ImageModerationResult {
        if (bitmaps.isEmpty()) {
            return ImageModerationResult(isSafe = true)
        }

        val allFlagged = mutableSetOf<String>()

        for (bmp in bitmaps) {
            val result = checkBitmap(bmp)
            if (!result.isSafe) {
                allFlagged += result.flaggedLabels
            }
        }

        return if (allFlagged.isEmpty()) {
            ImageModerationResult(isSafe = true)
        } else {
            ImageModerationResult(
                isSafe = false,
                flaggedLabels = allFlagged.toList()
            )
        }
    }
}
