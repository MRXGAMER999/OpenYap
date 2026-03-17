package com.openyap.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppRailTreatment(
    val containerWidth: Dp,
    val showTitle: Boolean,
    val showLabels: Boolean,
) {
    Wide(containerWidth = 280.dp, showTitle = true, showLabels = true),
    Compact(containerWidth = 120.dp, showTitle = false, showLabels = true),
    CompactFallback(containerWidth = 88.dp, showTitle = false, showLabels = false),
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

    val isCompactFallbackRail: Boolean
        get() = railTreatment == AppRailTreatment.CompactFallback

    val prefersTopBarMenuFallback: Boolean
        get() = fallback == AppShellFallback.TopBarMenu
}

object AppShellBreakpoints {
    val wideRailMinWidth = 1200.dp
    val compactRailPreferredMinWidth = 900.dp
    val protectedContentMinWidth = 720.dp
    val shellHorizontalPadding = 32.dp
    val railContentGap = 16.dp
    val compactRailOccupiedWidth = 120.dp
    val compactFallbackRailOccupiedWidth = 88.dp
}

fun prefersWideRail(windowWidth: Dp): Boolean {
    return windowWidth >= AppShellBreakpoints.wideRailMinWidth
}

fun prefersCompactRail(windowWidth: Dp): Boolean {
    return !prefersWideRail(windowWidth) &&
        windowWidth >= AppShellBreakpoints.compactRailPreferredMinWidth
}

fun canProtectContentWithCompactFallback(windowWidth: Dp): Boolean {
    return windowWidth -
        AppShellBreakpoints.shellHorizontalPadding -
        AppShellBreakpoints.railContentGap -
        AppShellBreakpoints.compactFallbackRailOccupiedWidth >=
        AppShellBreakpoints.protectedContentMinWidth
}

fun resolveAppShellLayout(windowWidth: Dp): AppShellLayout {
    return when {
        prefersWideRail(windowWidth) -> AppShellLayout(
            railTreatment = AppRailTreatment.Wide,
            fallback = AppShellFallback.None,
        )

        prefersCompactRail(windowWidth) -> AppShellLayout(
            railTreatment = AppRailTreatment.Compact,
            fallback = AppShellFallback.None,
        )

        canProtectContentWithCompactFallback(windowWidth) -> AppShellLayout(
            railTreatment = AppRailTreatment.CompactFallback,
            fallback = AppShellFallback.None,
        )

        else -> AppShellLayout(
            railTreatment = null,
            fallback = AppShellFallback.TopBarMenu,
        )
    }
}
