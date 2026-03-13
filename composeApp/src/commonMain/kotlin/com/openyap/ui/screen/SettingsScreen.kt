package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar for hotkey errors
    LaunchedEffect(state.hotkeyError) {
        state.hotkeyError?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short,
            )
            onEvent(SettingsEvent.ClearHotkeyMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Tune the voice pipeline, choose the Gemini model, and control how OpenYap rewrites what you say.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 620.dp),
            )

            // ── Recording hotkey ─────────────────────────────────
            Text("Recording hotkey", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Current shortcut: ${state.hotkeyLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Press new key combination to reassign the global recording hotkey") } },
                    state = rememberTooltipState(),
                ) {
                    FilledTonalButton(
                        onClick = { onEvent(SettingsEvent.CaptureHotkey) },
                        enabled = !state.isCapturingHotkey,
                    ) {
                        Text(if (state.isCapturingHotkey) "Press keys..." else "Change hotkey")
                    }
                }
                if (state.isCapturingHotkey) {
                    Text(
                        text = "Press the new shortcut now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider()

            // ── Gemini API key ───────────────────────────────────
            Text("Gemini API key", style = MaterialTheme.typography.headlineSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Enter your Gemini API key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                )
                TextButton(onClick = {
                    showKey = !showKey
                }) { Text(if (showKey) "Hide" else "Show") }
                FilledTonalButton(
                    onClick = { onEvent(SettingsEvent.SaveApiKey(apiKeyInput)) },
                    enabled = apiKeyInput.isNotBlank(),
                ) {
                    Text("Save")
                }
            }
            Text(
                "The key is stored locally and used only for Gemini requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SaveMessage(state.saveMessage) { onEvent(SettingsEvent.DismissSaveMessage) }

            HorizontalDivider()

            // ── Model ────────────────────────────────────────────
            Text("Model", style = MaterialTheme.typography.headlineSmall)

            AnimatedVisibility(
                visible = state.isLoadingModels,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Loading available models...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.modelsFetchError?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onEvent(SettingsEvent.RefreshModels) }) { Text("Retry") }
                    }
                }
            }

            AnimatedVisibility(
                visible = !state.isLoadingModels && state.availableModels.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Select which Gemini model powers transcription and rewriting") } },
                    state = rememberTooltipState(),
                ) {
                    ModelDropdown(
                        models = state.availableModels.map { it.id to it.displayName },
                        selectedModelId = state.geminiModel,
                        onModelSelected = { onEvent(SettingsEvent.SelectModel(it)) },
                    )
                }
            }

            if (!state.isLoadingModels && state.availableModels.isEmpty() && state.modelsFetchError == null && state.apiKey.isBlank()) {
                Text(
                    "Save your API key to load available models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // ── Features ─────────────────────────────────────────
            Text("Features", style = MaterialTheme.typography.headlineSmall)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Automatically replace shortcuts like your name, company, and dictionary phrases in transcribed text") } },
                state = rememberTooltipState(),
            ) {
                FeatureToggleRow(
                    label = "Phrase Expansion",
                    description = "Expand shortcuts like your name, company, and custom dictionary phrases.",
                    checked = state.phraseExpansionEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.TogglePhraseExpansion(it)) },
                )
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Example: \"Please review the report\" → \"pls check the report rq 💀\"") } },
                state = rememberTooltipState(),
            ) {
                FeatureToggleRow(
                    label = "Gen Z Mode",
                    description = "Use a more playful tone when prompts allow it.",
                    checked = state.genZEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleGenZ(it)) },
                )
            }

            HorizontalDivider()

            Text("Recording", style = MaterialTheme.typography.headlineSmall)
            FeatureToggleRow(
                label = "Audio feedback",
                description = "Play a short tone when recording starts and stops.",
                checked = state.audioFeedbackEnabled,
                onCheckedChange = { onEvent(SettingsEvent.ToggleAudioFeedback(it)) },
            )

            HorizontalDivider()

            Text("Startup", style = MaterialTheme.typography.headlineSmall)
            FeatureToggleRow(
                label = "Start minimized to tray",
                description = "Launch directly to the system tray without showing the main window.",
                checked = state.startMinimized,
                onCheckedChange = { onEvent(SettingsEvent.ToggleStartMinimized(it)) },
            )

            Text(
                text = "OpenYap v${state.appVersion.ifBlank { "1.0.0" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun SaveMessage(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000)
            onDismiss()
        }
        Text(
            message,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    val selectedLabel =
        models.firstOrNull { it.first == selectedModelId }?.second ?: selectedModelId

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { (id, displayName) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(id)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}
