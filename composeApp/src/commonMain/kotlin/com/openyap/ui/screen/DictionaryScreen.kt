package com.openyap.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openyap.ui.component.EmptyState
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.DictionaryEvent
import com.openyap.viewmodel.DictionaryUiState

@Composable
fun DictionaryScreen(
    state: DictionaryUiState,
    onEvent: (DictionaryEvent) -> Unit,
) {
    var newOriginal by remember { mutableStateOf("") }
    var newReplacement by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Dictionary", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Teach OpenYap your preferred replacements, shortcuts, and repeated phrases so every paste sounds like you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 620.dp),
        )
        if (state.entries.isNotEmpty()) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("${state.entries.count { it.isEnabled }} active phrases") })
        }

        // Input row — stays as ElevatedCard (primary action area)
        ElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = newOriginal,
                    onValueChange = { newOriginal = it },
                    label = { Text("Original phrase") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newReplacement,
                    onValueChange = { newReplacement = it },
                    label = { Text("Replacement") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                FilledTonalButton(
                    onClick = {
                        onEvent(DictionaryEvent.AddEntry(newOriginal, newReplacement))
                        newOriginal = ""
                        newReplacement = ""
                    },
                    enabled = newOriginal.isNotBlank() && newReplacement.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        }

        if (state.isLoading) {
            com.openyap.ui.component.SkeletonList(count = 3)
        } else if (state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Book,
                title = "No dictionary entries",
                subtitle = "Add your first phrase above.",
                actionLabel = "Learn more",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.entries, key = { it.id }) { entry ->
                    OutlinedCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Text(entry.original, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "-> ${entry.replacement}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(entry.source.name.lowercase()) })
                            }
                            Switch(
                                checked = entry.isEnabled,
                                onCheckedChange = { onEvent(DictionaryEvent.ToggleEntry(entry.id)) },
                            )
                            TextButton(onClick = { onEvent(DictionaryEvent.RemoveEntry(entry.id)) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}
