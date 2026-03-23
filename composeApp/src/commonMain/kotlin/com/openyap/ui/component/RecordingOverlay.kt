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
import androidx.compose.foundation.layout.widthIn
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

// ── Overlay-specific palette (always dark glass, independent of app theme) ──
private val OverlayBg = Color(0x401A1A2E) // 25% opacity
private val OverlayText = Color.White
private val OverlayAccent = Color(0xFF76D1C1)   // teal – matches app primary
private val OverlaySuccess = Color(0xFF4ADE80)   // green
private val OverlayBorder = Color(0x66FFFFFF) // 40% white
private val OverlayRimHighlight = Color(0x33FFFFFF) // 20% white highlight

@Composable
fun RecordingOverlay(
    state: OverlayState,
    level: Float,
    processingMessage: String,
    flashMessage: String?,
) {
    val capsuleShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .padding(4.dp)
            .shadow(
                elevation = 12.dp,
                shape = capsuleShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.3f),
                clip = false
            )
            .clip(capsuleShape)
            .background(OverlayBg)
            // Outer Refractive Rim
            .border(0.5.dp, OverlayBorder, capsuleShape)
            // Inner Lens Thickness Simulation
            .padding(1.dp)
            .border(0.5.dp, OverlayRimHighlight, capsuleShape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(animationSpec = tween(120)))
                        .using(SizeTransform(clip = false))
                },
                label = "flowBar",
            ) { s ->
                when (s) {
                    OverlayState.RECORDING,
                    OverlayState.PROCESSING -> ActiveBar(
                        state = s,
                        level = level,
                        processingMessage = processingMessage,
                    )

                    OverlayState.SUCCESS -> SuccessBar()
                    OverlayState.ERROR -> ErrorBar()
                }
            }

            var lastFlashMessage by remember { mutableStateOf<String?>(null) }
            if (flashMessage != null) lastFlashMessage = flashMessage

            AnimatedVisibility(
                visible = flashMessage != null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        expandVertically(spring(stiffness = Spring.StiffnessMedium)) +
                        slideInVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 2 },
                exit = fadeOut(tween(150)) +
                        shrinkVertically(tween(150)) +
                        slideOutVertically(tween(150)) { it / 2 },
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
    processingMessage: String,
) {
    Row(
        modifier = Modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            },
            label = "overlayLeading",
        ) { currentState ->
            when (currentState) {
                OverlayState.RECORDING -> WaveformBars(level)
                OverlayState.PROCESSING -> CircularWavyProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = OverlayAccent,
                )

                else -> Unit
            }
        }

        if (state == OverlayState.PROCESSING) {
            Text(
                text = processingMessage,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OverlayAccent.copy(alpha = 0.85f),
                letterSpacing = 0.5.sp,
            )
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
            stiffness = Spring.StiffnessMedium
        ),
        label = "success_scale"
    )

    Row(
        modifier = Modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(OverlaySuccess)
        )
        Text(
            text = "Pasted",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = OverlaySuccess.copy(alpha = 0.9f),
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ErrorBar() {
    Row(
        modifier = Modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
        )
        Text(
            text = "Could not paste",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun FlashMessageRow(message: String, state: OverlayState) {
    val errorColor = MaterialTheme.colorScheme.error
    val (icon, tint) = when (state) {
        OverlayState.SUCCESS -> Icons.Default.CheckCircle to OverlaySuccess
        OverlayState.ERROR -> Icons.Default.WarningAmber to errorColor
        else -> Icons.Default.WarningAmber to OverlayText.copy(alpha = 0.60f)
    }

    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = tint,
        )
        Text(
            text = message,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = OverlayText.copy(alpha = 0.60f),
        )
    }
}

@Composable
private fun WaveformBars(level: Float) {
    val transition = rememberInfiniteTransition(label = "wave")
    val barCount = 7
    val barOffsets = (0 until barCount).map { i ->
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + i * 50,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }

    val animLevel by animateFloatAsState(
        targetValue = (level * 2f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lvl",
    )

    val effectiveLevel = if (animLevel < 0.1f) 0.1f else animLevel

    val brush = Brush.verticalGradient(
        colors = listOf(OverlayAccent.copy(alpha = 0.6f), OverlayAccent)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barOffsets.forEachIndexed { _, anim ->
            val h = (5 + 15 * anim.value * effectiveLevel).dp
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(brush)
            )
        }
    }
}
