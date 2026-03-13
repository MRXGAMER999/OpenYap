package com.openyap.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Dictionary : Route

    @Serializable
    data object UserInfo : Route

    @Serializable
    data object Stats : Route

    @Serializable
    data object Customization : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Onboarding : Route
}

data class RailDestination(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val railRoutes = listOf(
    RailDestination(Route.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    RailDestination(Route.History, "History", Icons.Filled.History, Icons.Outlined.History),
    RailDestination(Route.Dictionary, "Dictionary", Icons.Filled.Book, Icons.Outlined.Book),
    RailDestination(Route.UserInfo, "User Info", Icons.Filled.Person, Icons.Outlined.Person),
    RailDestination(Route.Stats, "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    RailDestination(Route.Customization, "Per-App", Icons.Filled.Tune, Icons.Outlined.Tune),
    RailDestination(Route.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)
