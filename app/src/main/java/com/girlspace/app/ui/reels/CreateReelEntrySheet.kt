package com.girlspace.app.ui.reels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReelEntrySheet(
    onDismiss: () -> Unit,
    onRecord: () -> Unit,
    onPickFromGallery: () -> Unit,
    onAddYoutubeUrl: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Create", style = MaterialTheme.typography.titleMedium)

            Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) {
                Text("Record (Camera)")
            }
            Button(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
                Text("Pick from Gallery")
            }
            OutlinedButton(onClick = onAddYoutubeUrl, modifier = Modifier.fillMaxWidth()) {
                Text("Add YouTube URL (link-out)")
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Rules: 20s–180s allowed. <=60s shows as Reel, 61–180s shows as Video. Ads disabled (feature flag only).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
