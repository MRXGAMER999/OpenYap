package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
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
import com.openyap.model.AudioDevice
import com.openyap.model.PrimaryUseCase
import com.openyap.model.TranscriptionProvider
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
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
                    val geminiKeyFormatError = when {
                        apiKeyInput.isBlank() -> null
                        apiKeyInput.length < 10 -> "Key looks too short"
                        apiKeyInput.contains(" ") -> "Key should not contain spaces"
                        else -> null
                    }
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
                            isError = geminiKeyFormatError != null,
                            supportingText = geminiKeyFormatError?.let { err -> { Text(err) } },
                        )
                        TextButton(onClick = {
                            showKey = !showKey
                        }) { Text(if (showKey) "Hide" else "Show") }
                        FilledTonalButton(
                            onClick = { onEvent(SettingsEvent.SaveApiKey(apiKeyInput)) },
                            enabled = apiKeyInput.isNotBlank() && geminiKeyFormatError == null,
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
                    val groqKeyFormatError = when {
                        groqApiKeyInput.isBlank() -> null
                        groqApiKeyInput.length < 10 -> "Key looks too short"
                        groqApiKeyInput.contains(" ") -> "Key should not contain spaces"
                        else -> null
                    }
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
                            isError = groqKeyFormatError != null,
                            supportingText = groqKeyFormatError?.let { err -> { Text(err) } },
                        )
                        TextButton(onClick = {
                            showGroqKey = !showGroqKey
                        }) { Text(if (showGroqKey) "Hide" else "Show") }
                        FilledTonalButton(
                            onClick = { onEvent(SettingsEvent.SaveGroqApiKey(groqApiKeyInput)) },
                            enabled = groqApiKeyInput.isNotBlank() && groqKeyFormatError == null,
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
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
                                TextButton(onClick = { onEvent(SettingsEvent.RefreshModels) }) {
                                    Text(
                                        "Retry"
                                    )
                                }
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
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
                                TextButton(onClick = { onEvent(SettingsEvent.RefreshModels) }) {
                                    Text(
                                        "Retry"
                                    )
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
                            "Save your Gemini API key to load correction models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSectionCard(
                title = "Transcription language",
                description = "Set the language for Whisper-based transcription. Gemini auto-detects language.",
            ) {
                WhisperLanguageDropdown(
                    selectedLanguage = state.whisperLanguage,
                    onLanguageSelected = { onEvent(SettingsEvent.SelectWhisperLanguage(it)) },
                )
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

                Text("Transcription context", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tell OpenYap what you mainly talk about so it can better recognize specialized vocabulary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UseCaseChipRow(
                    selected = state.primaryUseCase,
                    onSelected = { onEvent(SettingsEvent.SelectUseCase(it)) },
                )
                AnimatedVisibility(
                    visible = state.primaryUseCase != PrimaryUseCase.GENERAL,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    var contextInput by remember(state.useCaseContext) {
                        mutableStateOf(state.useCaseContext)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val placeholder = when (state.primaryUseCase) {
                            PrimaryUseCase.PROGRAMMING -> "e.g., Kotlin, Android, Jetpack Compose"
                            PrimaryUseCase.BUSINESS -> "e.g., product management, finance, marketing"
                            PrimaryUseCase.CREATIVE_WRITING -> "e.g., fiction, technical docs, screenwriting"
                            PrimaryUseCase.GENERAL -> ""
                        }
                        val label = when (state.primaryUseCase) {
                            PrimaryUseCase.PROGRAMMING -> "Describe your stack"
                            PrimaryUseCase.BUSINESS -> "Describe your field"
                            PrimaryUseCase.CREATIVE_WRITING -> "Describe your topics"
                            PrimaryUseCase.GENERAL -> ""
                        }
                        OutlinedTextField(
                            value = contextInput,
                            onValueChange = { contextInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            label = { Text(label) },
                            placeholder = { Text(placeholder) },
                            supportingText = {
                                Text("This context is passed into transcription prompts.")
                            },
                        )
                        FilledTonalButton(
                            onClick = { onEvent(SettingsEvent.SaveUseCaseContext(contextInput.trim())) },
                            enabled = contextInput.trim() != state.useCaseContext,
                        ) {
                            Text("Save context")
                        }
                    }
                }

                HorizontalDivider()

                Text("Recording", style = MaterialTheme.typography.titleMedium)
                FeatureToggleRow(
                    label = "Audio feedback",
                    description = "Play a short tone when recording starts and stops.",
                    checked = state.audioFeedbackEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleAudioFeedback(it)) },
                )
                AnimatedVisibility(visible = state.audioFeedbackEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Tone volume",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${(state.soundFeedbackVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = state.soundFeedbackVolume,
                            onValueChange = { onEvent(SettingsEvent.SetSoundFeedbackVolume(it)) },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Adjust how loud the start and stop tones play.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                    "This deletes local settings, saved prompts, history, dictionary entries, profile info, and stored API keys."
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

@Composable
private fun UseCaseChipRow(
    selected: PrimaryUseCase,
    onSelected: (PrimaryUseCase) -> Unit,
) {
    val options = listOf(
        PrimaryUseCase.GENERAL to "General",
        PrimaryUseCase.PROGRAMMING to "Programming",
        PrimaryUseCase.BUSINESS to "Business",
        PrimaryUseCase.CREATIVE_WRITING to "Writing",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (useCase, label) ->
            val isSelected = selected == useCase
            if (isSelected) {
                Button(
                    onClick = { onSelected(useCase) },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(useCase) },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(label)
                }
            }
        }
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
    val selectedLabel =
        providers.firstOrNull { it.first == selectedProvider }?.second ?: selectedProvider.name

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

private val WHISPER_LANGUAGES = listOf(
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "it" to "Italian",
    "pt" to "Portuguese",
    "nl" to "Dutch",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ru" to "Russian",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "pl" to "Polish",
    "tr" to "Turkish",
    "sv" to "Swedish",
    "da" to "Danish",
    "no" to "Norwegian",
    "fi" to "Finnish",
    "uk" to "Ukrainian",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhisperLanguageDropdown(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = WHISPER_LANGUAGES.firstOrNull { it.first == selectedLanguage }?.second
        ?: "$selectedLanguage (custom)"

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
            WHISPER_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}
