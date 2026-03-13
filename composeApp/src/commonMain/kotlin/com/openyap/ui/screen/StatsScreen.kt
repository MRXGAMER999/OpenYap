@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.openyap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openyap.viewmodel.StatsUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(state: StatsUiState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.totalRecordings == 0) {
            ElevatedCard {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No recordings yet. Stats appear after your first captured thought.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatCard("Recordings", state.totalRecordings.toString())
                StatCard("Total Duration", formatDuration(state.totalDurationSeconds))
                StatCard("Characters", state.totalCharacters.toString())
                StatCard("Avg Duration", "${state.averageDurationSeconds}s")
            }

            if (state.topApps.isNotEmpty()) {
                ElevatedCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatCard(label: String, value: String) {
    ElevatedCard(modifier = Modifier.widthIn(min = 180.dp)) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
