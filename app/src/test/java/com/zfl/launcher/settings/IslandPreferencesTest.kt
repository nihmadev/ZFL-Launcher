package com.zfl.launcher.settings

import org.junit.Assert.*
import org.junit.Test

class IslandPreferencesTest {
    @Test fun freshAndLegacyInstallsAreDisabled() {
        assertFalse(IslandPreferences.fromStored(emptyMap<String, Any>()).enabled)
        assertFalse(IslandPreferences.fromStored(mapOf("blur_effect" to true, "onboarding_done" to true)).enabled)
        assertEquals(IslandPreferences(), SettingsState().island)
    }

    @Test fun booleanRoundTripAndUnknownValuesAreSafe() {
        for (enabled in listOf(true, false)) for (charging in listOf(true, false)) {
            val value = IslandPreferences(enabled, charging)
            assertEquals(value, IslandPreferences.fromStored(value.storedValues()))
        }
        assertEquals(IslandPreferences(), IslandPreferences.fromStored(mapOf("island_enabled" to "true", "island_charging" to 1)))
        assertEquals(IslandPreferences(), IslandPreferences.fromStored(mapOf("future_version" to true)))
    }

    @Test fun lookRoundTripsAndOutOfRangeValuesAreClamped() {
        val custom = IslandPreferences(enabled = true, timers = false, alwaysOn = true, blur = false, artworkTint = false,
            compactWidth = 180f, sideMargin = 4f, topOffset = -3f, collapseSeconds = 20f)
        assertEquals(custom, IslandPreferences.fromStored(custom.storedValues()))
        val clamped = IslandPreferences.fromStored(mapOf("island_compact_width" to 900f, "island_side_margin" to Float.NaN))
        assertEquals(IslandPreferences.COMPACT_WIDTH_RANGE.endInclusive, clamped.compactWidth)
        assertEquals(IslandPreferences.DEFAULT_SIDE_MARGIN, clamped.sideMargin)
        assertTrue(IslandPreferences().blur)
        assertTrue(IslandPreferences().artworkTint)
    }
}
