package com.girlspace.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/* ─────────────────────────────
   In-chat search bar (moved out of ChatScreenV2)
   ───────────────────────────── */

@Composable
fun InChatSearchBarV2(
    query: String,
    matchCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search in chat") },
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (matchCount > 0) {
            Text(
                text = "${currentIndex + 1}/$matchCount",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        TextButton(onClick = onPrev, enabled = matchCount > 0) {
            Text("Prev")
        }
        TextButton(onClick = onNext, enabled = matchCount > 0) {
            Text("Next")
        }
        TextButton(onClick = onClose) {
            Text("✕")
        }
    }
}
