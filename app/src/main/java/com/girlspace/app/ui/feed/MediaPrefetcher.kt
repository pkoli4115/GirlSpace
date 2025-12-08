package com.girlspace.app.ui.feed

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest

/**
 * Small helper to prefetch images (post images, reel thumbnails).
 * Keeps implementation simple and memory-safe.
 */
object MediaPrefetcher {

    private const val MAX_PREFETCH = 10

    fun prefetchImages(
        context: Context,
        urls: List<String>
    ) {
        if (urls.isEmpty()) return

        val appContext = context.applicationContext
        val distinctUrls = urls.distinct().take(MAX_PREFETCH)
        val imageLoader = ImageLoader.Builder(appContext).build()

        distinctUrls.forEach { url ->
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .allowHardware(false)
                .build()

            imageLoader.enqueue(request)
        }
    }
}
