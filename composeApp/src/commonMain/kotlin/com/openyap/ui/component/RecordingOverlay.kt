package com.openyap.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openyap.model.OverlayState

private val OverlayBarBg = Color(0xFF1A1A1A)

@Composable
fun RecordingOverlay(
    state: OverlayState,
    level: Float,
    durationSeconds: Int,
    flashMessage: String?,
) {
    val pillShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .padding(32.dp) // Provide room for Android/Desktop Window borders to NOT clip the shadow and spring bounce
            .shadow(
                elevation = 20.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.4f),
                clip = true
            )
            .background(OverlayBarBg.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), pillShape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) togetherWith
                            fadeOut(animationSpec = tween(150)))
                        .using(SizeTransform(clip = false))
                },
                label = "flowBar",
            ) { s ->
                when (s) {
                    OverlayState.RECORDING,
                    OverlayState.PROCESSING -> ActiveBar(
                        state = s,
                        level = level,
                        durationSeconds = durationSeconds,
                    )

                    OverlayState.SUCCESS -> SuccessBar()
                    OverlayState.ERROR -> ErrorBar()
                }
            }

            var lastFlashMessage by remember { mutableStateOf<String?>(null) }
            if (flashMessage != null) lastFlashMessage = flashMessage

            AnimatedVisibility(
                visible = flashMessage != null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(spring(stiffness = Spring.StiffnessLow)) +
                        slideInVertically(spring(stiffness = Spring.StiffnessLow)) { it / 2 },
                exit = fadeOut(tween(200)) +
                        shrinkVertically(tween(200)) +
                        slideOutVertically(tween(200)) { it / 2 },
            ) {
                lastFlashMessage?.let { msg ->
                    FlashMessageRow(message = msg, state = state)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ActiveBar(
    state: OverlayState,
    level: Float,
    durationSeconds: Int,
) {
    Row(
        modifier = Modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
            },
            label = "overlayLeading",
        ) { currentState ->
            when (currentState) {
                OverlayState.RECORDING -> WaveformBars(level)
                OverlayState.PROCESSING -> CircularWavyProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.78f),
                )

                else -> Unit
            }
        }

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
            },
            label = "overlayLabel",
        ) { currentState ->
            when (currentState) {
                OverlayState.RECORDING -> Text(
                    text = formatDuration(durationSeconds),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 0.5.sp,
                )

                OverlayState.PROCESSING -> Text(
                    text = "Refining...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp,
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun SuccessBar() {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "success_scale"
    )

    Row(
        modifier = Modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(
            text = "Pasted ✓",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ErrorBar() {
    Row(
        modifier = Modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "Could not paste",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FlashMessageRow(message: String, state: OverlayState) {
    val (icon, tint) = when (state) {
        OverlayState.SUCCESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        OverlayState.ERROR -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.error
        else -> Icons.Default.WarningAmber to Color.White.copy(alpha = 0.72f)
    }

    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
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
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 500f
        ),
        label = "lvl",
    )

    val effectiveLevel = if (animLevel < 0.05f) 0f else animLevel

    val brush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.5f), MaterialTheme.colorScheme.tertiary)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barOffsets.forEachIndexed { _, anim ->
            val h = if (effectiveLevel == 0f) 4.dp else (4 + 32 * anim.value * effectiveLevel).dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(brush)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
