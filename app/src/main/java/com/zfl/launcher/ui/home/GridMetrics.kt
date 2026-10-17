package com.zfl.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.settings.AreaLook
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.GridMargins
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.icon.rememberLabelLineHeight
import com.zfl.launcher.ui.theme.Tokens

/** Icons and names of one area (home pages, dock or folders) at the current density and font scale. */
@Immutable
data class AreaMetrics(
    val iconSize: Dp,
    val labelScale: Float,
    val showLabels: Boolean,
    /** [LabelGap] and one line of name text; zero without names. */
    val labelBlock: Dp,
)

/** The resolved look of [area]; each area passes its own metrics down, nothing reads them from a parent. */
@Composable
fun rememberAreaMetrics(settings: SettingsState, area: IconArea): AreaMetrics {
    val look: AreaLook = settings.look(area)
    val line = rememberLabelLineHeight(look.labelScale)
    return remember(look, line) { areaMetrics(look, line) }
}

fun areaMetrics(look: AreaLook, labelLine: Dp): AreaMetrics =
    AreaMetrics(look.iconSize, look.labelScale, look.showLabels, if (look.showLabels) LabelGap + labelLine else 0.dp)

/**
 * Where the cells of a grid go inside the area left for it, in dp from the area's top-start
 * corner. One value serves measuring, drawing, hit tests, the drop marker and the settings
 * preview, so they can't disagree.
 */
@Immutable
data class GridFrame(
    val grid: GridSpec,
    /** Effective margins and the grid's offset from the top (margin plus alignment). */
    val side: Dp,
    val top: Dp,
    val cellWidth: Dp,
    val cellHeight: Dp,
    /** Icon size drawn in this grid: the requested one, smaller when the cells are too small for it. */
    val iconSize: Dp,
    /** False when a name doesn't fit under the icon; the saved choice stays as it is. */
    val showLabels: Boolean,
) {
    val width: Dp get() = cellWidth * grid.columns
    val height: Dp get() = cellHeight * grid.rows

    /** Width of an icon's touch area: the icon with a margin, never wider than its cell. */
    val itemWidth: Dp get() = min(iconSize + ItemSideMargin * 2, cellWidth)
}

/** Extra width of an icon's touch area (and label) on each side of the icon. */
private val ItemSideMargin = 8.dp

/**
 * Lays [grid] out in [area]. Margins that would leave less than a touch target per cell are
 * reduced for this layout only (a split screen, a big font); the saved values don't change.
 * Cells grow to fill the height up to a cap that moves with the icon size; when the cap is
 * reached the grid sits at the top, the middle or the bottom of the free space ([alignment]).
 */
fun gridFrame(
    area: DpSize,
    grid: GridSpec,
    margins: GridMargins,
    alignment: GridAlignment,
    metrics: AreaMetrics,
    maxCellHeight: Dp = Tokens.MaxCellHeight,
): GridFrame {
    val width = max(area.width, 0.dp)
    val height = max(area.height, 0.dp)
    val side = margins.side.coerceIn(0.dp, max((width - Tokens.MinCellSize * grid.columns) / 2, 0.dp))
    // Top and bottom give way in proportion, so the rows keep at least a touch target each.
    val vertical = margins.top + margins.bottom
    val room = max(height - Tokens.MinCellSize * grid.rows, 0.dp)
    val shrink = if (vertical > room && vertical > 0.dp) room / vertical else 1f
    val top = margins.top * shrink
    val bottom = margins.bottom * shrink
    val cellWidth = (width - side * 2) / grid.columns
    val available = max(height - top - bottom, 0.dp)
    // Bigger icons need taller cells; the cap moves with them.
    val cellHeight = min(available / grid.rows, max(maxCellHeight + (metrics.iconSize - Tokens.IconSize), 0.dp))
    val free = available - cellHeight * grid.rows
    val offset = when (alignment) {
        GridAlignment.Top -> 0.dp
        GridAlignment.Center -> free / 2
        GridAlignment.Bottom -> free
    }
    val labels = metrics.showLabels && cellHeight - metrics.labelBlock >= Tokens.MinIconSize
    val fitHeight = if (labels) cellHeight - metrics.labelBlock else cellHeight
    val icon = min(metrics.iconSize, min(cellWidth - Tokens.MinIconGap, fitHeight))
        .coerceAtLeast(min(Tokens.MinIconSize, min(cellWidth, cellHeight)))
    return GridFrame(grid, side, top + offset, cellWidth, cellHeight, icon, labels)
}

/** Dock icon size: the dock's own, smaller when [slots] icons wouldn't fit [width] with a gap between them. */
fun dockIconSize(width: Dp, slots: Int, metrics: AreaMetrics): Dp =
    min(metrics.iconSize, max(width / slots.coerceAtLeast(1) - Tokens.MinIconGap, Tokens.MinIconSize))

/** What one icon in a grid or the dock is drawn with. */
@Immutable
data class IconSlot(
    val iconSize: Dp,
    /** Width of the icon's touch area and name. */
    val width: Dp,
    val showLabel: Boolean,
    val labelScale: Float,
)

fun GridFrame.iconSlot(metrics: AreaMetrics): IconSlot = IconSlot(iconSize, itemWidth, showLabels, metrics.labelScale)
