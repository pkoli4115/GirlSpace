package com.girlspace.app.ui.reels.create
import com.girlspace.app.ui.reels.ReelsRefreshBus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelYoutubeScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Add YouTube URL") }) }
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
