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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.openyap.model.RecordingState
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingUiState
import com.openyap.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeHeroCard(
    state: RecordingUiState,
    settingsState: SettingsUiState,
    statusTitle: String,
    statusBody: String,
    latestResultText: String?,
    onNavigateToSettings: () -> Unit,
    onEvent: (RecordingEvent) -> Unit,
    reducedMotion: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val isRecording = state.recordingState is RecordingState.Recording
    val canStart = state.recordingState is RecordingState.Idle ||
        state.recordingState is RecordingState.Success ||
        state.recordingState is RecordingState.Error
    val idlePulseAlpha = if (reducedMotion) {
        1f
    } else {
        rememberInfiniteTransition(label = "idleMicPulse")
            .animateFloat(
                initialValue = 0.72f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "idleMicAlpha",
            )
            .value
    }
    val pulseScale = if (reducedMotion || !isRecording) {
        1f
    } else {
        rememberInfiniteTransition(label = "recordPulse")
            .animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "recordPulseScale",
            )
            .value
    }
    val resultEnter = if (reducedMotion) {
        fadeIn(animationSpec = motionScheme.fastEffectsSpec())
    } else {
        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
            scaleIn(
                initialScale = 0.98f,
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
    }
    val resultExit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
        if (reducedMotion) {
            scaleOut(targetScale = 1f, animationSpec = motionScheme.fastSpatialSpec())
        } else {
            scaleOut(targetScale = 0.98f, animationSpec = motionScheme.fastSpatialSpec())
        }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Voice capture")
                    }
                }
                AnimatedContent(
                    targetState = statusTitle,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                        } else {
                            (slideInVertically(animationSpec = motionScheme.defaultSpatialSpec()) { it / 4 } +
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                (slideOutVertically(animationSpec = motionScheme.fastSpatialSpec()) { -it / 4 } +
                                    fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                        }
                    },
                    label = "homeStatusTitle",
                ) { title ->
                    Text(title, style = MaterialTheme.typography.displaySmallEmphasized)
                }
                AnimatedContent(
                    targetState = statusBody,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                        } else {
                            (slideInVertically(animationSpec = motionScheme.defaultSpatialSpec()) { it / 4 } +
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                (slideOutVertically(animationSpec = motionScheme.fastSpatialSpec()) { -it / 4 } +
                                    fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                        }
                    },
                    label = "homeStatusBody",
                ) { body ->
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 620.dp),
                    )
                }

                AnimatedVisibility(
                    visible = state.recordingState is RecordingState.Idle,
                    enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
                    exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = idlePulseAlpha),
                    )
                }

                AnimatedVisibility(
                    visible = state.recordingState is RecordingState.Idle && latestResultText == null,
                    enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
                    exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                "Quick start",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "Hold ${settingsState.hotkeyLabel} to record, release to transcribe and paste. You can also press the mic button below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.hasMicPermission && state.hasApiKey) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("All systems go") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        )
                    } else {
                        AssistChip(
                            onClick = onNavigateToSettings,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.SettingsVoice,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(if (state.hasMicPermission) "Mic ready" else "Mic needed") },
                        )
                        AssistChip(
                            onClick = onNavigateToSettings,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(if (state.hasApiKey) "Gemini linked" else "API key needed") },
                        )
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        leadingIcon = {
                            Icon(
                                Icons.Default.KeyboardCommandKey,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(settingsState.hotkeyLabel) },
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .scale(pulseScale)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                ),
                        )
                    }
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

            AnimatedVisibility(
                visible = latestResultText != null,
                enter = resultEnter,
                exit = resultExit,
            ) {
                ElevatedCard(modifier = Modifier.widthIn(max = 720.dp)) {
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
                            text = latestResultText.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}
