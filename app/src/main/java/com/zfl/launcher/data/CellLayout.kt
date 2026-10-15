package com.zfl.launcher.data

/** Keeps the entire footprint inside the grid, including drops along its bottom/right edge. */
fun boundedCell(cell: Int, span: Span, rows: Int, columns: Int = GRID_COLUMNS): Int {
    val row = (cell.coerceAtLeast(0) / columns).coerceAtMost((rows - span.rows).coerceAtLeast(0))
    val col = (cell.coerceAtLeast(0) % columns).coerceAtMost((columns - span.columns).coerceAtLeast(0))
    return row * columns + col
}

/** Resolves legacy automatic placement once, or refreshes cells after a resize/overflow. */
fun LayoutData.withPageCells(pageCapacity: Int): LayoutData {
    val rows = (pageCapacity / GRID_COLUMNS).coerceAtLeast(1)
    val resolved = buildMap {
        pages.forEach { page ->
            val placed = packCells(page.map { it.span }, GRID_COLUMNS, rows, page.map { cells[it.id] })
            page.forEachIndexed { index, item -> placed[index]?.let { put(item.id, it) } }
        }
    }
    return copy(cells = resolved)
}

/** Exact placement has priority over occupied cells; only overlapping items need a free slot. */
internal fun LayoutData.moveToCell(
    item: HomeItem,
    position: DropPosition.PageCell,
    pageCapacity: Int,
    dockCapacity: Int,
): LayoutData {
    val rows = (pageCapacity / GRID_COLUMNS).coerceAtLeast(1)
    if (position.page < 0 || item.span.columns > GRID_COLUMNS || item.span.rows > rows) return this
    val rest = withPageCells(pageCapacity).without(item.id)
    val pages = rest.pages.toMutableList()
    while (pages.size <= position.page) pages.add(emptyList())
    // First reservation wins if the destination is occupied.
    pages[position.page] = listOf(item) + pages[position.page]
    return rest.copy(
        pages = pages,
        cells = rest.cells + (item.id to boundedCell(position.cell, item.span, rows)),
    ).normalized(pageCapacity, dockCapacity)
}
