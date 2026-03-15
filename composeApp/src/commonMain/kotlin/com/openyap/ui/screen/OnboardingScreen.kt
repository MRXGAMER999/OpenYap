package com.openyap.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.openyap.model.PermissionStatus
import com.openyap.model.PrimaryUseCase
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState

private const val RECOMMENDED_MODEL_PREFIX = "gemini-3.1-flash-lite"

enum class StepState { LOCKED, ACTIVE, COMPLETE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.modelsFetchErrorId) {
        val errorMsg = state.modelsFetchError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = errorMsg,
            actionLabel = "Retry",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onEvent(OnboardingEvent.RetryModelFetch)
        }
    }

    LaunchedEffect(state.micSettingsUnavailable) {
        if (state.micSettingsUnavailable) {
            snackbarHostState.showSnackbar(
                message = "Cannot open sound settings on this platform. Please configure your microphone manually.",
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
                        // Hero section with app icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Text(
                                "OpenYap",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "Shape your voice workflow",
                            style = MaterialTheme.typography.displaySmallEmphasized,
                        )
                        Text(
                            text = "Give OpenYap microphone access, add your Gemini key, pick a model, and tell us what you talk about. After that, every recording becomes a polished paste.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Badge row — passive info chips, not disabled buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "4-step setup",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "Desktop voice workflow",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        // Progress bar — computed from actual step completions
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progress,
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            label = "progressAnim",
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Step 1 — Microphone
                        val step1State = when {
                            state.micStepComplete -> StepState.COMPLETE
                            state.currentStep >= 0 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 1,
                            title = "Microphone Access",
                            subtitle = "We'll try to open your mic briefly to confirm it works.",
                            stepState = step1State,
                            delayMs = 0,
                        ) {
                            when {
                                state.micPermission == PermissionStatus.GRANTED -> {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(
                                            text = "Microphone access granted.",
                                            modifier = Modifier.padding(
                                                horizontal = Spacing.md,
                                                vertical = Spacing.sm,
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }

                                state.micSkipped -> {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            text = "Microphone check skipped — you can verify later in Settings.",
                                            modifier = Modifier.padding(
                                                horizontal = Spacing.md,
                                                vertical = Spacing.sm,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                else -> {
                                    Text("OpenYap needs microphone permission before it can capture your voice.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        Button(onClick = { onEvent(OnboardingEvent.CheckMicPermission) }) {
                                            Text("Verify Microphone")
                                        }
                                        OutlinedButton(onClick = { onEvent(OnboardingEvent.OpenMicSettings) }) {
                                            Text("Open Sound Settings")
                                        }
                                    }
                                    TextButton(onClick = { onEvent(OnboardingEvent.SkipMicPermission) }) {
                                        Text("Skip for now")
                                    }
                                }
                            }
                        }

                        StepConnector(completed = step1State == StepState.COMPLETE)

                        // Step 2 — API Key
                        val step2State = when {
                            state.apiKeyStepComplete -> StepState.COMPLETE
                            state.currentStep >= 1 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        val apiFocusRequester = remember { FocusRequester() }

                        StepSection(
                            stepNumber = 2,
                            title = "Gemini API Key",
                            subtitle = "Connect the voice pipeline to your model backend.",
                            stepState = step2State,
                            delayMs = 80,
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

                                    // Inline "Get a free key" link — more discoverable
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Text(
                                            "Don't have a key?",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(
                                            onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") },
                                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                                        ) {
                                            Text(
                                                "Get a free key \u2197",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            )
                                        }
                                    }

                                    val keyFormatError = when {
                                        apiKeyInput.isBlank() -> null
                                        apiKeyInput.length < 10 -> "Key looks too short"
                                        apiKeyInput.contains(" ") -> "Key should not contain spaces"
                                        else -> null
                                    }

                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        modifier = Modifier.fillMaxWidth().focusRequester(apiFocusRequester),
                                        singleLine = true,
                                        placeholder = { Text("Gemini API key") },
                                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (state.isValidatingKey) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else if (state.keyValidationSuccess == true) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Valid",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                                TextButton(onClick = { showKey = !showKey }) {
                                                    Text(if (showKey) "Hide" else "Show")
                                                }
                                            }
                                        },
                                        isError = state.keyValidationSuccess == false || keyFormatError != null,
                                        supportingText = when {
                                            keyFormatError != null -> {{ Text(keyFormatError) }}
                                            state.keyValidationSuccess == false -> {{ Text("Key appears invalid — model fetch failed. Double-check and retry.") }}
                                            else -> null
                                        },
                                    )

                                    FilledTonalButton(
                                        onClick = { onEvent(OnboardingEvent.SaveApiKey(apiKeyInput)) },
                                        enabled = apiKeyInput.isNotBlank() && !state.isValidatingKey,
                                    ) {
                                        if (state.isValidatingKey) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text("Save & Verify")
                                        }
                                    }

                                    if (state.apiKey.isNotBlank() && state.keyValidationSuccess == true) {
                                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                                            Text(
                                                text = "\u2713 Key verified — saved locally for model requests.",
                                                modifier = Modifier.padding(
                                                    horizontal = Spacing.md,
                                                    vertical = Spacing.sm,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                    }
                                }
                            }

                            // Auto-focus API key field when step 2 becomes active
                            LaunchedEffect(state.currentStep) {
                                if (state.currentStep == 1 && state.apiKey.isBlank()) {
                                    try { apiFocusRequester.requestFocus() } catch (_: Exception) { }
                                }
                            }

                            if (state.currentStep < 1) {
                                Text(
                                    text = "Complete the microphone step first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        StepConnector(completed = step2State == StepState.COMPLETE)

                        // Step 3 — Model selection
                        val step3State = when {
                            state.modelStepComplete -> StepState.COMPLETE
                            state.currentStep >= 2 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 3,
                            title = "Choose Model",
                            subtitle = "Pick the model that should power your writing flow.",
                            stepState = step3State,
                            delayMs = 160,
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
                                                strokeWidth = 2.dp,
                                            )
                                            Text(
                                                "Loading available models\u2026",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
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

                        StepConnector(completed = step3State == StepState.COMPLETE)

                        // Step 4 — Use case context (optional)
                        val step4State = when {
                            state.useCaseStepComplete -> StepState.COMPLETE
                            state.currentStep >= 3 -> StepState.ACTIVE
                            else -> StepState.LOCKED
                        }
                        StepSection(
                            stepNumber = 4,
                            title = "What You Talk About",
                            subtitle = "Help OpenYap understand your vocabulary.",
                            stepState = step4State,
                            delayMs = 240,
                        ) {
                            AnimatedVisibility(
                                visible = state.currentStep >= 3,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                UseCaseContent(state = state, onEvent = onEvent)
                            }

                            if (state.currentStep < 3) {
                                Text(
                                    text = "Select a model to unlock this step.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // CTA — premium "Enter OpenYap" button
                        Button(
                            onClick = { onEvent(OnboardingEvent.CompleteOnboarding) },
                            enabled = state.canComplete,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
                        ) {
                            Text(
                                "Enter OpenYap",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UseCaseContent(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            "Telling OpenYap what you mainly use it for improves transcription accuracy for specialized terms. This is optional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UseCaseChipRow(
            selected = state.primaryUseCase,
            onSelected = { onEvent(OnboardingEvent.SelectUseCase(it)) },
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
                        Text("This helps recognize domain-specific words during transcription.")
                    },
                )
                FilledTonalButton(
                    onClick = { onEvent(OnboardingEvent.SaveUseCaseContext(contextInput.trim())) },
                    enabled = contextInput.isNotBlank(),
                ) {
                    Text("Save Context")
                }
            }
        }
    }
}

@Composable
private fun StepConnector(completed: Boolean) {
    val dashColor = if (completed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .width(2.dp)
            .height(Spacing.xxl)
            .then(
                if (completed) {
                    Modifier.background(dashColor)
                } else {
                    Modifier.background(dashColor.copy(alpha = 0.5f))
                }
            ),
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
                val isRecommended = id.startsWith(RECOMMENDED_MODEL_PREFIX)
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Text(displayName, style = MaterialTheme.typography.bodyMedium)
                                if (isRecommended) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            "Recommended",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                            Text(
                                buildModelHint(id),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun buildModelHint(id: String): String = when {
    "flash-lite" in id -> "$id · Faster + cheaper"
    "flash" in id -> "$id · Balanced speed & quality"
    "pro" in id -> "$id · Higher quality, slower"
    else -> id
}

@Composable
private fun StepSection(
    stepNumber: Int,
    title: String,
    subtitle: String,
    stepState: StepState,
    delayMs: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Entrance animation — staggered fade-in
    var entranceTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        entranceTriggered = true
    }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entranceTriggered) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "entranceAlpha$stepNumber",
    )

    // Infinite subtle glow for active step
    val infiniteTransition = rememberInfiniteTransition(label = "activeGlow$stepNumber")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (stepState == StepState.ACTIVE) 0.4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha$stepNumber",
    )

    val cardModifier = Modifier
        .alpha(
            when (stepState) {
                StepState.LOCKED -> 0.5f * entranceAlpha
                else -> entranceAlpha
            }
        )
        .then(
            if (stepState == StepState.ACTIVE) {
                Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = glowAlpha * 0.5f),
                        )
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            } else {
                Modifier
            }
        )

    ElevatedCard(modifier = cardModifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        Surface(
                            modifier = Modifier.size(34.dp),
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
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
