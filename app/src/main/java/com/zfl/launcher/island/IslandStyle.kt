package com.zfl.launcher.island

import androidx.compose.runtime.staticCompositionLocalOf
import com.zfl.launcher.settings.IslandPreferences

/** How the Island looks, from the user's settings. Sizes in dp. */
data class IslandStyle(
    val blur: Boolean = true,
    val artworkTint: Boolean = true,
    val compactWidth: Float = IslandPreferences.DEFAULT_COMPACT_WIDTH,
    val sideMargin: Float = IslandPreferences.DEFAULT_SIDE_MARGIN,
    val topOffset: Float = 0f,
)

fun IslandPreferences.style(): IslandStyle = IslandStyle(blur, artworkTint, compactWidth, sideMargin, topOffset)

fun IslandPreferences.environment(window: Boolean, notifications: Boolean, connected: Boolean): IslandEnvironment =
    IslandEnvironment(enabled, window, notifications, connected, charging, timers, alwaysOn, (collapseSeconds * 1000).toLong())

/** The style of the Island being drawn, for the parts that size themselves. */
internal val LocalIslandStyle = staticCompositionLocalOf { IslandStyle() }
