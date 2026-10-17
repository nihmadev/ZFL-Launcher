package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryConfigTest {
    private fun LibraryConfig.created(name: String, id: String, reserved: List<String> = emptyList()): LibraryConfig =
        (createCategory(name, id, reserved) as CategoryEdit.Done).config

    @Test
    fun createRenameDeleteCategory() {
        val created = LibraryConfig().created("  Work  ", "user-a")
        assertEquals(listOf(UserCategory("user-a", "Work")), created.categories)
        val renamed = (created.renameCategory("user-a", "Office", emptyList()) as CategoryEdit.Done).config
        assertEquals("Office", renamed.category("user-a")?.name)
        val assigned = renamed.assign("mail#0", "user-a").assign("maps#0", "category-6")
            .moveGroup(listOf("user-a", "category-6"), 0, 1)
        val deleted = assigned.deleteCategory("user-a")
        assertTrue(deleted.categories.isEmpty())
        // Its apps go back to automatic; other choices stay.
        assertEquals(mapOf("maps#0" to "category-6"), deleted.assignments)
        assertEquals(listOf("category-6"), deleted.groupOrder)
    }

    @Test
    fun namesMustBeNonEmptyAndUnique() {
        val config = LibraryConfig().created("Work", "user-a")
        assertEquals(CategoryEdit.Refused(NameProblem.Empty), config.createCategory("   ", "user-b", emptyList()))
        assertEquals(CategoryEdit.Refused(NameProblem.Duplicate), config.createCategory("work", "user-b", emptyList()))
        assertEquals(CategoryEdit.Refused(NameProblem.Duplicate), config.createCategory("Games", "user-b", listOf("Games")))
        assertEquals(CategoryEdit.Refused(NameProblem.Empty), config.renameCategory("user-a", "", emptyList()))
        // Renaming to its own name (another case) is fine.
        assertEquals("WORK", (config.renameCategory("user-a", "WORK", emptyList()) as CategoryEdit.Done).config.category("user-a")?.name)
        val long = LibraryConfig().created("x".repeat(100), "user-c")
        assertEquals(MAX_CATEGORY_NAME, long.categories.single().name.length)
        val full = LibraryConfig(categories = List(MAX_USER_CATEGORIES) { UserCategory("user-$it", "C$it") })
        assertEquals(CategoryEdit.Refused(NameProblem.TooMany), full.createCategory("New", "user-x", emptyList()))
    }

    @Test
    fun newIdsAreUnique() {
        val taken = listOf(LibraryIds.newUser(100, emptyList()))
        val next = LibraryIds.newUser(100, taken)
        assertTrue(next !in taken && LibraryIds.isUser(next))
    }

    @Test
    fun assignOnlyToExistingGroupsAndBackToAutomatic() {
        val config = LibraryConfig().created("Work", "user-a")
        assertSame(config, config.assign("app#0", "user-missing"))
        assertSame(config, config.assign("app#0", "suggestions"))
        val assigned = config.assign("app#0", "user-a")
        assertEquals("user-a", assigned.assignments["app#0"])
        assertEquals(emptyMap<String, String>(), assigned.assign("app#0", null).assignments)
        assertEquals("other", config.assign("app#0", "other").assignments["app#0"])
    }

    @Test
    fun pinsMoveAmongShownAppsWithoutLossOrDuplicates() {
        val config = LibraryConfig().pin("a").pin("b").pin("gone").pin("c").pin("a")
        assertEquals(listOf("a", "b", "gone", "c"), config.pinned)
        val moved = config.movePinned("c", 0, shown = listOf("a", "b", "c"))
        assertEquals(listOf("c", "a", "b", "gone"), moved.pinned)
        assertSame(config, config.movePinned("gone", 0, shown = listOf("a", "b", "c")))
        assertEquals(listOf("a", "gone", "c"), config.unpin("b").pinned)
        assertEquals(config.pinned.toSet(), config.movePinned("a", 99, listOf("a", "b", "c")).pinned.toSet())
    }

    @Test
    fun orphansAreCappedOldestFirstExceptForPausedProfiles() {
        val many = (0 until MAX_REMEMBERED_MISSING + 5).map { "gone$it#0" }
        val config = LibraryConfig(
            assignments = (many + "work#10" + "here#0").associateWith { "other" },
            pinned = many + "work#10",
        )
        val capped = config.withOrphansCapped(present = setOf("here#0"), keepUsers = setOf("10"))
        assertEquals(MAX_REMEMBERED_MISSING, capped.pinned.count { it.startsWith("gone") })
        assertEquals("gone5#0", capped.pinned.first())
        assertTrue("work#10" in capped.pinned && "work#10" in capped.assignments && "here#0" in capped.assignments)
        assertSame(capped, capped.withOrphansCapped(setOf("here#0"), setOf("10")))
    }

    @Test
    fun jsonRoundTripAndLenientReading() {
        val config = LibraryConfig(
            view = LibraryView.List,
            showSuggestions = false,
            newApps = NewAppPlacement.LibraryOnly,
            categories = listOf(UserCategory("user-a", "Work \"quoted\"")),
            assignments = mapOf("a#0" to "user-a", "b#10" to "category-3"),
            pinned = listOf("b#10", "a#0"),
            groupOrder = listOf("category-3", "user-a"),
        )
        assertEquals(config, libraryConfigFromJson(config.toJson()))
        assertEquals(LibraryConfig(), libraryConfigFromJson(null))
        assertEquals(LibraryConfig(), libraryConfigFromJson("not json"))
        val future = """{"view":"Carousel","newApps":"Somewhere","recent":false,
            "categories":[{"id":"user-a","name":" "},{"id":"bad","name":"X"},{"id":"user-b","name":"Ok"}],
            "assignments":[{"app":"x#0"},{"app":"y#0","group":"user-b"}],"pinned":["p#0","p#0"]}"""
        val read = libraryConfigFromJson(future)
        assertEquals(LibraryView.Categories, read.view)
        assertEquals(NewAppPlacement.HomeAndLibrary, read.newApps)
        assertEquals(false, read.showRecent)
        assertEquals(listOf(UserCategory("user-b", "Ok")), read.categories)
        assertEquals(mapOf("y#0" to "user-b"), read.assignments)
        assertEquals(listOf("p#0"), read.pinned)
    }
}
