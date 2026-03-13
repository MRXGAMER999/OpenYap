package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.model.PermissionStatus
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 760.dp).padding(28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text("OpenYap", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Shape your voice workflow", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(
                    text = "Give OpenYap microphone access, add your Gemini key, and pick a model. After that, every recording becomes a polished paste.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("3-step setup") })
                    AssistChip(onClick = {}, enabled = false, label = { Text("Desktop voice workflow") })
                }

                LinearProgressIndicator(
                    progress = { ((state.currentStep + 1).coerceAtMost(3)) / 3f },
                    modifier = Modifier.fillMaxWidth(),
                )

                StepSection(stepNumber = 1, title = "Microphone Access", subtitle = "Enable live capture for hands-free input.") {
                    when (state.micPermission) {
                        PermissionStatus.GRANTED -> {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    text = "Microphone access granted.",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        else -> {
                            Text("OpenYap needs microphone permission before it can capture your voice.")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { onEvent(OnboardingEvent.CheckMicPermission) }) {
                                    Text("Check Permission")
                                }
                                OutlinedButton(onClick = { onEvent(OnboardingEvent.OpenMicSettings) }) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }

                StepSection(stepNumber = 2, title = "Gemini API Key", subtitle = "Connect the voice pipeline to your model backend.") {
                    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
                    var showKey by remember { mutableStateOf(false) }

                    AnimatedVisibility(
                        visible = state.currentStep >= 1,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Paste your Gemini API key. OpenYap stores it locally and uses it for transcription and rewriting.")
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Gemini API key") },
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showKey = !showKey }) {
                                        Text(if (showKey) "Hide" else "Show")
                                    }
                                },
                            )
                            FilledTonalButton(
                                onClick = { onEvent(OnboardingEvent.SaveApiKey(apiKeyInput)) },
                                enabled = apiKeyInput.isNotBlank(),
                            ) {
                                Text("Save API Key")
                            }
                            if (state.apiKey.isNotBlank()) {
                                Text(
                                    text = "Saved locally for model requests.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (state.currentStep < 1) {
                        Text(
                            text = "Complete the permission step first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                StepSection(stepNumber = 3, title = "Choose Model", subtitle = "Pick the model that should power your writing flow.") {
                    AnimatedVisibility(
                        visible = state.currentStep >= 2,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.isLoadingModels) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Loading available models...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            state.modelsFetchError?.let { error ->
                                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = error,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        TextButton(onClick = { onEvent(OnboardingEvent.SaveApiKey(state.apiKey)) }) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }

                            if (!state.isLoadingModels && state.availableModels.isNotEmpty()) {
                                OnboardingModelDropdown(
                                    models = state.availableModels.map { it.id to it.displayName },
                                    selectedModelId = state.selectedModel,
                                    onModelSelected = { onEvent(OnboardingEvent.SelectModel(it)) },
                                )
                            }
                        }
                    }

                    if (state.currentStep < 2) {
                        Text(
                            text = "Save your API key to unlock model selection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = { onEvent(OnboardingEvent.CompleteOnboarding) },
                    enabled = state.micPermission == PermissionStatus.GRANTED && state.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enter OpenYap")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingModelDropdown(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = models.firstOrNull { it.first == selectedModelId }?.second ?: selectedModelId

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun StepSection(
    stepNumber: Int,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stepNumber.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
