package com.zfl.launcher.ui.home

import androidx.compose.ui.geometry.Offset

/** Window-space layout of a grid of cells (a home screen page or an open folder). */
data class GridGeometry(
    val origin: Offset,
    val cellWidth: Float,
    val cellHeight: Float,
    val columns: Int,
    val rows: Int,
    val iconSize: Float,
) {
    fun cellAt(p: Offset): Int {
        val col = ((p.x - origin.x) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((p.y - origin.y) / cellHeight).toInt().coerceIn(0, rows - 1)
        return row * columns + col
    }

    /** Center of the icon drawn in [cell] (icons sit at the top center of their cell). */
    fun iconCenter(cell: Int): Offset = Offset(
        origin.x + (cell % columns + 0.5f) * cellWidth,
        origin.y + (cell / columns) * cellHeight + iconSize / 2,
    )
}

