package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridPackingTest {
    private val one = Span.One
    private val small = WidgetSize.Small.span
    private val medium = WidgetSize.Medium.span

    @Test
    fun appsFillRowsInOrder() {
        assertEquals(listOf(0, 1, 2, 3, 4), packCells(List(5) { one }, columns = 4, rows = 6))
    }

    @Test
    fun appsFlowAroundSmallWidget() {
        // Small widget on the left half: the next apps take the right half of its two rows.
        val cells = packCells(listOf(small, one, one, one, one, one), columns = 4, rows = 6)
        assertEquals(listOf(0, 2, 3, 6, 7, 8), cells)
    }

    @Test
    fun smallWidgetNeverStartsInTheMiddleColumn() {
        val cells = packCells(listOf(one, small), columns = 4, rows = 6)
        assertEquals(2, cells[1])
    }

    @Test
    fun itemsThatDoNotFitAreNull() {
        val cells = packCells(listOf(medium, medium, one), columns = 4, rows = 4)
        assertEquals(listOf(0, 8, null), cells)
    }

    @Test
    fun itemAtCellFindsWidgetByAnyOfItsCells() {
        val spans = listOf(small, one)
        val cells = packCells(spans, 4, 6)
        assertEquals(0, itemAtCell(spans, cells, cell = 5, columns = 4))
        assertEquals(1, itemAtCell(spans, cells, cell = 2, columns = 4))
        assertNull(itemAtCell(spans, cells, cell = 3, columns = 4))
    }

    @Test
    fun cascadeMovesWhatDoesNotFitToTheNextPage() {
        // 4×5 page: a large widget (16 cells) leaves room for 4 apps; the fifth moves on.
        val items = listOf("L") + List(5) { "a$it" }
        val pages = cascadePages(listOf(items), capacity = 20) { if (it == "L") WidgetSize.Large.span else one }
        assertEquals(listOf(listOf("L", "a0", "a1", "a2", "a3"), listOf("a4")), pages)
    }

    @Test
    fun cascadeWithAppsOnlyMatchesCapacity() {
        val pages = cascadePages(listOf(List(30) { it }), capacity = 24)
        assertEquals(listOf(24, 6), pages.map { it.size })
    }
}
