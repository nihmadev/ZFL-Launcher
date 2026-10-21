package com.zfl.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMotionTest {
    @Test
    fun allNineMiniaturesFitInsideTheTileWithoutOverlapping() {
        val cells = (0..8).map { folderPreviewCell(it) }
        cells.forEach { cell ->
            assertTrue(cell.size > 0f)
            assertTrue(cell.left > 0f && cell.top > 0f)
            assertTrue(cell.left + cell.size < 1f && cell.top + cell.size < 1f)
        }
        cells.forEachIndexed { index, cell ->
            cells.drop(index + 1).forEach { other ->
                assertTrue(
                    cell.left + cell.size <= other.left || other.left + other.size <= cell.left ||
                        cell.top + cell.size <= other.top || other.top + other.size <= cell.top,
                )
            }
        }
    }

    @Test
    fun previewHasBalancedMarginsAndACenteredMiddleIcon() {
        val first = folderPreviewCell(0)
        val last = folderPreviewCell(8)
        val middle = folderPreviewCell(4)
        assertEquals(first.left, 1f - last.left - last.size, 0.00001f)
        assertEquals(first.top, 1f - last.top - last.size, 0.00001f)
        assertEquals(0.5f, middle.left + middle.size / 2, 0.00001f)
        assertEquals(0.5f, middle.top + middle.size / 2, 0.00001f)
    }

    @Test
    fun rtlMirrorsColumnsAndPreservesAppOrderWithinRows() {
        repeat(9) { index ->
            val ltr = folderPreviewCell(index)
            val rtl = folderPreviewCell(index, rtl = true)
            assertEquals(1f - ltr.left - ltr.size, rtl.left, 0.00001f)
            assertEquals(ltr.top, rtl.top, 0f)
            assertEquals(ltr.size, rtl.size, 0f)
        }
    }

    @Test
    fun detailFadeStaysValidThroughSpringOvershootAndReversal() {
        listOf(-0.01f, 0f, 0.25f).forEach { assertEquals(0f, folderDetailAlpha(it), 0f) }
        listOf(1f, 1.01f).forEach { assertEquals(1f, folderDetailAlpha(it), 0f) }
        val forward = (0..100).map { folderDetailAlpha(it / 100f) }
        assertTrue(forward.zipWithNext().all { (a, b) -> a <= b })
        val backward = (100 downTo 0).map { folderDetailAlpha(it / 100f) }
        assertEquals(forward.reversed(), backward)
    }
}
