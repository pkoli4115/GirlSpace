package com.girlspace.app.ui.reels.create

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.girlspace.app.ui.reels.ReelsRefreshBus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCaptureScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Record (Camera)") }) }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onBack) { Text("Back") }
            ReelsRefreshBus.notifyRefresh()

        }
    }
}
