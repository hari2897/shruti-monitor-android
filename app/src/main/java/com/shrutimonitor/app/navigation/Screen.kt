package com.shrutimonitor.app.navigation

/**
 * Sealed class defining the navigation routes in the Shruti Monitor app.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permission : Screen("permission")
    object Monitor : Screen("monitor")
    object Play : Screen("play")
    object Ragas : Screen("ragas")
    object Settings : Screen("settings")
}
