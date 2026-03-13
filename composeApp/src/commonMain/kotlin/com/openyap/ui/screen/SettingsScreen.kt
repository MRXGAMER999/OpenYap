package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.ui.theme.Spacing
import com.openyap.model.AudioDevice
import com.openyap.model.TranscriptionProvider
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var groqApiKeyInput by remember(state.groqApiKey) { mutableStateOf(state.groqApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
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
                "Choose a transcription provider, configure API keys, pick a model, and control how OpenYap processes what you say.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 620.dp),
            )

            SettingsSectionCard(
                title = "Input controls",
                description = "Tune the global hotkey and the microphone OpenYap listens to.",
            ) {
                Text("Recording hotkey", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Current shortcut: ${state.hotkeyLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hotkeyPulse = rememberInfiniteTransition(label = "hotkeyPulse")
                    val hotkeyBorderAlpha by hotkeyPulse.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                        label = "hotkeyBorderAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (state.isCapturingHotkey) {
                                    Modifier.border(
                                        BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = hotkeyBorderAlpha)
                                        ),
                                        RoundedCornerShape(18.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(2.dp)
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
                    }
                    if (state.isCapturingHotkey) {
                        Text(
                            text = "Press the new shortcut now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "Microphone",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Choose which microphone to use for recording. \"System Default\" follows your Windows default device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.isLoadingDevices) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Loading audio devices...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.devicesFetchError?.let { error ->
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
                            TextButton(onClick = { onEvent(SettingsEvent.RefreshDevices) }) { Text("Retry") }
                        }
                    }
                }

                if (!state.isLoadingDevices && state.audioDevices.isNotEmpty()) {
                    MicrophoneDropdown(
                        devices = state.audioDevices,
                        selectedDeviceId = state.selectedAudioDeviceId,
                        onDeviceSelected = { onEvent(SettingsEvent.SelectAudioDevice(it)) },
                    )
                }

                if (!state.isLoadingDevices && state.audioDevices.isEmpty() && state.devicesFetchError == null) {
                    Text(
                        "No audio devices found. Native audio pipeline may be unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionCard(
                title = "AI pipeline",
                description = "Choose the provider, connect your keys, and pick the models that shape each transcription.",
            ) {
                Text("Transcription provider", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gemini can transcribe and rewrite directly, Groq Whisper gives fast raw transcription, and the combined mode uses Groq first then Gemini for conservative context-aware correction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProviderDropdown(
                    selectedProvider = state.transcriptionProvider,
                    onProviderSelected = { onEvent(SettingsEvent.SelectProvider(it)) },
                )

                if (state.transcriptionProvider != TranscriptionProvider.GROQ_WHISPER) {
                    Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
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
                }

                if (state.transcriptionProvider == TranscriptionProvider.GROQ_WHISPER_GEMINI) {
                    Spacer(Modifier.height(Spacing.sm))
                }

                if (state.transcriptionProvider != TranscriptionProvider.GEMINI) {
                    Text("Groq API key", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = groqApiKeyInput,
                            onValueChange = { groqApiKeyInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Enter your Groq API key") },
                            visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                        TextButton(onClick = {
                            showGroqKey = !showGroqKey
                        }) { Text(if (showGroqKey) "Hide" else "Show") }
                        FilledTonalButton(
                            onClick = { onEvent(SettingsEvent.SaveGroqApiKey(groqApiKeyInput)) },
                            enabled = groqApiKeyInput.isNotBlank(),
                        ) {
                            Text("Save")
                        }
                    }
                    Text(
                        "The key is stored locally and used only for Groq Whisper requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SaveMessage(state.saveMessage) { onEvent(SettingsEvent.DismissSaveMessage) }

                Text("Models", style = MaterialTheme.typography.titleMedium)

                if (state.transcriptionProvider == TranscriptionProvider.GEMINI) {
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
                } else if (state.transcriptionProvider == TranscriptionProvider.GROQ_WHISPER) {
                    ModelDropdown(
                        models = state.groqModels.map { it.id to it.displayName },
                        selectedModelId = state.groqModel,
                        onModelSelected = { onEvent(SettingsEvent.SelectGroqModel(it)) },
                    )
                } else {
                    Text(
                        "Groq transcription model",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ModelDropdown(
                        models = state.groqModels.map { it.id to it.displayName },
                        selectedModelId = state.groqModel,
                        onModelSelected = { onEvent(SettingsEvent.SelectGroqModel(it)) },
                    )

                    Spacer(Modifier.height(Spacing.sm))

                    Text(
                        "Gemini correction model",
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                                "Loading available Gemini models...",
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
                        ModelDropdown(
                            models = state.availableModels.map { it.id to it.displayName },
                            selectedModelId = state.geminiModel,
                            onModelSelected = { onEvent(SettingsEvent.SelectModel(it)) },
                        )
                    }

                    if (!state.isLoadingModels && state.availableModels.isEmpty() && state.modelsFetchError == null && state.apiKey.isBlank()) {
                        Text(
                            "Save your Gemini API key to load correction models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSectionCard(
                title = "Experience",
                description = "Control how OpenYap rewrites text, behaves at launch, and sounds during recording.",
            ) {
                Text("Writing features", style = MaterialTheme.typography.titleMedium)
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
                    tooltip = { PlainTooltip { Text("Turn dictionary replacements and auto-learning on or off without deleting saved entries") } },
                    state = rememberTooltipState(),
                ) {
                    FeatureToggleRow(
                        label = "Dictionary",
                        description = "Use saved dictionary phrases and learn repeated replacements from your transcripts.",
                        checked = state.dictionaryEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.ToggleDictionary(it)) },
                    )
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Example: \"Please review the report\" -> \"pls check the report rq\"") } },
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

                Text("Recording", style = MaterialTheme.typography.titleMedium)
                FeatureToggleRow(
                    label = "Audio feedback",
                    description = "Play a short tone when recording starts and stops.",
                    checked = state.audioFeedbackEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleAudioFeedback(it)) },
                )
                FeatureToggleRow(
                    label = "Start minimized to tray",
                    description = "Launch directly to the system tray without showing the main window.",
                    checked = state.startMinimized,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleStartMinimized(it)) },
                )
                FeatureToggleRow(
                    label = "Launch on Windows startup",
                    description = if (state.startupSupported) {
                        "Start OpenYap automatically when you sign in to Windows. Pair this with Start minimized to tray for a quiet launch."
                    } else {
                        "Available in the installed desktop app. OpenYap needs its packaged launcher before it can register startup."
                    },
                    checked = state.launchOnStartup,
                    enabled = state.startupSupported,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleLaunchOnStartup(it)) },
                )
            }

            SettingsSectionCard(
                title = "Troubleshooting",
                description = "Reset local settings, prompts, history, dictionary entries, profile data, and stored API keys.",
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    enabled = !state.isResettingData,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                ) {
                    Text(if (state.isResettingData) "Resetting..." else "Reset app data")
                }
            }

            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text("About", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "OpenYap v${state.appVersion.ifBlank { "1.0.0" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset app data?") },
            text = {
                Text(
                    "This deletes local settings, saved prompts, history, dictionary entries, profile info, and the stored API key."
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showResetDialog = false
                    onEvent(SettingsEvent.ResetAppData)
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
private fun SettingsSectionCard(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
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
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicrophoneDropdown(
    devices: List<AudioDevice>,
    selectedDeviceId: String?,
    onDeviceSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId }
    val selectedLabel = when {
        selectedDeviceId == null -> "System Default"
        selectedDevice != null -> selectedDevice.name
        else -> "System Default"
    }

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
            // "System Default" option at the top
            DropdownMenuItem(
                text = {
                    Text("System Default", style = MaterialTheme.typography.bodyMedium)
                },
                onClick = {
                    onDeviceSelected(null)
                    expanded = false
                },
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            // Individual devices
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                buildString {
                                    append(device.name)
                                    if (device.isDefault) append(" (Default)")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        onDeviceSelected(device.id)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: TranscriptionProvider,
    onProviderSelected: (TranscriptionProvider) -> Unit,
) {
    val providers = listOf(
        TranscriptionProvider.GEMINI to "Gemini (Transcribe + Rewrite)",
        TranscriptionProvider.GROQ_WHISPER to "Groq Whisper (Raw Transcription)",
        TranscriptionProvider.GROQ_WHISPER_GEMINI to "Groq + Gemini (Context Correction)",
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = providers.firstOrNull { it.first == selectedProvider }?.second ?: selectedProvider.name

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
            providers.forEach { (provider, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}
