package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        HorizontalDivider()

        Text("Gemini API Key", style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Enter your Gemini API key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            )
            TextButton(onClick = { showKey = !showKey }) {
                Text(if (showKey) "Hide" else "Show")
            }
            Button(
                onClick = { onEvent(SettingsEvent.SaveApiKey(apiKeyInput)) },
                enabled = apiKeyInput.isNotBlank(),
            ) {
                Text("Save")
            }
        }

        HorizontalDivider()

        Text("Model", style = MaterialTheme.typography.titleSmall)

        AnimatedVisibility(visible = state.isLoadingModels) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    "Loading available models…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.modelsFetchError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.modelsFetchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEvent(SettingsEvent.RefreshModels) }) {
                        Text("Retry")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !state.isLoadingModels && state.availableModels.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ModelDropdown(
                models = state.availableModels.map { it.id to it.displayName },
                selectedModelId = state.geminiModel,
                onModelSelected = { onEvent(SettingsEvent.SelectModel(it)) },
            )
        }

        if (!state.isLoadingModels && state.availableModels.isEmpty() && state.modelsFetchError == null && state.apiKey.isBlank()) {
            Text(
                "Save your API key to load available models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        Text("Features", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Phrase Expansion", modifier = Modifier.weight(1f))
            Switch(
                checked = state.phraseExpansionEnabled,
                onCheckedChange = { onEvent(SettingsEvent.TogglePhraseExpansion(it)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gen Z Mode", modifier = Modifier.weight(1f))
            Switch(
                checked = state.genZEnabled,
                onCheckedChange = { onEvent(SettingsEvent.ToggleGenZ(it)) },
            )
        }

        if (state.saveMessage != null) {
            LaunchedEffect(state.saveMessage) {
                kotlinx.coroutines.delay(2000)
                onEvent(SettingsEvent.DismissSaveMessage)
            }
            Text(
                state.saveMessage!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "OpenYap v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = models.firstOrNull { it.first == selectedModelId }?.second
        ?: selectedModelId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { (id, displayName) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(id)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
