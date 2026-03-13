package com.openyap.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openyap.model.RecordingState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordingIndicator(
    recordingState: RecordingState,
    amplitude: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onErrorDismissed: (() -> Unit)? = null,
) {
    if (recordingState is RecordingState.Error) {
        LaunchedEffect(recordingState.message) {
            delay(4000)
            onErrorDismissed?.invoke()
        }
    }

    val isVisible = recordingState !is RecordingState.Idle
    val backgroundColor by animateColorAsState(
        targetValue = when (recordingState) {
            is RecordingState.Recording -> MaterialTheme.colorScheme.errorContainer
            is RecordingState.Processing -> MaterialTheme.colorScheme.primaryContainer
            is RecordingState.Success -> MaterialTheme.colorScheme.tertiaryContainer
            is RecordingState.Error -> MaterialTheme.colorScheme.errorContainer
            is RecordingState.Idle -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(),
        label = "indicatorBackground",
    )
    val scale by animateFloatAsState(
        targetValue = when (recordingState) {
            is RecordingState.Recording -> 1.02f + (amplitude.coerceIn(0f, 1f) * 0.08f)
            is RecordingState.Processing -> 1.01f
            else -> 1f
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "indicatorScale",
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.95f),
        exit = slideOutVertically { -it } + fadeOut() + scaleOut(targetScale = 0.95f),
        modifier = modifier,
    ) {
        Surface(
            color = backgroundColor,
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.scale(scale),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (recordingState) {
                    is RecordingState.Recording -> AmplitudeBar(
                        amplitude = amplitude,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )

                    is RecordingState.Processing -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    else -> Unit
                }

                Text(
                    text = when (recordingState) {
                        is RecordingState.Recording -> "Recording ${recordingState.durationSeconds}s"
                        is RecordingState.Processing -> "Refining with Gemini"
                        is RecordingState.Success -> "Pasted ${recordingState.charCount} chars"
                        is RecordingState.Error -> recordingState.message
                        is RecordingState.Idle -> ""
                    },
                    color = when (recordingState) {
                        is RecordingState.Recording -> MaterialTheme.colorScheme.onErrorContainer
                        is RecordingState.Processing -> MaterialTheme.colorScheme.onPrimaryContainer
                        is RecordingState.Success -> MaterialTheme.colorScheme.onTertiaryContainer
                        is RecordingState.Error -> MaterialTheme.colorScheme.onErrorContainer
                        is RecordingState.Idle -> Color.Transparent
                    },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )

                if (recordingState is RecordingState.Recording) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${(amplitude.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.alpha(0.9f),
                    )
                }

                if (recordingState is RecordingState.Recording || recordingState is RecordingState.Processing) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = when (recordingState) {
                                is RecordingState.Recording -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmplitudeBar(amplitude: Float, color: Color) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "amplitude",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(7) { index ->
            val threshold = index.toFloat() / 7f
            val barHeight = if (animatedAmplitude > threshold) {
                (10 + 22 * ((animatedAmplitude - threshold) * 7f).coerceAtMost(1f)).dp
            } else {
                6.dp
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color.copy(alpha = if (animatedAmplitude > threshold) 1f else 0.35f)),
            )
        }
    }
}
