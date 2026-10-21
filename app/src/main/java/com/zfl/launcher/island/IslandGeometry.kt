package com.zfl.launcher.island

import com.zfl.launcher.ui.theme.IslandTokens
import kotlin.math.roundToInt

internal data class IslandGeometry(
    /** Horizontal offset of the camera (or screen center) from the display center. */
    val centerOffsetPx: Int,
    /** Top of the compact capsule: centered on the camera inside the status bar. */
    val topPx: Int,
    val widthDp: Float,
    val maxHeightDp: Float,
    val available: Boolean,
    val expansionAllowed: Boolean,
)

/**
 * [left]..[bottom] are stable system bar and cutout insets, [statusBarPx] the stable status bar
 * height, [cameraX]/[cameraY] the center of the top cutout (null without one) and [style] the
 * user's pill width, card margins and vertical offset.
 */
internal fun islandGeometry(
    widthPx: Int, heightPx: Int, leftPx: Int, rightPx: Int, bottomPx: Int, statusBarPx: Int,
    cameraX: Int?, cameraY: Int?, density: Float, fontScale: Float, style: IslandStyle = IslandStyle(),
): IslandGeometry {
    val scale = density.takeIf { it.isFinite() && it > 0 } ?: 1f
    val font = fontScale.takeIf { it.isFinite() && it > 0 } ?: 1f
    val compact = IslandTokens.CompactHeight.value * scale
    val centerY = cameraY ?: if (statusBarPx > 0) statusBarPx / 2 else (IslandTokens.EdgeGap.value * scale + compact / 2).roundToInt()
    val top = (centerY - compact / 2 + style.topOffset * scale).roundToInt().coerceAtLeast(0)
    val centerX = cameraX ?: (leftPx + (widthPx - leftPx - rightPx) / 2)
    val width = (widthPx - leftPx - rightPx) / scale - style.sideMargin * 2
    val height = (heightPx - bottomPx - top) / scale - IslandTokens.EdgeGap.value
    return IslandGeometry(
        centerOffsetPx = centerX - widthPx / 2,
        topPx = top,
        widthDp = width.coerceIn(0f, IslandTokens.CardWidth.value),
        maxHeightDp = height.coerceAtLeast(0f),
        available = width >= style.compactWidth && height >= IslandTokens.CompactHeight.value * font.coerceAtLeast(1f),
        expansionAllowed = widthPx < heightPx && width >= IslandTokens.MinCardWidth.value &&
            height >= IslandTokens.CARD_FIXED_HEIGHT_DP + IslandTokens.CARD_TEXT_HEIGHT_DP * font,
    )
}
