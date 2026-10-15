package com.zfl.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens. Values are measured from the reference screenshots, see docs/DESIGN.md. */
object Tokens {
    val IconSize = 70.dp
    const val IconCornerRatio = 0.225f
    const val GridColumns = 4
    const val GridRows = 6
    val GridSidePadding = 22.dp
    val LabelSize = 13.sp

    val DockHeight = 108.dp
    val DockRadius = 38.dp
    val DockSideMargin = 13.dp
    val DockBottomMargin = 14.dp

    val PillHeight = 26.dp
    val SearchSwipeThreshold = 56.dp

    val GlassTint = Color.White.copy(alpha = 0.12f)
    val GlassFallback = Color(0xB32D2D2A)
    val GlassStroke = Color.White.copy(alpha = 0.35f)

    val MenuBackground = Color(0xF21C1C1E)
    val MenuStroke = Color.White.copy(alpha = 0.16f)
    val MenuWidth = 230.dp
    val MenuItemHeight = 40.dp
    val MenuRadius = 24.dp
    val MenuVerticalPadding = 6.dp
    val WidgetBackground = Color(0xFF1C1C1D)

    val Destructive = Color(0xFFFF453A)
    val Accent = Color(0xFF0A84FF)
    val Scrim = Color.Black.copy(alpha = 0.45f)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.6f)
}
