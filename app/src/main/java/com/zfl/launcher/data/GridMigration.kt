package com.zfl.launcher.data

/** Result of moving the layout to another grid ([withGrid]). */
sealed interface GridChange {
    /** The layout in the new grid; nothing was lost. */
    data class Applied(val layout: LayoutData) : GridChange

    /** [items] are bigger than the proposed grid, so it isn't available; the layout is unchanged. */
    data class Blocked(val items: List<HomeItem>) : GridChange
}

/** Top-level items that can't be placed on an empty page of [target]. */
fun LayoutData.itemsTooBigFor(target: GridSpec): List<HomeItem> = pages.flatten().filter { !target.fits(it.span) }

/**
 * Moves every home page to [target]. Each item keeps its row and column when its whole
 * footprint still fits; an item cut off by the new edge takes the nearest free spot of its
 * own page (reading from its old row down, then from the top), and what the page can't hold
 * goes to a new page right after it. Untouched pages, empty cells, folders, stacks, the dock
 * and the Today page stay exactly as they were; every item keeps its id and contents.
 *
 * Deterministic and idempotent: applying the same grid again changes nothing.
 */
fun LayoutData.withGrid(target: GridSpec): GridChange {
    if (target == grid) return GridChange.Applied(this)
    itemsTooBigFor(target).takeIf { it.isNotEmpty() }?.let { return GridChange.Blocked(it) }
    // Automatic (legacy) placement is resolved first, so positions are kept, not the order.
    val placed = withPageCells()
    val pages = mutableListOf<List<HomeItem>>()
    val cells = mutableMapOf<String, Int>()
    for (page in placed.pages) {
        val occupancy = Occupancy(target)
        val displaced = mutableListOf<Pair<HomeItem, Int>>()
        page.forEach { item ->
            val old = placed.cells[item.id]
            val row = old?.let(grid::rowOf) ?: 0
            val col = old?.let(grid::columnOf)
            if (col != null && occupancy.fits(row, col, item.span)) {
                target.cell(row, col).also { cells[item.id] = it; occupancy.occupy(it, item.span) }
            } else {
                displaced += item to row
            }
        }
        val overflow = displaced.mapNotNull { (item, row) ->
            val cell = occupancy.firstFree(item.span, fromRow = row) ?: return@mapNotNull item
            cells[item.id] = cell
            occupancy.occupy(cell, item.span)
            null
        }
        pages += page.filter { it.id in cells }
        pages += overflowPages(overflow, target, cells)
    }
    return GridChange.Applied(copy(pages = pages, cells = cells, grid = target))
}

/** Pages for [items] in their order, top-left first; every item fits an empty page (checked by the caller). */
private fun overflowPages(items: List<HomeItem>, grid: GridSpec, cells: MutableMap<String, Int>): List<List<HomeItem>> {
    val pages = mutableListOf<List<HomeItem>>()
    var rest = items
    while (rest.isNotEmpty()) {
        val occupancy = Occupancy(grid)
        val (page, carry) = rest.partition { item ->
            occupancy.firstFree(item.span)?.also { cells[item.id] = it; occupancy.occupy(it, item.span) } != null
        }
        pages += page
        rest = carry
    }
    return pages
}
