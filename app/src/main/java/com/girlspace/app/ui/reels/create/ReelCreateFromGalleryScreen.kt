package com.girlspace.app.ui.reels.create

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.ReelsRepository
import com.girlspace.app.ui.reels.ReelsRefreshBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelCreateViewModel @Inject constructor(
    private val repo: ReelsRepository
) : ViewModel() {

    var isBusy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var durationSec by mutableStateOf<Int?>(null)
        private set

    // Progress UI state
    var stage by mutableStateOf("")
        private set
    var progress by mutableStateOf(0f)
        private set
    var uploadedReelId by mutableStateOf<String?>(null)
        private set

    fun clearError() { error = null }

    fun loadInfo(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            try {
                error = null
                val info = repo.readVideoInfo(context, uri)
                durationSec = info.durationSec
            } catch (t: Throwable) {
                error = t.message ?: "Failed to read video"
            }
        }
    }

    fun progressColor(p: Float): Color = when {
        p < 0.4f -> Color(0xFFE53935) // red
        p < 0.8f -> Color(0xFFFFA000) // amber
        else -> Color(0xFF43A047)     // green
    }

    fun uploadWithProgress(
        context: android.content.Context,
        uri: Uri,
        caption: String,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                isBusy = true
                error = null
                uploadedReelId = null
                stage = "Starting…"
                progress = 0f

                repo.uploadReelWithProgress(
                    context = context,
                    videoUri = uri,
                    caption = caption,
                    tags = emptyList(),
                    visibility = "PUBLIC"
                ).collect { u ->
                    stage = u.stage
                    progress = u.progress.coerceIn(0f, 1f)

                    if (!u.error.isNullOrBlank()) {
                        error = u.error
                    }

                    if (u.done && !u.reelId.isNullOrBlank()) {
                        uploadedReelId = u.reelId
                        progress = 1f

                        // 🔥 Make it appear in Reels tab immediately
                        ReelsRefreshBus.notifyRefresh()

                        // ✅ Let user SEE success for a moment
                        delay(900)

                        onDone(u.reelId)
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: "Upload failed"
            } finally {
                isBusy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCreateFromGalleryScreen(
    videoUriString: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val vm: ReelCreateViewModel = hiltViewModel()

    val uri = remember(videoUriString) { Uri.parse(Uri.decode(videoUriString)) }

    LaunchedEffect(uri) { vm.loadInfo(context, uri) }

    var caption by remember { mutableStateOf(TextFieldValue("")) }

    val dur = vm.durationSec
    val typeLabel = remember(dur) {
        when {
            dur == null -> ""
            dur <= 60 -> "Reel"
            else -> "Video"
        }
    }

    val ruleText = "Rules: 20s–180s allowed. <=60s shows as Reel, 61–180s shows as Video."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create") },
                navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }
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
            if (dur == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text("Duration: ${dur}s • Type: $typeLabel", style = MaterialTheme.typography.titleMedium)
            }

            Text(ruleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Caption") },
                maxLines = 3,
                enabled = !vm.isBusy
            )

            // ✅ Upload progress UI
            if (vm.isBusy) {
                val pct = (vm.progress * 100).toInt().coerceIn(0, 100)
                val c = vm.progressColor(vm.progress)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(vm.stage.ifBlank { "Uploading…" }, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { vm.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = c
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = c
                        )

                        if (!vm.uploadedReelId.isNullOrBlank()) {
                            Text("File uploaded successfully ✅", color = Color(0xFF43A047))
                            Text("It will now appear in Reels.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            vm.error?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.clearError() }) { Text("OK") }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val captionOk = caption.text.trim().isNotBlank()
            if (!captionOk) {
                Text(
                    text = "Caption is required.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            val canSubmit = (dur != null && dur in 2..180) && captionOk && !vm.isBusy
            Button(
                onClick = {
                    vm.uploadWithProgress(
                        context = context,
                        uri = uri,
                        caption = caption.text
                    ) { reelId ->
                        onCreated(reelId)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.isBusy) "Uploading…" else "Upload")
            }
        }
    }
}
