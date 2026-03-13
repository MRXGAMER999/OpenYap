package com.openyap.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openyap.model.OverlayState

private val BarColor = Color.White
private val BarBg = Color(0xFF1A1A1A)
private val AccentGreen = Color(0xFF4ADE80)

@Composable
fun RecordingOverlay(
    state: OverlayState,
    level: Float,
    durationSeconds: Int,
    flashMessage: String?,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BarBg.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "flowBar",
        ) { s ->
            when (s) {
                OverlayState.RECORDING -> RecordingBar(level, durationSeconds)
                OverlayState.PROCESSING -> ProcessingBar()
                OverlayState.SUCCESS -> SuccessBar()
            }
        }

        var lastFlashMessage by remember { mutableStateOf<String?>(null) }
        if (flashMessage != null) lastFlashMessage = flashMessage

        AnimatedVisibility(
            visible = flashMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            lastFlashMessage?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun RecordingBar(level: Float, durationSeconds: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WaveformBars(level)
        Text(
            formatDuration(durationSeconds),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ProcessingBar() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 1.5.dp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            "refining",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun SuccessBar() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(AccentGreen),
        )
        Text(
            "pasted",
            fontSize = 12.sp,
            color = AccentGreen,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun WaveformBars(level: Float) {
    val transition = rememberInfiniteTransition(label = "wave")
    val barCount = 5
    val barOffsets = (0 until barCount).map { i ->
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + i * 60,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }

    val animLevel by animateFloatAsState(
        targetValue = level.coerceIn(0.05f, 1f),
        animationSpec = tween(80),
        label = "lvl",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barOffsets.forEachIndexed { _, anim ->
            val h = (4 + 14 * anim.value * animLevel).dp
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BarColor.copy(alpha = 0.85f)),
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
