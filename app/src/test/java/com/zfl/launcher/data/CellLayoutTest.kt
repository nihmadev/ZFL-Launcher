package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellLayoutTest {
    private val google = AppWidgetItem("google", 31, "google/.Search", Span(4, 1))
    private val chat = AppItem("chatgpt")
    private fun move(layout: LayoutData, item: HomeItem, page: Int, cell: Int, capacity: Int = 24): LayoutData =
        layout.move(item, DropPosition.PageCell(page, cell), capacity) { "Folder" }

    @Test
    fun hostedWidgetMovesIntoEmptyBottomRowWithoutMovingOtherItems() {
        val layout = LayoutData(listOf(listOf(google, chat, AppItem("a"))), emptyList())
        val before = layout.withPageCells(24)
        val next = move(layout, google, 0, 20)
        assertEquals(20, next.cells[google.id])
        assertEquals(before.cells[chat.id], next.cells[chat.id])
        assertEquals(before.cells["a"], next.cells["a"])
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        assertEquals(listOf(google), next.allPageWidgets().filter { it.id == google.id })
    }

    @Test
    fun appMovesToBottomRightOfSecondPageAndSurvivesReloadAndReconcile() {
        val layout = LayoutData(listOf(listOf(chat, google), listOf(AppItem("a"))), emptyList())
        val next = move(layout, chat, 1, 23)
        val restored = LayoutData.fromJson(next.toJson())!!.reconcile(listOf(chat.appKey, "a"), 24)
        assertEquals(next, restored)
        assertTrue(restored.pages[1].contains(chat))
        assertEquals(23, restored.cells[chat.id])
        assertEquals(0, restored.cells["a"])
    }

    @Test
    fun soleItemCanMoveToSecondPageWithoutDeletingTheEmptyFirstPage() {
        val next = move(LayoutData(listOf(listOf(chat)), emptyList()), chat, 1, 21)
        assertTrue(next.pages[0].isEmpty())
        assertEquals(listOf(chat), next.pages[1])
        assertEquals(21, next.cells[chat.id])
        assertEquals(next, next.normalized(24))
    }

    @Test
    fun pullingAppOutOfFolderPreservesFolderAndExactDestination() {
        val folder = FolderItem("folder", "AI", listOf("a", "b", chat.appKey))
        val layout = LayoutData(listOf(listOf(folder), listOf(AppItem("c"))), emptyList())
        val next = move(layout, chat, 1, 22)
        assertEquals(listOf("a", "b"), next.findFolder(folder.id)?.apps)
        assertEquals(0, next.cells[folder.id])
        assertEquals(22, next.cells[chat.id])
        assertEquals(layout.allAppKeys(), next.allAppKeys())
    }

    @Test
    fun dissolvingFolderLeavesRemainingAppInItsSavedCell() {
        val folder = FolderItem("folder", "AI", listOf("a", chat.appKey))
        val layout = LayoutData(listOf(listOf(folder)), emptyList(), cells = mapOf(folder.id to 10))
        val next = move(layout, chat, 0, 23)
        assertEquals(10, next.cells["a"])
        assertEquals(23, next.cells[chat.id])
    }

    @Test
    fun occupiedDestinationDisplacesOnlyTheOverlappingApp() {
        val apps = List(24) { AppItem("a$it") }
        val layout = LayoutData(listOf(apps), emptyList())
        val next = move(layout, apps[0], 0, 23)
        assertEquals(23, next.cells["a0"])
        assertEquals(0, next.cells["a23"])
        for (i in 1..22) assertEquals(i, next.cells["a$i"])
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        assertEquals(1, next.pages.size)
    }

    @Test
    fun widgetFootprintIsClampedAtBottomRightAndCanUseMiddleColumn() {
        val widget = WidgetItem("clock", WidgetKind.Clock, WidgetSize.Small)
        val layout = LayoutData(listOf(listOf(widget, chat)), emptyList())
        val next = move(layout, widget, 0, 23)
        assertEquals(18, next.cells[widget.id])
        val middle = move(next, widget, 0, 17)
        assertEquals(17, middle.cells[widget.id])
        assertNotEquals(middle.cells[widget.id], middle.cells[chat.id])
    }

    @Test
    fun resizingGridReflowsOutOfBoundsItemsWithoutLossOrOverlap() {
        val apps = List(24) { AppItem("a$it") }
        val layout = move(LayoutData(listOf(apps), emptyList()), apps[0], 0, 23)
        val next = layout.normalized(20)
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        next.pages.forEach { page ->
            val cells = page.map { next.cells.getValue(it.id) }
            assertEquals(cells.size, cells.toSet().size)
            assertTrue(cells.all { it in 0..19 })
        }
    }

    @Test
    fun savedPositionsReserveSpaceBeforeAutomaticPlacement() {
        assertEquals(listOf(1, 0, 23), packCells(List(3) { Span.One }, 4, 6, listOf(null, 0, 23)))
        assertEquals(listOf(0, 1), packCells(List(2) { Span.One }, 4, 6, listOf(0, 0)))
    }

    @Test
    fun oldLayoutsKeepOriginalPositionsUntilMoved() {
        val old = """{"version":1,"pages":[[{"app":"a"},{"app":"b"}]],"dock":[]}"""
        val layout = LayoutData.fromJson(old)!!
        assertTrue(layout.cells.isEmpty())
        assertEquals(mapOf("a" to 0, "b" to 1), layout.withPageCells(24).cells)
    }

    @Test
    fun mergeInheritsTargetCellAndMovingToDockKeepsOtherGaps() {
        val layout = LayoutData(listOf(listOf(chat, AppItem("a"), AppItem("b"))), emptyList(),
            cells = mapOf(chat.id to 23, "a" to 10, "b" to 15))
        val merged = layout.move(chat, DropPosition.Merge("a"), 24) { "AI" }
        assertEquals(10, merged.cells[merged.pages[0].filterIsInstance<FolderItem>().single().id])
        val docked = layout.move(chat, DropPosition.Dock(0), 24) { "" }
        assertEquals(mapOf("a" to 10, "b" to 15), docked.cells)
        assertEquals(listOf(chat), docked.dock)
    }
    @Test
    fun addingAndResizingWidgetReservesItsFootprintBeforeExistingApps() {
        val layout = move(LayoutData(listOf(listOf(chat)), emptyList()), chat, 0, 0)
        val added = layout.addWidget(google, WidgetTarget.Page(0), 24)
        assertEquals(0, added.cells[google.id])
        assertEquals(4, added.cells[chat.id])
        val resized = added.updateWidget(google.copy(span = Span(4, 2)), 24)
        assertEquals(0, resized.cells[google.id])
        assertEquals(8, resized.cells[chat.id])
        assertEquals(added.allAppKeys(), resized.allAppKeys())
    }

    @Test
    fun uninstallingOneAppInFolderKeepsRemainingAppAtFolderCell() {
        val folder = FolderItem("folder", "AI", listOf(chat.appKey, "gone"))
        val layout = LayoutData(listOf(listOf(folder)), emptyList(), cells = mapOf(folder.id to 19))
        val next = layout.reconcile(listOf(chat.appKey), 24)
        assertEquals(listOf(listOf(chat)), next.pages)
        assertEquals(mapOf(chat.id to 19), next.cells)
    }

}
