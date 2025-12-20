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
    onRecordVideo: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = onRecordVideo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record Video (System Camera)")
            }

            Button(
                onClick = onTakePhoto,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Photo (System Camera)")
            }

            Button(
                onClick = onPickFromGallery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick from Gallery")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Rules: 20s–180s allowed. ≤60s → Reel, 61–180s → Video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
