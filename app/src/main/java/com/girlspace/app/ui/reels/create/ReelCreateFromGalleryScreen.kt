package com.girlspace.app.ui.reels.create
import com.girlspace.app.ui.reels.ReelsRefreshBus
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.girlspace.app.data.reels.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun upload(
        context: android.content.Context,
        uri: Uri,
        caption: String,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                isBusy = true
                error = null
                val reelId = repo.uploadReel(
                    context = context,
                    videoUri = uri,
                    caption = caption,
                    tags = emptyList()
                )

// 🔥 THIS LINE MAKES IT APPEAR IN REELS TAB
                ReelsRefreshBus.notifyRefresh()

                onDone(reelId)
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

    LaunchedEffect(uri) {
        vm.loadInfo(context, uri)
    }

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
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("Back") }
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
                maxLines = 3
            )

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

            val canSubmit = (dur != null && dur in 20..180) && !vm.isBusy
            Button(
                onClick = {
                    vm.upload(
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
                if (vm.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Uploading…")
                } else {
                    Text("Upload")
                }
            }
        }
    }
}
