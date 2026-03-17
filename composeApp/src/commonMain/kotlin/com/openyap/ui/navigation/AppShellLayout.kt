package com.openyap.ui.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

enum class AppRailTreatment(
    val containerWidth: Dp,
    val showTitle: Boolean,
    val showLabels: Boolean,
) {
    Wide(containerWidth = 280.dp, showTitle = true, showLabels = true),
    Compact(containerWidth = 120.dp, showTitle = false, showLabels = true),
}

enum class AppShellFallback {
    None,
    TopBarMenu,
}

@Immutable
data class AppShellLayout(
    val railTreatment: AppRailTreatment?,
    val fallback: AppShellFallback,
) {
    val showsRail: Boolean
        get() = railTreatment != null

    val isWideRail: Boolean
        get() = railTreatment == AppRailTreatment.Wide

    val isCompactRail: Boolean
        get() = railTreatment == AppRailTreatment.Compact

    val prefersTopBarMenuFallback: Boolean
        get() = fallback == AppShellFallback.TopBarMenu
}

/**
 * Returns the [AppShellLayout] for the current window, derived from the
 * Material 3 [WindowWidthSizeClass] rather than manual dp thresholds.
 *
 * | Window width class  | Rail treatment  | Fallback   |
 * |---------------------|-----------------|------------|
 * | EXPANDED (≥ 840 dp) | Wide (280 dp)   | None       |
 * | MEDIUM (600–840 dp) | Compact (120 dp)| None       |
 * | COMPACT (< 600 dp)  | —               | TopBarMenu |
 *
 * Using the official adaptive library instead of bespoke dp constants means
 * the breakpoints stay in sync with M3 guidance automatically. This replaces
 * the old `AppShellBreakpoints` / `resolveAppShellLayout` system and removes
 * the four manual helper functions that were reimplementing what
 * [WindowWidthSizeClass] already encodes.
 */
@Composable
fun rememberAdaptiveShellLayout(): AppShellLayout {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    return remember(widthClass) {
        when (widthClass) {
            WindowWidthSizeClass.EXPANDED -> AppShellLayout(
                railTreatment = AppRailTreatment.Wide,
                fallback = AppShellFallback.None,
            )
            WindowWidthSizeClass.MEDIUM -> AppShellLayout(
                railTreatment = AppRailTreatment.Compact,
                fallback = AppShellFallback.None,
            )
            else -> AppShellLayout(
                railTreatment = null,
                fallback = AppShellFallback.TopBarMenu,
            )
        }
    }
}
