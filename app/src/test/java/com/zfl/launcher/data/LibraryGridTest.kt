package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryGridTest {
    private data class App(
        override val key: String,
        override val label: String = key,
        override val installTime: Long = 1,
        override val systemCategory: Int = 0,
    ) : LibraryApp

    private val titles = LibraryTitles(Locale.ENGLISH, mapOf(0 to "Tools"), "Other", "Suggestions", "Recent", "Pinned")
    private val apps = listOf(App("a#0"), App("b#0"), App("c#0"), App("d#0"))
    private val shown = apps.map { it.key }

    @Test
    fun chosenNeighborDeterminesExactGridPositionAfterRestartAndUsageChanges() {
        val placed = LibraryConfig().placeInGrid("d#0", "b#0", shown)
        assertEquals(listOf("a#0", "d#0", "b#0", "c#0"), placed.gridOrder)
        assertEquals(LibraryView.Categories, placed.view)
        val restored = libraryConfigFromJson(placed.toJson())
        assertEquals(placed, restored)
        val library = buildLibrary(apps.reversed(), mapOf("c#0" to 1000.0), restored, titles)
        assertEquals(placed.gridOrder, library.gridApps.map { it.key })
        assertEquals(shown, library.alphabetical.map { it.key })
        assertTrue(library.manualGrid)
    }

    @Test
    fun movingToBeginningEndAndSameSlotNeverDuplicatesOrLosesApps() {
        val first = LibraryConfig().placeInGrid("d#0", "a#0", shown)
        assertEquals(listOf("d#0", "a#0", "b#0", "c#0"), first.gridOrder)
        val last = first.placeInGrid("d#0", null, first.gridOrder)
        assertEquals(shown, last.gridOrder)
        assertEquals(last, last.placeInGrid("b#0", "c#0", shown))
        assertEquals(shown.toSet(), last.gridOrder.toSet())
        assertEquals(shown.size, last.gridOrder.size)
    }

    @Test
    fun unavailableAppsRetainTheirRelativeOrderAndNewAppsAppend() {
        val initial = LibraryConfig(gridOrder = listOf("d#0", "work#10", "b#0", "a#0", "c#0"))
        val moved = initial.placeInGrid("c#0", "b#0", listOf("d#0", "b#0", "a#0", "c#0"))
        val returned = orderedLibraryGrid(apps + App("work#10") + App("new#0"), moved.gridOrder)
        assertEquals(listOf("d#0", "c#0", "b#0", "a#0", "work#10", "new#0"), returned.map { it.key })
    }

    @Test
    fun invalidOrMissingTargetsDoNotChangePreferences() {
        val initial = LibraryConfig()
        assertSame(initial, initial.placeInGrid("gone#0", "a#0", shown))
        assertSame(initial, initial.placeInGrid("a#0", "gone#0", shown))
        assertSame(initial, initial.placeInGrid("a#0", "a#0", shown))
    }

    @Test
    fun oldConfigurationsKeepAutomaticGridAndMissingOrdersAreBounded() {
        val old = libraryConfigFromJson("""{"view":"Grid"}""")
        assertFalse(buildLibrary(apps, emptyMap(), old, titles).manualGrid)
        val missing = List(MAX_REMEMBERED_MISSING + 5) { "gone$it#0" }
        val capped = LibraryConfig(gridOrder = missing + "work#10" + "a#0")
            .withOrphansCapped(setOf("a#0"), setOf("10"))
        assertEquals(MAX_REMEMBERED_MISSING + 2, capped.gridOrder.size)
        assertEquals("gone5#0", capped.gridOrder.first())
        assertTrue("work#10" in capped.gridOrder)
        assertTrue("a#0" in capped.gridOrder)
        val duplicates = libraryConfigFromJson("""{"gridOrder":["b#0","b#0","a#0"]}""")
        assertEquals(listOf("b#0", "a#0"), duplicates.gridOrder)
    }

    @Test
    fun everyPlacementPreservesTheUsersSelectedView() {
        for (view in LibraryView.entries) {
            val config = LibraryConfig(view = view)
            assertEquals(view, config.placeInGrid("d#0", "a#0", shown).view)
            assertEquals(view, config.placeInGroup("d#0", "category-0", "a#0", shown).view)
            assertEquals(view, config.placeInGroup("d#0", LibraryIds.PINNED, null, emptyList()).view)
        }
    }

    @Test
    fun categoryAndExactSlotSurviveReloadUsageChangesAndLanguageChanges() {
        val original = LibraryConfig(categories = listOf(UserCategory("user-work", "Work")))
        val config = original.placeInGroup("d#0", "user-work", null, emptyList())
            .placeInGroup("b#0", "user-work", "d#0", listOf("d#0"))
        val restored = libraryConfigFromJson(config.toJson())
        assertEquals(config, restored)
        val state = buildLibrary(apps, mapOf("d#0" to 10000.0), restored, titles.copy(locale = Locale.GERMAN))
        assertEquals(listOf("b#0", "d#0"), state.groups.single { it.id == "user-work" }.apps.map { it.key })
        assertEquals("user-work", state.groupOf["b#0"])
        assertEquals(LibraryView.Categories, state.view)
        assertFalse("user-work" in restored.deleteCategory("user-work").groupAppOrder)
    }

    @Test
    fun pinnedPlacementKeepsCategoryAndInvalidGroupsCannotRearrangeApps() {
        val initial = LibraryConfig(pinned = listOf("a#0", "c#0"))
        val pinned = initial.placeInGroup("d#0", LibraryIds.PINNED, "c#0", listOf("a#0", "c#0"))
        assertEquals(listOf("a#0", "d#0", "c#0"), pinned.pinned)
        assertEquals(initial.assignments, pinned.assignments)
        assertEquals(initial, initial.placeInGroup("d#0", LibraryIds.SUGGESTIONS, null, shown))
        assertEquals(initial, initial.placeInGroup("d#0", "user-missing", null, shown))
    }
}
