package com.openyap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openyap.model.RecordingEntry
import com.openyap.viewmodel.HistoryEvent
import com.openyap.viewmodel.HistoryUiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    onCopyToClipboard: (String) -> Unit = {},
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear history") },
            text = { Text("Delete all ${state.entries.size} recordings? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onEvent(HistoryEvent.ClearAll)
                    showClearConfirm = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("History", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Review recent captures, copy them again, or clear old entries when the trail gets noisy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onEvent(HistoryEvent.Refresh) }) { Text("Refresh") }
                if (state.entries.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) { Text("Clear all") }
                }
            }
        }
        if (state.entries.isNotEmpty()) {
            AssistChip(onClick = {}, enabled = false, label = { Text("${state.entries.size} saved captures") })
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (state.entries.isEmpty()) {
            ElevatedCard {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No recordings yet. Your captured phrases will show up here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.entries, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onCopy = { onCopyToClipboard(entry.response) },
                        onDelete = { onEvent(HistoryEvent.DeleteEntry(entry.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: RecordingEntry,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.targetApp.isNotBlank()) {
                        Text(entry.targetApp, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, enabled = false, label = { Text(formatRecordedAt(entry)) })
                        AssistChip(onClick = {}, enabled = false, label = { Text("${entry.durationSeconds}s") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onCopy) { Text("Copy") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(entry.response, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatRecordedAt(entry: RecordingEntry): String {
    val local = entry.recordedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(local.date)
        append(' ')
        append(local.hour.toString().padStart(2, '0'))
        append(':')
        append(local.minute.toString().padStart(2, '0'))
    }
}
