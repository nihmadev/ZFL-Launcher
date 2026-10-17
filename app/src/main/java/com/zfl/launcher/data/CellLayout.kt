package com.zfl.launcher.data

/** Keeps the entire footprint inside [grid], including drops along its bottom/right edge. */
fun boundedCell(cell: Int, span: Span, grid: GridSpec): Int {
    val safe = cell.coerceAtLeast(0)
    val row = grid.rowOf(safe).coerceAtMost((grid.rows - span.rows).coerceAtLeast(0))
    val col = grid.columnOf(safe).coerceAtMost((grid.columns - span.columns).coerceAtLeast(0))
    return grid.cell(row, col)
}

/** Resolves legacy automatic placement once, or refreshes cells after a resize/overflow. */
fun LayoutData.withPageCells(): LayoutData {
    val resolved = buildMap {
        pages.forEach { page ->
            val placed = packCells(page.map { it.span }, grid, page.map { cells[it.id] })
            page.forEachIndexed { index, item -> placed[index]?.let { put(item.id, it) } }
        }
    }
    return copy(cells = resolved)
}

/** Exact placement has priority over occupied cells; only overlapping items need a free slot. */
internal fun LayoutData.moveToCell(item: HomeItem, position: DropPosition.PageCell, dockCapacity: Int): LayoutData {
    if (position.page < 0 || !grid.fits(item.span)) return this
    val rest = withPageCells().without(item.id)
    val pages = rest.pages.toMutableList()
    while (pages.size <= position.page) pages.add(emptyList())
    // First reservation wins if the destination is occupied.
    pages[position.page] = listOf(item) + pages[position.page]
    return rest.copy(
        pages = pages,
        cells = rest.cells + (item.id to boundedCell(position.cell, item.span, grid)),
    ).normalized(dockCapacity)
}
