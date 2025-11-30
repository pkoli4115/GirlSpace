package com.girlspace.app.ui.chat.audio

import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VoiceRecorder(
    private val outputFile: File
) {
    private var recorder: MediaRecorder? = null

    fun start() {
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "start failed", e)
        }
    }

    suspend fun stop(): Long? = withContext(Dispatchers.IO) {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            return@withContext getDuration(outputFile)
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "stop failed", e)
            return@withContext null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile.delete()
        } catch (_: Exception) { }
    }

    private fun getDuration(file: File): Long {
        return try {
            android.media.MediaMetadataRetriever().run {
                setDataSource(file.absolutePath)
                val time = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                release()
                time?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
