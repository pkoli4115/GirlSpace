package com.girlspace.app.ui.video
import androidx.media3.datasource.BuildConfig
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
object VideoCache {
    private const val TAG = "VideoCache"

    // 250MB disk cache
    private const val CACHE_SIZE_BYTES = 250L * 1024L * 1024L

    // Prefetch limit per neighbor (bytes). Keep small to avoid blowing bandwidth.
    // 1.5MB is usually enough to get first-frame fast for faststart mp4.
    private const val PREFETCH_BYTES_DEFAULT = 5_000_000L // 5MB

    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val dir = File(context.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
                SimpleCache(dir, evictor).also { cache = it }
            }
        }
    }

    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)

        val upstream = DefaultDataSource.Factory(context, http)

        // Important:
        // - This factory will READ from cache if available
        // - And will WRITE to cache as the player downloads
        return CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Byte-level prefetch into SimpleCache.
     * This does NOT start playback. It just downloads first N bytes (range) into cache.
     *
     * Returns a cancellation handle you can call to stop quickly on fast swipes.
     */
    fun prefetch(
        context: Context,
        url: String,
        bytes: Long = PREFETCH_BYTES_DEFAULT,
        onHttpError: ((code: Int, message: String) -> Unit)? = null,
        onDone: ((downloadedBytes: Long) -> Unit)? = null
    ): PrefetchHandle {
        if (url.isBlank()) return PrefetchHandle.NOOP

        val cancelFlag = AtomicBoolean(false)
        val dsFactory = cacheDataSourceFactory(context)

        val dataSource = dsFactory.createDataSource()

        // Request only [0, bytes) to avoid huge traffic.
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(0L)
            .setLength(bytes)
            .build()

        val progress = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
            // bytesCached is how many bytes got cached for this request
            if (cancelFlag.get()) throw InterruptedException("Prefetch cancelled")
            if (BuildConfig.DEBUG) {
                VideoDebugStats.onPrefetchProgress(url, requestLength, bytesCached)
            }
        }

        val writer = CacheWriter(
            dataSource,
            dataSpec,
            /* temporaryBuffer= */ ByteArray(64 * 1024),
            progress
        )

        val t = Thread {
            try {
                if (BuildConfig.DEBUG) VideoDebugStats.onPrefetchStart(url, bytes)
                writer.cache()

                // CacheWriter doesn't directly tell final bytes; we track via progress stats.
                if (BuildConfig.DEBUG) VideoDebugStats.onPrefetchDone(url)
                onDone?.invoke(bytes)
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (BuildConfig.DEBUG) VideoDebugStats.onHttpError(url, e.responseCode)
                onHttpError?.invoke(e.responseCode, e.message ?: "HTTP ${e.responseCode}")
                logHttpDenied(url, e)
            } catch (e: Exception) {
                // Cancellation or other network errors
                if (BuildConfig.DEBUG) VideoDebugStats.onPrefetchFailed(url, e)
                if (BuildConfig.DEBUG) Log.d(TAG, "Prefetch failed for $url : ${e::class.java.simpleName} ${e.message}")
            } finally {
                try { dataSource.close() } catch (_: Throwable) {}
            }
        }.apply { name = "ReelsPrefetch" }

        t.start()

        return PrefetchHandle(
            cancel = {
                cancelFlag.set(true)
                runCatching { t.interrupt() }
            }
        )
    }

    private fun logHttpDenied(url: String, e: HttpDataSource.InvalidResponseCodeException) {
        // 401/403 are the typical "Denied"
        val code = e.responseCode
        if (code == 401 || code == 403) {
            Log.w(TAG, "HTTP denied for url=$url code=$code headers=${e.headerFields}")
        } else {
            Log.w(TAG, "HTTP error for url=$url code=$code")
        }
    }

    data class PrefetchHandle(val cancel: () -> Unit) {
        companion object {
            val NOOP = PrefetchHandle(cancel = {})
        }
    }
}
