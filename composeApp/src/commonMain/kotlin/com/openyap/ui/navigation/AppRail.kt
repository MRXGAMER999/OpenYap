package com.openyap.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.openyap.ui.theme.Spacing

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
    modifier: Modifier = Modifier,
    treatment: AppRailTreatment = AppRailTreatment.Compact,
    title: String = "OpenYap",
    primaryAction: AppRailPrimaryAction? = null,
) {
    val hasHeader = title.isNotBlank() || primaryAction != null
    when (treatment) {
        AppRailTreatment.Wide -> WideAppRail(
            destinations = destinations,
            currentRoute = currentRoute,
            onRouteSelected = onRouteSelected,
            modifier = modifier,
            title = title,
            primaryAction = primaryAction,
            hasHeader = hasHeader,
        )

        AppRailTreatment.Compact,
        AppRailTreatment.CompactFallback,
        -> CompactAppRail(
            destinations = destinations,
            currentRoute = currentRoute,
            onRouteSelected = onRouteSelected,
            modifier = modifier,
            treatment = treatment,
            title = title,
            primaryAction = primaryAction,
            hasHeader = hasHeader,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideAppRail(
    destinations: List<RailDestination>,
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
    modifier: Modifier,
    title: String,
    primaryAction: AppRailPrimaryAction?,
    hasHeader: Boolean,
) {
    val colors = wideRailItemColors()
    WideNavigationRail(
        modifier = modifier.width(AppRailTreatment.Wide.containerWidth),
        state = rememberWideNavigationRailState(WideNavigationRailValue.Expanded),
        colors = WideNavigationRailDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        header = if (hasHeader) {
            {
                AppRailHeader(
                    treatment = AppRailTreatment.Wide,
                    title = title,
                    primaryAction = primaryAction,
                )
            }
        } else {
            null
        },
    ) {
        destinations.forEach { destination ->
            WideNavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onRouteSelected(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.iconFor(currentRoute == destination.route),
                        contentDescription = if (AppRailTreatment.Wide.showLabels) null else destination.label,
                    )
                },
                label = { Text(destination.label) },
                railExpanded = true,
                colors = colors,
            )
        }
    }
}

@Composable
private fun CompactAppRail(
    destinations: List<RailDestination>,
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
    modifier: Modifier,
    treatment: AppRailTreatment,
    title: String,
    primaryAction: AppRailPrimaryAction?,
    hasHeader: Boolean,
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(treatment.containerWidth),
        containerColor = Color.Transparent,
        header = if (hasHeader) {
            {
                AppRailHeader(
                    treatment = treatment,
                    title = title,
                    primaryAction = primaryAction,
                )
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            destinations.forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onRouteSelected(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.iconFor(currentRoute == destination.route),
                            contentDescription = if (treatment.showLabels) null else destination.label,
                        )
                    },
                    label = if (treatment.showLabels) {
                        { Text(destination.label, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        null
                    },
                    alwaysShowLabel = treatment.showLabels,
                    colors = railItemColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRailHeader(
    treatment: AppRailTreatment,
    title: String,
    primaryAction: AppRailPrimaryAction?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
                primaryAction?.let {
            AppRailPrimaryActionChip(action = it, treatment = treatment)
        }
        if (treatment.showTitle) {
            Text(
                text = title,
                style = if (treatment == AppRailTreatment.Wide) {
                    MaterialTheme.typography.titleLargeEmphasized
                } else {
                    MaterialTheme.typography.titleMediumEmphasized
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRailPrimaryActionChip(
    action: AppRailPrimaryAction,
    treatment: AppRailTreatment,
) {
    Surface(
        onClick = { action.onClick?.invoke() },
        enabled = action.enabled && action.onClick != null,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = if (treatment == AppRailTreatment.Wide) {
            MaterialTheme.shapes.medium
        } else {
            MaterialTheme.shapes.medium
        },
    ) {
        if (treatment == AppRailTreatment.Wide) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(action.label, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun railItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
)

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
