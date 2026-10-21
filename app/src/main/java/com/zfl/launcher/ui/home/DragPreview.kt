package com.zfl.launcher.ui.home

import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.boundedCell
import com.zfl.launcher.data.cascadePages
import com.zfl.launcher.data.span

/*
 * What the screen shows while dragging: the layout with a gap where the item would land, like
 * iOS. Placed with the same grid and reservations as the move that the drop saves.
 */

fun DragController.previewPages(position: DropPosition? = target): List<List<HomeEntry>> {
    val dragged = session?.takeIf { it.active }?.entry ?: return state.pages
    val pages = state.pages.map { page -> page.filter { it.id != dragged.id } }.toMutableList()
    // An extra empty page at the end lets the user drop on a new page.
    if (pages.last().isNotEmpty()) pages += emptyList<HomeEntry>()
    (position as? DropPosition.PageCell)?.let { t ->
        if (t.page in pages.indices) {
            // The gap is as big as what is dragged: a widget opens a widget-sized hole.
            pages[t.page] = pages[t.page].toMutableList().apply { add(0, PlaceholderEntry(dragged.span)) }
        }
    }
    val cells = previewCells(position)
    return cascadePages(pages, state.grid, dropEmpty = false, cell = { cells[it.id] }) { it.span }
}

/** The preview uses the same reservations as the persisted move. */
fun DragController.previewCells(position: DropPosition? = target): Map<String, Int> {
    val dragged = session?.takeIf { it.active }?.entry ?: return state.cells
    val rest = state.cells - dragged.id
    val t = position as? DropPosition.PageCell ?: return rest
    return rest + ("placeholder" to boundedCell(t.cell, dragged.span, state.grid))
}

fun DragController.previewDock(position: DropPosition? = target): List<HomeEntry> {
    val dragged = draggedId ?: return state.dock
    val dock = state.dock.filter { it.id != dragged }.toMutableList()
    (position as? DropPosition.Dock)?.let { dock.add(it.index.coerceIn(0, dock.size), PlaceholderEntry()) }
    return dock
}

fun DragController.previewFolder(folder: FolderEntry, position: DropPosition? = target): List<HomeEntry> {
    val apps: MutableList<HomeEntry> = folder.apps.map(::AppEntry).filter { it.id != draggedId }.toMutableList()
    (position as? DropPosition.InFolder)?.takeIf { it.folderId == folder.id }?.let {
        apps.add(it.index.coerceIn(0, apps.size), PlaceholderEntry())
    }
    return apps
}
