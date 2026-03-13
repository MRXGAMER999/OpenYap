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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openyap.model.PermissionStatus
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState

enum class StepState { LOCKED, ACTIVE, COMPLETE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar for model fetch errors
    androidx.compose.runtime.LaunchedEffect(state.modelsFetchError) {
        state.modelsFetchError?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long,
            )
        }
    }

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
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    modifier = Modifier.widthIn(max = 760.dp).padding(Spacing.lg),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Text(
                            "OpenYap",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Shape your voice workflow",
                            style = MaterialTheme.typography.displaySmallEmphasized
                        )
                        Text(
                            text = "Give OpenYap microphone access, add your Gemini key, and pick a model. After that, every recording becomes a polished paste.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("3-step setup") })
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Desktop voice workflow") })
                        }

                        LinearProgressIndicator(
                            progress = { ((state.currentStep + 1).coerceAtMost(3)) / 3f },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Step 1 — Microphone
                        val step1State = when {
                            state.micPermission == PermissionStatus.GRANTED -> StepState.COMPLETE
                            state.currentStep >= 0 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 1,
                            title = "Microphone Access",
                            subtitle = "Enable live capture for hands-free input.",
                            stepState = step1State
                        ) {
                            when (state.micPermission) {
                                PermissionStatus.GRANTED -> {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(
                                            text = "Microphone access granted.",
                                            modifier = Modifier.padding(
                                                horizontal = Spacing.md,
                                                vertical = Spacing.sm
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }

                                else -> {
                                    Text("OpenYap needs microphone permission before it can capture your voice.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                        // Vertical connector
                        StepConnector()

                        // Step 2 — API Key
                        val step2State = when {
                            state.apiKey.isNotBlank() -> StepState.COMPLETE
                            state.currentStep >= 1 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 2,
                            title = "Gemini API Key",
                            subtitle = "Connect the voice pipeline to your model backend.",
                            stepState = step2State
                        ) {
                            var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
                            var showKey by remember { mutableStateOf(false) }

                            AnimatedVisibility(
                                visible = state.currentStep >= 1,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                        // Vertical connector
                        StepConnector()

                        // Step 3 — Model selection
                        val step3State = when {
                            state.selectedModel.isNotBlank() -> StepState.COMPLETE
                            state.currentStep >= 2 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 3,
                            title = "Choose Model",
                            subtitle = "Pick the model that should power your writing flow.",
                            stepState = step3State
                        ) {
                            AnimatedVisibility(
                                visible = state.currentStep >= 2,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    if (state.isLoadingModels) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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

                                    if (!state.isLoadingModels && state.availableModels.isNotEmpty()) {
                                        OnboardingModelDropdown(
                                            models = state.availableModels.map { it.id to it.displayName },
                                            selectedModelId = state.selectedModel,
                                            onModelSelected = {
                                                onEvent(
                                                    OnboardingEvent.SelectModel(
                                                        it
                                                    )
                                                )
                                            },
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
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .padding(start = 17.dp) // Align with center of step circle (34dp / 2)
            .width(1.dp)
            .height(Spacing.xxl)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingModelDropdown(
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

@Composable
private fun StepSection(
    stepNumber: Int,
    title: String,
    subtitle: String,
    stepState: StepState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Step number circle with state-based styling
                when (stepState) {
                    StepState.COMPLETE -> {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Complete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    StepState.ACTIVE -> {
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "stepPulse$stepNumber")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                            label = "stepScale$stepNumber",
                        )
                        Surface(
                            modifier = Modifier.size(34.dp).scale(pulseScale),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    stepNumber.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    StepState.LOCKED -> {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    stepNumber.toString(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (stepState == StepState.LOCKED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}
