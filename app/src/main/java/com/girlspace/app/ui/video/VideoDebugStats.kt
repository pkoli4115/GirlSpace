package com.girlspace.app.ui.video

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only counters so we can validate improvements:
 * - prepares per swipe
 * - prefetch bytes
 * - HTTP errors (401/403/404 etc)
 *
 * No UI. No prod analytics. Just Logcat.
 */
object VideoDebugStats {
    private const val TAG = "ReelsStats"

    private val prepareCalls = AtomicLong(0)
    private val playbackStartCalls = AtomicLong(0)

    private val httpErrors = ConcurrentHashMap<Int, AtomicLong>()
    private val failedUrls = ConcurrentHashMap<String, AtomicLong>()

    // track prefetch totals per URL (best effort)
    private val prefetchRequested = ConcurrentHashMap<String, Long>()
    private val prefetchCached = ConcurrentHashMap<String, AtomicLong>()

    fun onPrepareCalled() {
        prepareCalls.incrementAndGet()
    }

    fun onRequestPlayCalled() {
        playbackStartCalls.incrementAndGet()
    }

    fun onHttpError(url: String, code: Int) {
        httpErrors.getOrPut(code) { AtomicLong(0) }.incrementAndGet()
        failedUrls.getOrPut(url) { AtomicLong(0) }.incrementAndGet()
        Log.w(TAG, "HTTP error code=$code url=$url")
    }

    fun onPrefetchStart(url: String, requestedBytes: Long) {
        prefetchRequested[url] = requestedBytes
        prefetchCached.getOrPut(url) { AtomicLong(0) }.set(0)
        Log.d(TAG, "Prefetch start url=$url bytes=$requestedBytes")
    }

    fun onPrefetchProgress(url: String, requestLength: Long, bytesCached: Long) {
        prefetchCached.getOrPut(url) { AtomicLong(0) }.set(bytesCached)
        // Keep logs light
    }

    fun onPrefetchDone(url: String) {
        val req = prefetchRequested[url] ?: 0L
        val got = prefetchCached[url]?.get() ?: 0L
        Log.d(TAG, "Prefetch done url=$url requested=$req cached=$got")
    }

    fun onPrefetchFailed(url: String, t: Throwable) {
        failedUrls.getOrPut(url) { AtomicLong(0) }.incrementAndGet()
        Log.d(TAG, "Prefetch failed url=$url err=${t::class.java.simpleName}:${t.message}")
    }

    fun dump(tagSuffix: String = "") {
        val prepares = prepareCalls.get()
        val plays = playbackStartCalls.get()

        val http = httpErrors.entries
            .sortedBy { it.key }
            .joinToString { (code, cnt) -> "$code=${cnt.get()}" }

        Log.i(TAG, "DUMP$tagSuffix prepares=$prepares requestPlay=$plays httpErrors={$http}")
    }

    fun isKnownBadUrl(url: String): Boolean {
        // If failed 2+ times this session, treat as bad to avoid storms.
        return (failedUrls[url]?.get() ?: 0L) >= 2L
    }
}
