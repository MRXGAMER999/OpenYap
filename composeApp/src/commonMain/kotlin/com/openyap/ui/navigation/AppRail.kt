package com.openyap.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.openyap.ui.theme.reducedMotionEnabled

@Immutable
data class AppRailPrimaryAction(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRail(
    destinations: List<RailDestination>,
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
    railState: WideNavigationRailState,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
) {
    val isExpanded = railState.targetValue == WideNavigationRailValue.Expanded
    val colors = wideRailItemColors()

    WideNavigationRail(
        state = railState,
        modifier = modifier,
        colors = WideNavigationRailDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        header = header,
    ) {
        destinations.forEach { destination ->
            val isSelected = currentRoute == destination.route
            WideNavigationRailItem(
                selected = isSelected,
                onClick = { onRouteSelected(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.iconFor(isSelected),
                        contentDescription = if (!isExpanded) destination.label else null,
                    )
                },
                label = { Text(destination.label) },
                railExpanded = isExpanded,
                colors = colors,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RailToggleButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = reducedMotionEnabled()
    val motionScheme = MaterialTheme.motionScheme

    IconButton(onClick = onToggle, modifier = modifier) {
        Crossfade(
            targetState = isExpanded,
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec(),
            label = "railToggleIcon",
        ) { expanded ->
            Icon(
                imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                contentDescription = if (expanded) "Collapse navigation" else "Expand navigation",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun wideRailItemColors() = WideNavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
)

private fun RailDestination.iconFor(selected: Boolean): ImageVector {
    return if (selected) selectedIcon else unselectedIcon
}
