package com.girlspace.app.ui.reels.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.girlspace.app.ui.reels.ReelsRefreshBus
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelGalleryPickScreen(
    navController: NavHostController,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = Uri.encode(uri.toString())
            navController.navigate("reelCreate/$encoded")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick a video") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { picker.launch("video/*") }) {
                Text("Choose video from gallery")
            }
        }
    }
}
