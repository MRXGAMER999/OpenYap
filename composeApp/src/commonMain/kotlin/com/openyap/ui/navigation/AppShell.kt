package com.openyap.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openyap.model.RecordingState
import com.openyap.ui.component.RecordingIndicator
import com.openyap.ui.screen.CustomizationScreen
import com.openyap.ui.screen.DictionaryScreen
import com.openyap.ui.screen.HistoryScreen
import com.openyap.ui.screen.OnboardingScreen
import com.openyap.ui.screen.SettingsScreen
import com.openyap.ui.screen.StatsScreen
import com.openyap.ui.screen.UserInfoScreen
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.DictionaryEvent
import com.openyap.viewmodel.DictionaryUiState
import com.openyap.viewmodel.HistoryEvent
import com.openyap.viewmodel.HistoryUiState
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingUiState
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsUiState
import com.openyap.viewmodel.StatsUiState
import com.openyap.viewmodel.UserProfileEvent
import com.openyap.viewmodel.UserProfileUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(
    backStack: MutableList<Route>,
    recordingState: RecordingUiState,
    settingsState: SettingsUiState,
    historyState: HistoryUiState,
    onboardingState: OnboardingUiState,
    dictionaryState: DictionaryUiState,
    userProfileState: UserProfileUiState,
    statsState: StatsUiState,
    appTones: Map<String, String>,
    appPrompts: Map<String, String>,
    onRecordingEvent: (RecordingEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onHistoryEvent: (HistoryEvent) -> Unit,
    onOnboardingEvent: (OnboardingEvent) -> Unit,
    onDictionaryEvent: (DictionaryEvent) -> Unit,
    onUserProfileEvent: (UserProfileEvent) -> Unit,
    onSaveTone: (String, String) -> Unit,
    onSavePrompt: (String, String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onStatsRefresh: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
    if (!onboardingState.isLoaded) {
        return
    }

    if (!onboardingState.isComplete) {
        OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
        return
    }

    val currentRoute = backStack.lastOrNull() ?: Route.Home

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md, top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Slimmed-down navigation rail (92dp instead of 132dp)
        Surface(
            modifier = Modifier.fillMaxHeight().width(92.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                containerColor = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // App icon — smaller 40dp circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text("OpenYap", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.sm))

                    // Navigation items with selected/unselected icons
                    railRoutes.forEach { dest ->
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    backStack.clear()
                                    backStack.add(dest.route)
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == dest.route) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = {
                                Text(
                                    dest.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Content area with Snackbar support
        val snackbarHostState = remember { SnackbarHostState() }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 2.dp,
                ) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<Route.Home> {
                                HomeContent(
                                    recordingState,
                                    settingsState,
                                    onRecordingEvent,
                                    snackbarHostState,
                                )
                            }
                            entry<Route.History> {
                                HistoryScreen(historyState, onHistoryEvent, onCopyToClipboard)
                            }
                            entry<Route.Dictionary> {
                                DictionaryScreen(
                                    state = dictionaryState,
                                    isDictionaryEnabled = settingsState.dictionaryEnabled,
                                    onEvent = onDictionaryEvent,
                                )
                            }
                            entry<Route.UserInfo> {
                                UserInfoScreen(userProfileState, onUserProfileEvent)
                            }
                            entry<Route.Stats> {
                                StatsScreen(statsState, onRefresh = onStatsRefresh)
                            }
                            entry<Route.Customization> {
                                CustomizationScreen(
                                    appTones,
                                    appPrompts,
                                    onSaveTone,
                                    onSavePrompt,
                                    onRemoveApp
                                )
                            }
                            entry<Route.Settings> {
                                SettingsScreen(settingsState, onSettingsEvent)
                            }
                            entry<Route.Onboarding> {
                                OnboardingScreen(
                                    state = onboardingState,
                                    onEvent = onOnboardingEvent
                                )
                            }
                        },
                    )
                }
            }

            // In-window indicator — secondary to the floating overlay.
            // Only shown for error states (overlay doesn't persist errors).
            val showInWindowIndicator = recordingState.recordingState is RecordingState.Error
            if (showInWindowIndicator) {
                RecordingIndicator(
                    recordingState = recordingState.recordingState,
                    amplitude = recordingState.amplitude,
                    onCancel = { onRecordingEvent(RecordingEvent.CancelRecording) },
                    onErrorDismissed = { onRecordingEvent(RecordingEvent.DismissError) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.md),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
private fun HomeContent(
    state: RecordingUiState,
    settingsState: SettingsUiState,
    onEvent: (RecordingEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val lastResultText = state.lastResultText
    val errorMessage = state.error
    val statusTitle = when (state.recordingState) {
        is RecordingState.Idle -> "Ready to speak"
        is RecordingState.Recording -> "Capturing your thought"
        is RecordingState.Processing -> "Polishing your words"
        is RecordingState.Success -> "Pasted with style"
        is RecordingState.Error -> "Need one more try"
    }
    val statusBody = when (state.recordingState) {
        is RecordingState.Idle -> "Press ${settingsState.hotkeyLabel} or use the button below to record and paste polished text instantly."
        is RecordingState.Recording -> "Listening live. Release when you're done speaking."
        is RecordingState.Processing -> "Gemini is transcribing, refining, and preparing the final paste."
        is RecordingState.Success -> "Your latest response has already been pasted into the active app."
        is RecordingState.Error -> errorMessage ?: "Something interrupted the flow."
    }
    val isRecording = state.recordingState is RecordingState.Recording
    val canStart = state.recordingState is RecordingState.Idle ||
            state.recordingState is RecordingState.Success ||
            state.recordingState is RecordingState.Error

    // Snackbar for errors instead of inline error banner
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short,
            )
            onEvent(RecordingEvent.DismissError)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Voice capture") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    AnimatedContent(
                        targetState = statusTitle,
                        transitionSpec = {
                            (slideInVertically { it / 4 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 4 } + fadeOut())
                        },
                        label = "homeStatusTitle",
                    ) { title ->
                        Text(title, style = MaterialTheme.typography.displaySmallEmphasized)
                    }
                    Text(
                        text = statusBody,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 620.dp),
                    )

                    // Interactive status chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (state.hasMicPermission && state.hasApiKey) {
                            // All systems go
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("✓ All systems go") },
                                colors = AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            )
                        } else {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SettingsVoice,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (state.hasMicPermission) "Mic ready" else "Mic needed") },
                            )
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (state.hasApiKey) "Gemini linked" else "API key needed") },
                            )
                        }
                        // Hotkey chip (relocated from nav rail)
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.KeyboardCommandKey,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(settingsState.hotkeyLabel) },
                        )
                    }
                }

                // Prominent FAB-style record button with pulse animation
                val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isRecording) 1.15f else 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "pulseScale",
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Outer pulse ring (visible only during recording)
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    ),
                            )
                        }
                        // Main record button
                        Surface(
                            onClick = { onEvent(RecordingEvent.ToggleRecording) },
                            enabled = isRecording || canStart,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = if (isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isRecording) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }

                // Latest result
                AnimatedVisibility(
                    visible = lastResultText != null && state.recordingState is RecordingState.Success,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    ElevatedCard {
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = "Latest result",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = lastResultText.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
