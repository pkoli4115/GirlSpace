package com.girlspace.app.ui.reels.create

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girlspace.app.ui.reels.ReelsRefreshBus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelYoutubeScreen(
    onBack: () -> Unit,
    prefillUrl: String? = null
) {
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    // ✅ Prefill from deep link/share
    LaunchedEffect(prefillUrl) {
        val p = prefillUrl?.trim().orEmpty()
        if (p.isNotBlank() && urlText.isBlank()) {
            urlText = p
        }
    }

    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    val youtubeId = remember(urlText) { extractYoutubeVideoId(urlText.trim()) }
    val isValid = youtubeId != null

    fun submit() {
        if (!isValid || isBusy) return
        scope.launch {
            isBusy = true
            error = null
            success = false
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid.isNullOrBlank()) {
                    error = "Please login again."
                    return@launch
                }

                val cleanedUrl = urlText.trim()
                val videoId = youtubeId!!
                val thumb = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                val authorName = FirebaseAuth.getInstance().currentUser?.displayName
                    ?: FirebaseAuth.getInstance().currentUser?.email
                    ?: "User"

                val data = hashMapOf(
                    "caption" to caption.trim(),
                    "videoUrl" to "", // link-out / embedded only
                    "thumbnailUrl" to thumb,
                    "durationSec" to 0,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp(),

                    // 🔑 ADD THIS
                    "youtubeVideoId" to videoId,

                    "source" to hashMapOf(
                        "provider" to "youtube_url",
                        "youtubeUrl" to cleanedUrl,
                        "youtubeVideoId" to videoId
                    )

                )

                val docRef = FirebaseFirestore.getInstance()
                    .collection("reels")
                    .add(data)
                    .await()

                ReelsRefreshBus.notifyRefresh()
                success = true
            } catch (t: Throwable) {
                error = t.message ?: "Failed to create YouTube reel."
            } finally {
                isBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add YouTube URL") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (!isBusy) onBack() }
                    ) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Link-out only (no playback inside Togetherly).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    if (error != null) error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube URL") },
                placeholder = { Text("https://youtube.com/watch?v=... or https://youtu.be/...") },
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )

            if (urlText.isNotBlank() && !isValid) {
                Text(
                    text = "Please enter a valid YouTube link.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Caption (optional)") },
                enabled = !isBusy,
                maxLines = 3
            )

            error?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { error = null }) { Text("OK") }
                    }
                }
            }

            if (success) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Added ✅", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "This will appear in Reels as a YouTube link-out.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(onClick = onBack, enabled = !isBusy) { Text("Done") }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { submit() },
                enabled = isValid && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isBusy) "Creating…" else "Submit")
            }
        }
    }
}

/**
 * Accepts:
 * - https://www.youtube.com/watch?v=VIDEOID
 * - https://youtu.be/VIDEOID
 * - https://www.youtube.com/shorts/VIDEOID
 */
private fun extractYoutubeVideoId(input: String): String? {
    if (input.isBlank()) return null
    return try {
        val uri = Uri.parse(input)
        val host = (uri.host ?: "").lowercase()
        when {
            host.contains("youtu.be") -> {
                uri.pathSegments.firstOrNull()?.takeIf { it.length in 8..20 }
            }
            host.contains("youtube.com") -> {
                val v = uri.getQueryParameter("v")
                if (!v.isNullOrBlank() && v.length in 8..20) return v
                val segments = uri.pathSegments
                val shortsIdx = segments.indexOfFirst { it.equals("shorts", ignoreCase = true) }
                if (shortsIdx >= 0) {
                    segments.getOrNull(shortsIdx + 1)?.takeIf { it.length in 8..20 }
                } else null
            }
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

}
