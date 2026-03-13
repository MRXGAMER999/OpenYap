package com.openyap.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxHeight().width(132.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp),
                containerColor = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text("OpenYap", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        text = "Expressive voice flow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Capture, rewrite, paste",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))

                    railRoutes.forEach { dest ->
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    backStack.clear()
                                    backStack.add(dest.route)
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.KeyboardCommandKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(settingsState.hotkeyLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                tonalElevation = 2.dp,
            ) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<Route.Home> {
                            HomeContent(recordingState, settingsState, onRecordingEvent)
                        }
                        entry<Route.History> {
                            HistoryScreen(historyState, onHistoryEvent, onCopyToClipboard)
                        }
                        entry<Route.Dictionary> {
                            DictionaryScreen(dictionaryState, onDictionaryEvent)
                        }
                        entry<Route.UserInfo> {
                            UserInfoScreen(userProfileState, onUserProfileEvent)
                        }
                        entry<Route.Stats> {
                            StatsScreen(statsState, onRefresh = onStatsRefresh)
                        }
                        entry<Route.Customization> {
                            CustomizationScreen(appTones, appPrompts, onSaveTone, onSavePrompt, onRemoveApp)
                        }
                        entry<Route.Settings> {
                            SettingsScreen(settingsState, onSettingsEvent)
                        }
                        entry<Route.Onboarding> {
                            OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
                        }
                    },
                )
            }

            RecordingIndicator(
                recordingState = recordingState.recordingState,
                amplitude = recordingState.amplitude,
                onCancel = { onRecordingEvent(RecordingEvent.CancelRecording) },
                onErrorDismissed = { onRecordingEvent(RecordingEvent.DismissError) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
private fun HomeContent(
    state: RecordingUiState,
    settingsState: SettingsUiState,
    onEvent: (RecordingEvent) -> Unit,
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

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Voice capture") },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                leadingIcon = { Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(if (state.hasMicPermission) "Mic ready" else "Mic needed") },
                            )
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(if (state.hasApiKey) "Gemini linked" else "API key needed") },
                            )
                            if (lastResultText != null) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Latest result ready") },
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MetaPill("Hotkey", settingsState.hotkeyLabel)
                            MetaPill("API key", if (state.hasApiKey) "Ready" else "Missing")
                            MetaPill("Microphone", if (state.hasMicPermission) "Granted" else "Required")
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onEvent(RecordingEvent.ToggleRecording) },
                        enabled = isRecording || canStart,
                        colors = if (isRecording) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (isRecording) "Stop Recording" else "Start Recording")
                    }
                    FilledTonalButton(onClick = {}, enabled = false) {
                        Icon(Icons.Default.KeyboardCommandKey, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Hotkey: ${settingsState.hotkeyLabel}")
                    }
                }

                AnimatedVisibility(
                    visible = lastResultText != null && state.recordingState is RecordingState.Success,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    ElevatedCard {
                        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = errorMessage.orEmpty(),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { onEvent(RecordingEvent.DismissError) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPill(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
