package com.zfl.launcher.data

/**
 * Size of a grid in cells. Cells are addressed row by row: `row * columns + column`, so two
 * grids with the same capacity (4×6 and 6×4) still address the same index differently.
 */
data class GridSpec(val columns: Int, val rows: Int) {
    init {
        require(columns > 0 && rows > 0) { "Empty grid $columns×$rows" }
    }

    val cellCount: Int get() = columns * rows

    fun cell(row: Int, column: Int): Int = row * columns + column

    fun rowOf(cell: Int): Int = cell / columns

    fun columnOf(cell: Int): Int = cell % columns

    /** Whether an item of [span] fits on an empty page of this grid. */
    fun fits(span: Span): Boolean = span.columns <= columns && span.rows <= rows

    override fun toString(): String = "$columns×$rows"

    companion object {
        /** Columns and rows the Home Screen grid can have. */
        val HOME_COLUMNS: IntRange = 4..6
        val HOME_ROWS: IntRange = 4..8

        /** Home Screen grid of a fresh install. */
        val Default: GridSpec = GridSpec(4, 6)

        /** Every install before columns became configurable had four. */
        const val LEGACY_COLUMNS: Int = 4

        /** An open folder shows nine apps per page, whatever the Home Screen grid is. */
        val Folder: GridSpec = GridSpec(3, 3)

        /** Columns of the Today page, where a small widget is half the width. */
        const val TODAY_COLUMNS: Int = 4

        /** A Home Screen grid within [HOME_COLUMNS] × [HOME_ROWS]. */
        fun home(columns: Int, rows: Int): GridSpec =
            GridSpec(columns.coerceIn(HOME_COLUMNS), rows.coerceIn(HOME_ROWS))
    }
}
