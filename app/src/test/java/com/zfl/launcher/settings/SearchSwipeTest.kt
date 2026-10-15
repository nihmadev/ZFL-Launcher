package com.zfl.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSwipeTest {
    @Test
    fun oldToggleAndFreshInstallKeepTheirBehavior() {
        assertEquals(SearchSwipe.Down, SearchSwipe.fromStored(null, legacyEnabled = true))
        assertEquals(SearchSwipe.Disabled, SearchSwipe.fromStored(null, legacyEnabled = false))
        assertEquals(SearchSwipe.Down, SettingsState().searchSwipe)
    }

    @Test
    fun savedDirectionTakesPrecedenceOverOldToggle() {
        for (swipe in SearchSwipe.entries) {
            assertEquals(swipe, SearchSwipe.fromStored(swipe.name, legacyEnabled = true))
            assertEquals(swipe, SearchSwipe.fromStored(swipe.name, legacyEnabled = false))
        }
    }

    @Test
    fun unknownValueFallsBackToPreviousBehavior() {
        assertEquals(SearchSwipe.Down, SearchSwipe.fromStored("unknown", legacyEnabled = true))
        assertEquals(SearchSwipe.Disabled, SearchSwipe.fromStored("unknown", legacyEnabled = false))
    }

    @Test
    fun onlySelectedDirectionBeyondThresholdOpensSearch() {
        assertTrue(SearchSwipe.Down.isTriggered(57f, 56f))
        assertFalse(SearchSwipe.Down.isTriggered(-100f, 56f))
        assertTrue(SearchSwipe.Up.isTriggered(-57f, 56f))
        assertFalse(SearchSwipe.Up.isTriggered(100f, 56f))
        for (swipe in SearchSwipe.entries) {
            for (travel in listOf(-56f, -20f, 0f, 20f, 56f)) {
                assertFalse(swipe.isTriggered(travel, 56f))
            }
        }
        assertFalse(SearchSwipe.Disabled.isTriggered(-100f, 56f))
        assertFalse(SearchSwipe.Disabled.isTriggered(100f, 56f))
    }
}
