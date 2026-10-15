package com.zfl.launcher.data

/** Size of a grid item in cells. Apps and folders are 1×1, widgets are larger. */
data class Span(val columns: Int, val rows: Int) {
    companion object {
        val One: Span = Span(1, 1)
    }
}

/** Columns of the home screen grid. */
const val GRID_COLUMNS: Int = 4

/**
 * Places items on a [columns] × [rows] grid in list order, iOS style: each item takes the
 * first free spot (top-left first) where it fits, so apps flow around widgets. Wide items
 * only start on a column that is a multiple of their width (a small widget sits on the
 * left or the right half, never in the middle).
 *
 * @return the top-left cell index (`row * columns + column`) of each item, or null for
 *   items that don't fit anymore.
 */
fun packCells(
    spans: List<Span>,
    columns: Int,
    rows: Int,
    preferredCells: List<Int?> = emptyList(),
): List<Int?> {
    val taken = BooleanArray(columns * rows)
    fun fits(row: Int, col: Int, span: Span): Boolean {
        if (row < 0 || col < 0 || col + span.columns > columns || row + span.rows > rows) return false
        for (r in row until row + span.rows) for (c in col until col + span.columns) {
            if (taken[r * columns + c]) return false
        }
        return true
    }
    fun occupy(cell: Int, span: Span) {
        val row = cell / columns
        val col = cell % columns
        for (r in row until row + span.rows) for (c in col until col + span.columns) taken[r * columns + c] = true
    }
    val cells = MutableList<Int?>(spans.size) { null }
    // Reserve saved cells before filling gaps. Earlier items win collisions; a dragged
    // item is placed first so its requested footprint displaces only overlapping items.
    spans.forEachIndexed { index, span ->
        val cell = preferredCells.getOrNull(index) ?: return@forEachIndexed
        if (cell >= 0 && fits(cell / columns, cell % columns, span)) {
            cells[index] = cell
            occupy(cell, span)
        }
    }
    spans.forEachIndexed { index, span ->
        if (cells[index] != null) return@forEachIndexed
        search@ for (row in 0 until rows) {
            for (col in 0..(columns - span.columns)) {
                // Preserve the legacy automatic placement of wide widgets. Explicit
                // cells may use any column where the whole footprint fits.
                if (col % span.columns != 0) continue
                if (fits(row, col, span)) {
                    val cell = row * columns + col
                    cells[index] = cell
                    occupy(cell, span)
                    break@search
                }
            }
        }
    }
    return cells
}

/** Index of the item covering [cell], given the cells from [packCells]; null for an empty cell. */
fun itemAtCell(spans: List<Span>, cells: List<Int?>, cell: Int, columns: Int): Int? {
    val row = cell / columns
    val col = cell % columns
    return spans.indices.firstOrNull { i ->
        val start = cells[i] ?: return@firstOrNull false
        val r = start / columns
        val c = start % columns
        row in r until r + spans[i].rows && col in c until c + spans[i].columns
    }
}

/**
 * Splits pages so each one fits its grid: from the first item that doesn't fit, the rest of
 * the page moves to the next one (cascading). A page always keeps at least one item, so an
 * item bigger than the grid can't loop forever.
 *
 * @param capacity cells per page ([GRID_COLUMNS] × rows).
 * @param dropEmpty remove empty pages. The drag preview keeps them, so page indices stay
 *   the same as in the layout the drop will be applied to.
 */
fun <T> cascadePages(
    pages: List<List<T>>,
    capacity: Int,
    dropEmpty: Boolean = true,
    cell: (T) -> Int? = { null },
    span: (T) -> Span = { Span.One },
): List<List<T>> {
    val rows = (capacity / GRID_COLUMNS).coerceAtLeast(1)
    fun fittingCount(items: List<T>): Int {
        val cells = packCells(items.map(span), GRID_COLUMNS, rows, items.map(cell))
        val firstMissing = cells.indexOfFirst { it == null }
        return if (firstMissing < 0) items.size else firstMissing.coerceAtLeast(1)
    }

    val result = mutableListOf<List<T>>()
    var carry = emptyList<T>()
    for (page in pages) {
        val items = carry + page
        val count = fittingCount(items)
        result += items.take(count)
        carry = items.drop(count)
    }
    while (carry.isNotEmpty()) {
        val count = fittingCount(carry)
        result += carry.take(count)
        carry = carry.drop(count)
    }
    return if (dropEmpty) result.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) } else result
}

/** Grid footprint of a persisted layout item. */
val HomeItem.span: Span
    get() = (this as? PageWidget)?.span ?: Span.One
