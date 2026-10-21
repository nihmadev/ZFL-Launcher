package com.zfl.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryDragNavigationTest {
    @Test
    fun appCanEnterLibraryAndReturnToLastHomeWithOrWithoutToday() {
        for (today in listOf(false, true)) {
            val pages = HomePages(3, today, library = true)
            assertEquals(3, pages.dragNeighbor(2, 1, allowLibrary = true))
            assertEquals(HomePage.Library, pages.page(3 + pages.firstHome))
            assertEquals(2, pages.dragNeighbor(3, -1, allowLibrary = true))
            assertNull(pages.dragNeighbor(3, 1, allowLibrary = true))
            assertNull(pages.dragNeighbor(0, -1, allowLibrary = true))
        }
    }

    @Test
    fun disabledLibraryAndNonAppDragsStopAtLastHome() {
        assertNull(HomePages(3, true, false).dragNeighbor(2, 1, allowLibrary = true))
        assertNull(HomePages(3, true, true).dragNeighbor(2, 1, allowLibrary = false))
        assertEquals(1, HomePages(3, true, true).dragNeighbor(0, 1, allowLibrary = false))
    }
}
