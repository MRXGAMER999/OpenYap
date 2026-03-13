@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.openyap.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openyap.ui.component.EmptyState
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.StatsUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(state: StatsUiState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Stats", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "A snapshot of how often OpenYap is capturing and refining your voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 540.dp),
                )
            }
            FilledTonalButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (!state.isLoading && state.totalRecordings > 0) {
            AssistChip(onClick = {}, enabled = false, label = { Text("${state.totalRecordings} recordings analyzed") })
        }

        if (state.isLoading) {
            com.openyap.ui.component.SkeletonList(count = 4)
        } else if (state.totalRecordings == 0) {
            EmptyState(
                icon = Icons.Default.BarChart,
                title = "No stats yet",
                subtitle = "Stats appear after your first captured thought.",
            )
        } else {
            // Hero stat cards — keep ElevatedCard for prominence
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StatCard("Recordings", state.totalRecordings)
                StatCard("Total Duration", formatDuration(state.totalDurationSeconds))
                StatCard("Characters", state.totalCharacters)
                StatCard("Avg Duration", "${state.averageDurationSeconds}s")
            }

            // Top apps — secondary info, use OutlinedCard
            if (state.topApps.isNotEmpty()) {
                OutlinedCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text("Top apps", style = MaterialTheme.typography.headlineSmall)
                        state.topApps.forEach { (app, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(app, style = MaterialTheme.typography.titleMedium)
                                AssistChip(onClick = {}, enabled = false, label = { Text("$count recordings") })
                            }
                        }
                    }
                }
            }
        }
    }
}

// Animated stat card for integer values
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatCard(label: String, targetValue: Int) {
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(targetValue) {
        animatedValue.animateTo(targetValue.toFloat(), tween(800))
    }
    ElevatedCard(modifier = Modifier.widthIn(min = 180.dp)) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(animatedValue.value.toInt().toString(), style = MaterialTheme.typography.displaySmallEmphasized)
        }
    }
}

// Stat card for string values (no animation)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatCard(label: String, value: String) {
    ElevatedCard(modifier = Modifier.widthIn(min = 180.dp)) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.displaySmallEmphasized)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
