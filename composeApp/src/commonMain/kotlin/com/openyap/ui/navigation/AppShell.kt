package com.openyap.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openyap.model.RecordingState
import com.openyap.ui.component.RecordingIndicator
import com.openyap.ui.screen.*
import com.openyap.viewmodel.*
import androidx.compose.material3.Icon

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
    onStatsRefresh: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
    if (!onboardingState.isComplete) {
        OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
        return
    }

    val currentRoute = backStack.lastOrNull() ?: Route.Home

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
            Spacer(Modifier.weight(1f))
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
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecordingIndicator(
                    recordingState = recordingState.recordingState,
                    amplitude = recordingState.amplitude,
                    onCancel = { onRecordingEvent(RecordingEvent.CancelRecording) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                )

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<Route.Home> {
                            HomeContent(recordingState, onRecordingEvent)
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
                            CustomizationScreen(appTones, appPrompts, onSaveTone, onSavePrompt)
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
        }
    }
}

@Composable
private fun HomeContent(
    state: RecordingUiState,
    onEvent: (RecordingEvent) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (state.recordingState) {
                    is RecordingState.Idle -> "Ready"
                    is RecordingState.Recording -> "Recording... ${(state.recordingState as RecordingState.Recording).durationSeconds}s"
                    is RecordingState.Processing -> "Processing..."
                    is RecordingState.Success -> "Pasted!"
                    is RecordingState.Error -> "Error"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (!state.hasApiKey) {
                    "Set your API key in Settings to get started."
                } else if (!state.hasMicPermission) {
                    "Microphone permission required."
                } else {
                    "Press Ctrl+Shift+R or use the button below."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (state.hasApiKey && state.hasMicPermission) {
                val isRecording = state.recordingState is RecordingState.Recording
                val isIdle = state.recordingState is RecordingState.Idle ||
                    state.recordingState is RecordingState.Success ||
                    state.recordingState is RecordingState.Error
                Button(
                    onClick = { onEvent(RecordingEvent.ToggleRecording) },
                    enabled = isIdle || isRecording,
                    colors = if (isRecording) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else ButtonDefaults.buttonColors(),
                ) {
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }
                Spacer(Modifier.height(16.dp))
            }

            if (state.lastResultText != null && state.recordingState is RecordingState.Success) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 600.dp),
                ) {
                    Text(
                        text = state.lastResultText!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error!!,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
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
