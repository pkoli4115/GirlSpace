package com.girlspace.app.ui.reels.create

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCaptureScreen(
    onBack: () -> Unit
) {
    // NOTE: Camera is intentionally parked. This screen must not crash or show blank UI.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record (Camera)") },
                navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Camera is temporarily disabled.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "We’ll add in-app camera later (design discussion).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
