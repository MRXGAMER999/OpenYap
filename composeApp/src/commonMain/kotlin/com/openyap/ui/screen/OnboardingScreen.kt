package com.openyap.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.model.PermissionStatus
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    "Welcome to OpenYap",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Let's get you set up in three quick steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                StepSection(
                    stepNumber = 1,
                    title = "Microphone Access",
                    isActive = state.currentStep == 0,
                ) {
                    when (state.micPermission) {
                        PermissionStatus.GRANTED -> {
                            Text(
                                "Microphone access granted",
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        else -> {
                            Text("OpenYap needs microphone access to record your voice.")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                HorizontalDivider()

                StepSection(
                    stepNumber = 2,
                    title = "Gemini API Key",
                    isActive = state.currentStep <= 1,
                ) {
                    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
                    var showKey by remember { mutableStateOf(false) }

                    Text("Enter your Google Gemini API key to enable voice processing.")
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("API Key") },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show")
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onEvent(OnboardingEvent.SaveApiKey(apiKeyInput)) },
                        enabled = apiKeyInput.isNotBlank(),
                    ) {
                        Text("Save API Key")
                    }
                }

                HorizontalDivider()

                StepSection(
                    stepNumber = 3,
                    title = "Choose Model",
                    isActive = state.currentStep == 2,
                ) {
                    AnimatedVisibility(visible = state.isLoadingModels) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "Loading models…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !state.isLoadingModels && state.availableModels.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        OnboardingModelDropdown(
                            models = state.availableModels.map { it.id to it.displayName },
                            selectedModelId = state.selectedModel,
                            onModelSelected = { onEvent(OnboardingEvent.SelectModel(it)) },
                        )
                    }

                    if (!state.isLoadingModels && state.availableModels.isEmpty() && state.apiKey.isBlank()) {
                        Text(
                            "Save your API key first to see available models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onEvent(OnboardingEvent.CompleteOnboarding) },
                    enabled = state.micPermission == PermissionStatus.GRANTED && state.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Get Started")
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

@Composable
private fun StepSection(
    stepNumber: Int,
    title: String,
    isActive: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$stepNumber",
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
