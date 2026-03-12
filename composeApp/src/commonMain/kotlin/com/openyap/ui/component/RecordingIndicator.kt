package com.openyap.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openyap.model.RecordingState
import androidx.compose.animation.core.animateFloatAsState

@Composable
fun RecordingIndicator(
    recordingState: RecordingState,
    amplitude: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = recordingState !is RecordingState.Idle

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val backgroundColor = when (recordingState) {
            is RecordingState.Recording -> MaterialTheme.colorScheme.errorContainer
            is RecordingState.Processing -> MaterialTheme.colorScheme.primaryContainer
            is RecordingState.Success -> MaterialTheme.colorScheme.secondaryContainer
            is RecordingState.Error -> MaterialTheme.colorScheme.errorContainer
            is RecordingState.Idle -> Color.Transparent
        }

        val contentColor = when (recordingState) {
            is RecordingState.Recording -> MaterialTheme.colorScheme.onErrorContainer
            is RecordingState.Processing -> MaterialTheme.colorScheme.onPrimaryContainer
            is RecordingState.Success -> MaterialTheme.colorScheme.onSecondaryContainer
            is RecordingState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is RecordingState.Idle -> Color.Transparent
        }

        val statusText = when (recordingState) {
            is RecordingState.Recording -> "Recording... ${recordingState.durationSeconds}s"
            is RecordingState.Processing -> "Processing with Gemini..."
            is RecordingState.Success -> "Pasted ${recordingState.charCount} chars"
            is RecordingState.Error -> recordingState.message
            is RecordingState.Idle -> ""
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (recordingState is RecordingState.Recording) {
                AmplitudeBar(amplitude = amplitude, color = contentColor)
            }
            if (recordingState is RecordingState.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            }

            Text(
                text = statusText,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )

            if (recordingState is RecordingState.Recording || recordingState is RecordingState.Processing) {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun AmplitudeBar(amplitude: Float, color: Color) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(100),
    )
    val barCount = 5
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until barCount) {
            val threshold = i.toFloat() / barCount
            val barHeight = if (animatedAmplitude > threshold) {
                (8 + (12 * ((animatedAmplitude - threshold) * barCount).coerceAtMost(1f))).dp
            } else 4.dp

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (animatedAmplitude > threshold) 1f else 0.4f)),
            )
        }
    }
}
