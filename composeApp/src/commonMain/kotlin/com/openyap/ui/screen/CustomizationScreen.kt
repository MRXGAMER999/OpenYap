package com.openyap.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
) {
    val apps = remember(appTones, appPrompts) {
        (appTones.keys + appPrompts.keys).distinct().sorted()
    }

    var newAppName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Per-App Customization", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Customize tone and prompt per application.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newAppName,
                onValueChange = { newAppName = it },
                label = { Text("App name (e.g., Chrome)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (newAppName.isNotBlank()) {
                        onSaveTone(newAppName.trim(), "normal")
                        newAppName = ""
                    }
                },
                enabled = newAppName.isNotBlank(),
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No per-app customizations yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps) { app ->
                    AppCustomizationCard(
                        appName = app,
                        currentTone = appTones[app] ?: "normal",
                        currentPrompt = appPrompts[app] ?: "",
                        onSaveTone = { tone -> onSaveTone(app, tone) },
                        onSavePrompt = { prompt -> onSavePrompt(app, prompt) },
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
) {
    var tone by remember(currentTone) { mutableStateOf(currentTone) }
    var prompt by remember(currentPrompt) { mutableStateOf(currentPrompt) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Edit")
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Tone", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptBuilder.validTones.forEach { t ->
                        FilterChip(
                            selected = tone == t,
                            onClick = {
                                tone = t
                                onSaveTone(t)
                            },
                            label = { Text(t) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Custom prompt instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = { onSavePrompt(prompt) }) {
                    Text("Save Prompt")
                }
            }
        }
    }
}
