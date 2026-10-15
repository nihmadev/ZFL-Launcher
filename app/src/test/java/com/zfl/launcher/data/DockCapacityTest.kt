package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DockCapacityTest {
    private fun apps(vararg keys: String): List<HomeItem> = keys.map(::AppItem)

    @Test
    fun fiveSlotDockAcceptsFifthApp() {
        val layout = LayoutData(pages = listOf(apps("a", "b")), dock = apps("1", "2", "3", "4"))
        val moved = layout.move(AppItem("a"), DropPosition.Dock(4), pageCapacity = 24, dockCapacity = 5) { "" }
        assertEquals(apps("1", "2", "3", "4", "a"), moved.dock)
        assertEquals(listOf(apps("b")), moved.pages)
    }

    @Test
    fun fullDockRejectsDrop() {
        val layout = LayoutData(pages = listOf(apps("a")), dock = apps("1", "2", "3", "4"))
        assertEquals(layout, layout.move(AppItem("a"), DropPosition.Dock(0), pageCapacity = 24, dockCapacity = 4) { "" })
    }

    @Test
    fun shrinkingDockMovesOverflowToLastPageInsteadOfDroppingIt() {
        val layout = LayoutData(pages = listOf(apps("a", "b"), apps("c")), dock = apps("1", "2", "3", "4", "5"))
        val normalized = layout.normalized(pageCapacity = 24, dockCapacity = 4)
        assertEquals(apps("1", "2", "3", "4"), normalized.dock)
        assertEquals(listOf(apps("a", "b"), apps("c", "5")), normalized.pages)
        assertEquals(layout.allAppKeys(), normalized.allAppKeys())
    }

    @Test
    fun reconcileKeepsOverflowingDockApps() {
        val layout = LayoutData(pages = listOf(apps("a")), dock = apps("1", "2", "3", "4", "5"))
        val next = layout.reconcile(listOf("a", "1", "2", "3", "4", "5"), pageCapacity = 24, dockCapacity = 4)
        assertEquals(setOf("a", "1", "2", "3", "4", "5"), next.allAppKeys())
        assertEquals(4, next.dock.size)
    }
}
