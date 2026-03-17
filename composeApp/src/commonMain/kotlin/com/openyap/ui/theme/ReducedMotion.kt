package com.openyap.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

expect fun platformPrefersReducedMotion(): Boolean

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun reducedMotionEnabled(): Boolean = LocalReducedMotion.current
