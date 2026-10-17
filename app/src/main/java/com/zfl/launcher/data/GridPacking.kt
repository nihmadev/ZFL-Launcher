package com.zfl.launcher.data

/** Size of a grid item in cells. Apps and folders are 1×1, widgets are larger. */
data class Span(val columns: Int, val rows: Int) {
    companion object {
        val One: Span = Span(1, 1)
    }
}

/**
 * Places items on [grid] in list order, iOS style: each item takes the first free spot
 * (top-left first) where it fits, so apps flow around widgets. Wide items only start on a
 * column that is a multiple of their width (a small widget sits on the left or the right
 * half, never in the middle).
 *
 * @return the top-left cell index ([GridSpec.cell]) of each item, or null for items that
 *   don't fit anymore.
 */
fun packCells(spans: List<Span>, grid: GridSpec, preferredCells: List<Int?> = emptyList()): List<Int?> {
    val occupancy = Occupancy(grid)
    val cells = MutableList<Int?>(spans.size) { null }
    // Reserve saved cells before filling gaps. Earlier items win collisions; a dragged
    // item is placed first so its requested footprint displaces only overlapping items.
    spans.forEachIndexed { index, span ->
        val cell = preferredCells.getOrNull(index) ?: return@forEachIndexed
        if (cell >= 0 && cell < grid.cellCount && occupancy.fits(grid.rowOf(cell), grid.columnOf(cell), span)) {
            cells[index] = cell
            occupancy.occupy(cell, span)
        }
    }
    spans.forEachIndexed { index, span ->
        if (cells[index] != null) return@forEachIndexed
        search@ for (row in 0 until grid.rows) {
            for (col in 0..(grid.columns - span.columns)) {
                // Preserve the legacy automatic placement of wide widgets. Explicit
                // cells may use any column where the whole footprint fits.
                if (col % span.columns != 0) continue
                if (occupancy.fits(row, col, span)) {
                    val cell = grid.cell(row, col)
                    cells[index] = cell
                    occupancy.occupy(cell, span)
                    break@search
                }
            }
        }
    }
    return cells
}

/** Which cells of one page are taken. */
internal class Occupancy(private val grid: GridSpec) {
    private val taken = BooleanArray(grid.cellCount)

    fun fits(row: Int, col: Int, span: Span): Boolean {
        if (row < 0 || col < 0 || col + span.columns > grid.columns || row + span.rows > grid.rows) return false
        for (r in row until row + span.rows) for (c in col until col + span.columns) {
            if (taken[grid.cell(r, c)]) return false
        }
        return true
    }

    fun occupy(cell: Int, span: Span) {
        val row = grid.rowOf(cell)
        val col = grid.columnOf(cell)
        for (r in row until row + span.rows) for (c in col until col + span.columns) taken[grid.cell(r, c)] = true
    }

    /** First free spot for [span] reading from row [fromRow] down, then from the top; null when the page is full. */
    fun firstFree(span: Span, fromRow: Int = 0): Int? {
        val start = fromRow.coerceIn(0, (grid.rows - 1).coerceAtLeast(0))
        val order = (start until grid.rows) + (0 until start)
        for (row in order) for (col in 0..(grid.columns - span.columns)) {
            if (fits(row, col, span)) return grid.cell(row, col)
        }
        return null
    }
}

/** Index of the item covering [cell], given the cells from [packCells]; null for an empty cell. */
fun itemAtCell(spans: List<Span>, cells: List<Int?>, cell: Int, grid: GridSpec): Int? {
    val row = grid.rowOf(cell)
    val col = grid.columnOf(cell)
    return spans.indices.firstOrNull { i ->
        val start = cells[i] ?: return@firstOrNull false
        val r = grid.rowOf(start)
        val c = grid.columnOf(start)
        row in r until r + spans[i].rows && col in c until c + spans[i].columns
    }
}

/**
 * Splits pages so each one fits [grid]: from the first item that doesn't fit, the rest of
 * the page moves to the next one (cascading). A page always keeps at least one item, so an
 * item bigger than the grid can't loop forever; such an item is not placed (see
 * [GridSpec.fits]), which is why operations reject oversized items before they get here.
 *
 * @param dropEmpty remove empty pages. The drag preview keeps them, so page indices stay
 *   the same as in the layout the drop will be applied to.
 */
fun <T> cascadePages(
    pages: List<List<T>>,
    grid: GridSpec,
    dropEmpty: Boolean = true,
    cell: (T) -> Int? = { null },
    span: (T) -> Span = { Span.One },
): List<List<T>> {
    fun fittingCount(items: List<T>): Int {
        val cells = packCells(items.map(span), grid, items.map(cell))
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
