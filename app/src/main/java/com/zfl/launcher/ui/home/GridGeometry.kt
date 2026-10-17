package com.zfl.launcher.ui.home

import androidx.compose.ui.geometry.Offset
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.data.Span

/**
 * Window-space layout of a grid of cells (a home screen page or an open folder), in px.
 * Cells are logical ([GridSpec.cell]); in a right-to-left layout column 0 is drawn on the
 * right, so every conversion between a screen position and a cell mirrors the column.
 */
data class GridGeometry(
    /** Window position of the grid's left edge and first row. */
    val origin: Offset,
    val cellWidth: Float,
    val cellHeight: Float,
    val grid: GridSpec,
    val iconSize: Float,
    val rtl: Boolean = false,
) {
    val columns: Int get() = grid.columns
    val rows: Int get() = grid.rows

    /** The logical cell under [p], clamped to the grid. */
    fun cellAt(p: Offset): Int {
        val visual = ((p.x - origin.x) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((p.y - origin.y) / cellHeight).toInt().coerceIn(0, rows - 1)
        return grid.cell(row, if (rtl) columns - 1 - visual else visual)
    }

    /**
     * Logical top-left cell of a [span] footprint whose top-left corner on screen is
     * [topLeft], snapped to the nearest cell. In RTL the footprint's left edge on screen is
     * its last logical column.
     */
    fun footprintCell(topLeft: Offset, span: Span): Int {
        val visual = ((topLeft.x - origin.x) / cellWidth + 0.5f).toInt().coerceIn(0, (columns - span.columns).coerceAtLeast(0))
        val row = ((topLeft.y - origin.y) / cellHeight + 0.5f).toInt().coerceIn(0, (rows - span.rows).coerceAtLeast(0))
        return grid.cell(row, if (rtl) columns - span.columns - visual else visual)
    }

    /** Window x of the left edge of a [span] footprint starting at logical [cell]. */
    fun footprintLeft(cell: Int, span: Span = Span.One): Float {
        val column = grid.columnOf(cell)
        val visual = if (rtl) columns - column - span.columns else column
        return origin.x + visual * cellWidth
    }

    /** Center of a [span] footprint starting at [cell]. */
    fun footprintCenter(cell: Int, span: Span): Offset = Offset(
        footprintLeft(cell, span) + span.columns * cellWidth / 2,
        origin.y + (grid.rowOf(cell) + span.rows / 2f) * cellHeight,
    )

    /** Center of the icon drawn in [cell] (icons sit at the top center of their cell). */
    fun iconCenter(cell: Int): Offset = Offset(
        footprintLeft(cell) + cellWidth / 2,
        origin.y + grid.rowOf(cell) * cellHeight + iconSize / 2,
    )
}
