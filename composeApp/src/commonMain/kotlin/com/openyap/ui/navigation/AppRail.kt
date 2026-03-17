package com.openyap.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openyap.ui.theme.Spacing
import com.openyap.ui.theme.reducedMotionEnabled

private val RailItemHeight = 56.dp
private val RailItemSpacing = 4.dp
private val RailItemSlot = RailItemHeight + RailItemSpacing
private val ExpandedIndicatorWidth = 216.dp
private val CollapsedIndicatorWidth = 56.dp

@Immutable
data class AppRailPrimaryAction(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * Navigation rail with an animated sliding indicator that uses M3 Expressive
 * spring physics. The indicator stretches asymmetrically: the leading edge
 * springs ahead while the trailing edge follows, creating an organic morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRail(
    destinations: List<RailDestination>,
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = remember(currentRoute, destinations) {
        destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = reducedMotionEnabled()

    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    val movingDown = selectedIndex > previousIndex
    SideEffect { previousIndex = selectedIndex }

    val targetTopDp = RailItemSlot * selectedIndex
    val targetBottomDp = targetTopDp + RailItemHeight

    val indicatorTop by animateDpAsState(
        targetValue = targetTopDp,
        animationSpec = when {
            reducedMotion -> snap()
            movingDown -> motionScheme.defaultSpatialSpec()
            else -> motionScheme.fastSpatialSpec()
        },
        label = "indicatorTop",
    )
    val indicatorBottom by animateDpAsState(
        targetValue = targetBottomDp,
        animationSpec = when {
            reducedMotion -> snap()
            movingDown -> motionScheme.fastSpatialSpec()
            else -> motionScheme.defaultSpatialSpec()
        },
        label = "indicatorBottom",
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isExpanded) ExpandedIndicatorWidth else CollapsedIndicatorWidth,
        animationSpec = if (reducedMotion) snap() else motionScheme.defaultSpatialSpec(),
        label = "indicatorWidth",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val indicatorHeight = (indicatorBottom - indicatorTop).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .offset(y = indicatorTop)
                .width(indicatorWidth)
                .height(indicatorHeight)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RailItemSpacing),
        ) {
            destinations.forEachIndexed { index, destination ->
                RailItem(
                    destination = destination,
                    isSelected = index == selectedIndex,
                    isExpanded = isExpanded,
                    onClick = { onRouteSelected(destination.route) },
                    modifier = Modifier.height(RailItemHeight).fillMaxWidth(),
                    reducedMotion = reducedMotion,
                )
            }
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
private fun RailItem(
    destination: RailDestination,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val motionScheme = MaterialTheme.motionScheme
    val contentColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec(),
        label = "railItemColor",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.spacedBy(Spacing.md) else Arrangement.Center,
            modifier = if (isExpanded) Modifier.fillMaxWidth().padding(start = Spacing.xs) else Modifier,
        ) {
            Icon(
                imageVector = destination.iconFor(isSelected),
                contentDescription = if (!isExpanded) destination.label else null,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            if (isExpanded) {
                Text(
                    text = destination.label,
                    style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized
                    else MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun RailDestination.iconFor(selected: Boolean): ImageVector {
    return if (selected) selectedIcon else unselectedIcon
}
