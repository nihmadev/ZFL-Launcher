package com.zfl.launcher.data

import com.zfl.launcher.LayoutWriter
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDropTest {
    private val app = AppItem("library#0")
    private val folder = FolderItem("folder", "Tools", listOf("a#0", "b#0"))
    private val layout = LayoutData(
        pages = listOf(listOf(AppItem("home#0"), folder)),
        dock = listOf(AppItem("dock#0")),
        removed = setOf(app.appKey, "other#0"),
        cells = mapOf("home#0" to 4, folder.id to 10),
        known = setOf("home#0", "dock#0", "a#0", "b#0", app.appKey, "other#0"),
    )

    private fun drop(position: DropPosition, base: LayoutData = layout, item: AppItem = app): LayoutData =
        base.move(item, position) { "New folder" }

    @Test
    fun libraryAppLandsInChosenCellAndClearsOnlyItsRemovedFlag() {
        val next = drop(DropPosition.PageCell(0, 23))
        assertEquals(layout.allAppKeys() + app.appKey, next.allAppKeys())
        assertEquals(23, next.cells[app.id])
        assertEquals(4, next.cells["home#0"])
        assertEquals(10, next.cells[folder.id])
        assertEquals(setOf("other#0"), next.removed)
        assertEquals(layout.dock, next.dock)
        assertEquals(folder, next.findFolder(folder.id))
    }

    @Test
    fun libraryAppCanCreateAPageAndSurvivesReloadAndLibraryOnlyReconcile() {
        val next = drop(DropPosition.PageCell(1, 20))
        val restored = layoutFromJson(next.toJson(), next.grid)!!
        assertEquals(listOf(app), restored.pages[1])
        assertEquals(20, restored.cells[app.id])
        assertEquals(next, restored.reconcile(next.known!!.toList(), newApps = NewAppPlacement.LibraryOnly))
    }

    @Test
    fun droppingOnAFullPagePreservesEveryAppAndTheRequestedCell() {
        val full = LayoutData(listOf(List(24) { AppItem("app$it") }), emptyList())
        val next = drop(DropPosition.PageCell(0, 23), full)
        assertEquals(full.allAppKeys() + app.appKey, next.allAppKeys())
        assertEquals(23, next.cells[app.id])
        assertEquals(2, next.pages.size)
        next.pages.forEach { page ->
            val cells = page.map { next.cells.getValue(it.id) }
            assertEquals(cells.size, cells.toSet().size)
        }
    }

    @Test
    fun libraryAppCanJoinAnExistingFolderOrMakeANewOne() {
        val joined = drop(DropPosition.Merge(folder.id))
        assertEquals(folder.apps + app.appKey, joined.findFolder(folder.id)?.apps)
        assertEquals(10, joined.cells[folder.id])
        assertFalse(app.appKey in joined.removed)

        val merged = drop(DropPosition.Merge("home#0"))
        val newFolder = merged.pages.flatten().filterIsInstance<FolderItem>().single { it.id != folder.id }
        assertEquals(listOf("home#0", app.appKey), newFolder.apps)
        assertEquals(4, merged.cells[newFolder.id])
        assertEquals(layout.allAppKeys() + app.appKey, merged.allAppKeys())
    }

    @Test
    fun libraryAppCanEnterDockButAFullDockRejectsTheDrop() {
        val next = drop(DropPosition.Dock(0))
        assertEquals(app, next.dock.first())
        assertEquals(layout.allAppKeys() + app.appKey, next.allAppKeys())
        assertFalse(app.appKey in next.removed)

        val full = layout.copy(dock = List(DEFAULT_DOCK_CAPACITY) { AppItem("dock$it") })
        assertEquals(full, drop(DropPosition.Dock(0), full))
    }

    @Test
    fun invalidDestinationKeepsLayoutAndRemovedFlagsUnchanged() {
        assertEquals(layout, drop(DropPosition.Merge("missing")))
        assertEquals(layout, drop(DropPosition.PageCell(-1, 0)))
        assertEquals(layout, drop(DropPosition.InFolder(folder.id, 0)))
    }

    @Test
    fun pickingAnAppAlreadyInsideAFolderMovesItWithoutDuplicatingIt() {
        val existing = AppItem(folder.apps.first())
        val next = drop(DropPosition.PageCell(0, 23), item = existing)
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        assertEquals(23, next.cells[existing.id])
        assertEquals(10, next.cells[folder.apps.last()])
        assertEquals(1, next.pages.flatten().count { it.id == existing.id })
        assertEquals(null, next.findFolder(folder.id))
    }

    @Test
    fun dropSavesOnceAndLockedOrRejectedDropsNeverSave() {
        val state = MutableStateFlow<LayoutData?>(layout)
        var saves = 0
        var locked = false
        val writer = LayoutWriter(state, { state.value = it; saves++ }, { locked })

        assertFalse(writer.arrange { drop(DropPosition.Merge("missing"), it) })
        assertEquals(0, saves)
        locked = true
        assertFalse(writer.arrange { drop(DropPosition.PageCell(0, 23), it) })
        assertEquals(layout, state.value)
        assertEquals(0, saves)
        locked = false
        assertTrue(writer.arrange { drop(DropPosition.PageCell(0, 23), it) })
        assertEquals(1, saves)
        assertEquals(23, state.value!!.cells[app.id])
    }

    @Test
    fun returningAppToLibraryRemovesItFromHomeAndSurvivesRestart() {
        val placed = drop(DropPosition.PageCell(1, 23))
        val next = drop(DropPosition.Library(), placed)
        assertFalse(app.appKey in next.allAppKeys())
        assertTrue(app.appKey in next.removed)
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        val restored = layoutFromJson(next.toJson(), next.grid)!!
        assertEquals(next, restored.reconcile(next.known!!.toList()))
    }

    @Test
    fun returningDockOrFolderAppToLibraryPreservesOtherApps() {
        val fromDock = drop(DropPosition.Library(), item = AppItem("dock#0"))
        assertTrue(fromDock.dock.isEmpty())
        assertEquals(layout.allAppKeys() - "dock#0", fromDock.allAppKeys())

        val fromFolder = drop(DropPosition.Library(), item = AppItem("a#0"))
        assertEquals(layout.allAppKeys() - "a#0", fromFolder.allAppKeys())
        assertEquals(10, fromFolder.cells["b#0"])
        assertTrue("a#0" in fromFolder.removed)
    }

    @Test
    fun returningUnplacedLibraryAppIsANoOpAndFoldersAndWidgetsAreRejected() {
        assertEquals(layout, drop(DropPosition.Library()))
        assertEquals(layout, layout.move(folder, DropPosition.Library()) { "" })
        val widget = WidgetItem("clock", WidgetKind.Clock, WidgetSize.Small)
        val withWidget = layout.copy(pages = listOf(layout.pages.single() + widget))
        assertEquals(withWidget, withWidget.move(widget, DropPosition.Library()) { "" })
    }
}
