@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.openyap.service.PromptBuilder

@Composable
fun CustomizationScreen(
    appTones: Map<String, String>,
    appPrompts: Map<String, String>,
    onSaveTone: (String, String) -> Unit,
    onSavePrompt: (String, String) -> Unit,
    onRemoveApp: (String) -> Unit = {},
) {
    val apps = remember(appTones, appPrompts) { (appTones.keys + appPrompts.keys).distinct().sorted() }
    var newAppName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Per-app customization", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Give each app its own tone and instructions so OpenYap adapts to email, chat, docs, and everything in between.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 620.dp),
        )
        if (apps.isNotEmpty()) {
            Text(
                "${apps.size} app profiles configured",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        ElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newAppName,
                    onValueChange = { newAppName = it },
                    label = { Text("App name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                FilledTonalButton(
                    onClick = {
                        if (newAppName.isNotBlank()) {
                            onSaveTone(newAppName.trim(), "normal")
                            newAppName = ""
                        }
                    },
                    enabled = newAppName.isNotBlank(),
                ) { Text("Add") }
            }
        }

        if (apps.isEmpty()) {
            ElevatedCard {
                Text(
                    text = "No per-app customizations yet.",
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(apps) { app ->
                    AppCustomizationCard(
                        appName = app,
                        currentTone = appTones[app] ?: "normal",
                        currentPrompt = appPrompts[app] ?: "",
                        onSaveTone = { onSaveTone(app, it) },
                        onSavePrompt = { onSavePrompt(app, it) },
                        onRemove = { onRemoveApp(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCustomizationCard(
    appName: String,
    currentTone: String,
    currentPrompt: String,
    onSaveTone: (String) -> Unit,
    onSavePrompt: (String) -> Unit,
    onRemove: () -> Unit = {},
) {
    var tone by remember(currentTone) { mutableStateOf(currentTone) }
    var prompt by remember(currentPrompt) { mutableStateOf(currentPrompt) }
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove customization") },
            text = { Text("Remove all customizations for $appName?") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    onRemove()
                    showDeleteConfirm = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    ElevatedCard {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(appName, style = MaterialTheme.typography.headlineSmall)
                    Text("Tone: $tone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Remove") }
                    FilledTonalButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Collapse" else "Edit") }
                }
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PromptBuilder.validTones.forEach { option ->
                            FilterChip(
                                selected = tone == option,
                                onClick = {
                                    tone = option
                                    onSaveTone(option)
                                },
                                label = { Text(option) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Custom prompt instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                    )
                    FilledTonalButton(onClick = { onSavePrompt(prompt) }) { Text("Save prompt") }
                }
            }
        }
    }
}
