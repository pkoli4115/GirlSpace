package com.girlspace.app.ui.reels

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun ReelsTabScreen(
    navController: NavHostController
) {
    val vm: ReelsViewModel = hiltViewModel()
    val reels by vm.reels.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val uploadState by vm.uploadState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadInitial() }

    var showUpload by remember { mutableStateOf(false) }

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            showUpload = true
            // quick default caption; user edits below
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Open viewer from first reel (or keep it as a list later)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (reels.isEmpty() && isLoading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = {
                    val startId = reels.firstOrNull()?.id ?: ""
                    navController.navigate("reelsViewer/$startId")
                }) { Text("Open Reels") }
            }
        }

        FloatingActionButton(
            onClick = { pickVideo.launch("video/*") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Upload reel") }
    }

    // Simple upload dialog (caption + tags)
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) { onDispose { } }

    // use last picked uri by reading Activity result again:
    // easiest: if you want strict handling, tell me; for now keep minimal.

    if (showUpload) {
        UploadReelDialog(
            onDismiss = { showUpload = false },
            onUpload = { uri, caption, tags ->
                pendingUri = uri
                vm.uploadReel(context, uri, caption, tags)
            }
        )
    }

    when (val s = uploadState) {
        is ReelsViewModel.UploadState.Done -> {
            LaunchedEffect(s.reelId) {
                showUpload = false
                navController.navigate("reelsViewer/${s.reelId}")
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadReelDialog(
    onDismiss: () -> Unit,
    onUpload: (Uri, String, List<String>) -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val uri = pickedUri ?: return@TextButton
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onUpload(uri, caption, tags)
                }
            ) { Text("Upload") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Upload Reel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { pick.launch("video/*") }) { Text("Pick video") }
                if (pickedUri != null) Text("Selected: ${pickedUri.toString().take(50)}…")

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    maxLines = 2
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    maxLines = 1
                )
                Text(
                    text = "Rules: 20–60 seconds. Uploads go to Firebase Storage.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
