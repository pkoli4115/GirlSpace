package com.girlspace.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoodOnboardingScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit = {}
) {
    // Read current mood from DataStore, default "calm"
    val selectedMoodFromStore by viewModel.mood.collectAsState(initial = "calm")
    val selectedMood = remember(selectedMoodFromStore) { selectedMoodFromStore }

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
                    text = "What’s your vibe today?",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can always change this later in Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Mood chips (2 columns)
                MoodRow(
                    moods = listOf("calm", "romantic"),
                    labels = listOf("🌸 Calm", "💖 Romantic"),
                    selected = selectedMood,
                    onSelect = { mood ->
                        viewModel.saveMood(mood)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                MoodRow(
                    moods = listOf("bold", "energetic"),
                    labels = listOf("🔥 Bold", "🌈 Energetic"),
                    selected = selectedMood,
                    onSelect = { mood ->
                        viewModel.saveMood(mood)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                MoodRow(
                    moods = listOf("elegant", "playful"),
                    labels = listOf("💜 Elegant", "💗 Playful"),
                    selected = selectedMood,
                    onSelect = { mood ->
                        viewModel.saveMood(mood)
                    }
                )
            }

            // Bottom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onBack() }
                )

                Button(
                    onClick = onNext,
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

@Composable
private fun MoodRow(
    moods: List<String>,
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        moods.zip(labels).forEach { (mood, label) ->
            val isSelected = selected == mood
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
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onSelect(mood) }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
