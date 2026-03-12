package com.openyap.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    val icon: ImageVector,
)

val railRoutes = listOf(
    RailDestination(Route.Home, "Home", Icons.Default.Home),
    RailDestination(Route.History, "History", Icons.Default.History),
    RailDestination(Route.Dictionary, "Dictionary", Icons.Default.Book),
    RailDestination(Route.UserInfo, "User Info", Icons.Default.Person),
    RailDestination(Route.Stats, "Stats", Icons.Default.BarChart),
    RailDestination(Route.Customization, "Per-App", Icons.Default.Tune),
    RailDestination(Route.Settings, "Settings", Icons.Default.Settings),
)
