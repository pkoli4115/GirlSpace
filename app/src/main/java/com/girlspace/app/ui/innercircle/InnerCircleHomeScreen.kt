package com.girlspace.app.ui.innercircle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleHomeScreen(
    onBack: () -> Unit,
    onGoToAppHome: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inner Circle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Inner Circle is enabled for your account.")

            Text(
                "Next, we’ll route this into Inner Circle mode (Feed/Reels/Chat/Communities) with " +
                        "privacy protections like no ads, no sharing, and screenshot restriction."
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onGoToAppHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
