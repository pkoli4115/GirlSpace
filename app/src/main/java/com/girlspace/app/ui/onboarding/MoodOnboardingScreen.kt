package com.girlspace.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

@Composable
fun MoodOnboardingScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    // themeMode holds the selected vibe key: "serenity", "radiance", etc.
    val selectedThemeKey by viewModel.themeMode.collectAsState()

    val themeOptions = vibeThemeOptions()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(
                    text = "What’s your vibe?",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose a theme you like. You can always change this later from Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Two cards per row
                val rows = themeOptions.chunked(2)
                rows.forEachIndexed { index, row ->
                    VibeRow(
                        options = row,
                        selectedKey = selectedThemeKey,
                        onSelect = { key ->
                            viewModel.saveThemeMode(key)
                        }
                    )
                    if (index != rows.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Bottom row: only "Next →" aligned to the end
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        // 1) Mark onboarding done in local prefs
                        viewModel.saveFirstLaunchDone()

                        // 2) Mark per-user flag in Firestore
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .set(
                                    mapOf("hasVibe" to true),
                                    SetOptions.merge()
                                )
                        }

                        // 3) Navigate to Home
                        onNext()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Next →",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private data class VibeOption(
    val key: String,         // "serenity", "radiance", etc. (stored in prefs.themeMode)
    val title: String,       // Vibe name
    val description: String, // Emotional tone + ideal for
    val previewColor: Color  // Color dot
)

/**
 * Vibes:
 * Serenity  | Calm Blue       | Calm, trust, clarity – networking, community, reflection
 * Radiance  | Sunset Glow     | Creative, vibrant, expressive – visual storytelling, expression
 * Wisdom    | Deep Blue       | Professional, focused, grounded – careers, creators, learning
 * Pulse     | Midnight Dark   | Bold, edgy, energetic – trends, entertainment, youth culture
 * Harmony   | Fresh Green     | Connection, growth, balance – messaging, communities, rituals
 * Ignite    | Energy Red      | Passion, excitement, momentum – video-first creators, inspiration
 */
private fun vibeThemeOptions(): List<VibeOption> = listOf(
    VibeOption(
        key = "serenity",
        title = "Serenity",
        description = "Calm, trust, clarity – for networking, community & reflection.",
        previewColor = Color(0xFF1877F2) // calm blue
    ),
    VibeOption(
        key = "radiance",
        title = "Radiance",
        description = "Creative, vibrant, expressive – for visual storytelling & self-expression.",
        previewColor = Color(0xFFF77737) // warm gradient-like orange
    ),
    VibeOption(
        key = "wisdom",
        title = "Wisdom",
        description = "Professional, focused, grounded – for careers, creators & learning.",
        previewColor = Color(0xFF0A66C2) // deep pro blue
    ),
    VibeOption(
        key = "pulse",
        title = "Pulse",
        description = "Bold, edgy, energetic – for trends, entertainment & youth culture.",
        previewColor = Color(0xFF111827) // near-black
    ),
    VibeOption(
        key = "harmony",
        title = "Harmony",
        description = "Connection, growth, balance – for messaging, communities & rituals.",
        previewColor = Color(0xFF25D366) // fresh green
    ),
    VibeOption(
        key = "ignite",
        title = "Ignite",
        description = "Passion, excitement, momentum – for video-first creators & inspiration.",
        previewColor = Color(0xFFFF0000) // strong red
    )
)

@Composable
private fun VibeRow(
    options: List<VibeOption>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.key == selectedKey
            val bg = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val content = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = bg,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(option.key) }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Colored circle symbol for the vibe
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = option.previewColor,
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = option.title,
                    color = content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = option.description,
                    color = content.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
