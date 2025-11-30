package com.girlspace.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AttachmentMenuSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onAudio: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Attach",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                AttachmentIcon("Camera", onCamera)
                AttachmentIcon("Gallery", onGallery)
                AttachmentIcon("File", onFile)
                AttachmentIcon("Audio", onAudio)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun AttachmentIcon(text: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )

        Spacer(Modifier.height(8.dp))
        Text(text)
    }
}
