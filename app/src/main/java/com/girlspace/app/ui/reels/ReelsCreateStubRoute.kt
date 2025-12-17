package com.girlspace.app.ui.reels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Temporary safe route so Reels "Create" options never crash.
 * We'll replace this with real CameraX / Gallery picker / URL input in the next step.
 */
@Composable
fun ReelsCreateStubRoute(
    title: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "This create flow is wired correctly now (no crash). Next we’ll implement the full working flow.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onDone) { Text("Back") }
    }
}
