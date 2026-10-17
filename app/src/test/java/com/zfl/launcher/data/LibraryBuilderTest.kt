package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryBuilderTest {
    private data class App(
        override val key: String,
        override val label: String,
        override val systemCategory: Int = UNDEFINED_CATEGORY,
        override val installTime: Long = 0,
    ) : LibraryApp

    private val titles = LibraryTitles(
        locale = Locale.ENGLISH,
        system = mapOf(GAME to "Games", SOCIAL to "Social", PRODUCTIVITY to "Productivity"),
        other = "Other",
        suggestions = "Suggestions",
        recent = "Recently Added",
        pinned = "Pinned",
    )

    private fun build(apps: List<App>, config: LibraryConfig = LibraryConfig(), scores: Map<String, Double> = emptyMap(), names: LibraryTitles = titles) =
        buildLibrary(apps, scores, config, names)

    private val chess = App("chess#0", "Chess", GAME, installTime = 30)
    private val go = App("go#0", "Go", GAME, installTime = 20)
    private val chat = App("chat#0", "Chat", SOCIAL, installTime = 10)
    private val notes = App("notes#0", "Notes", PRODUCTIVITY)
    private val tool = App("tool#0", "Tool")
    private val all = listOf(chess, go, chat, notes, tool)

    private fun LibraryState<App>.groupIds() = groups.map { it.id }
    private fun LibraryState<App>.members(id: String) = groups.single { it.id == id }.apps.map { it.key }

    @Test
    fun untouchedLibraryKeepsSystemGroupsBySizeWithOtherLast() {
        val library = build(all, scores = mapOf(chat.key to 3.0))
        assertEquals(listOf("category-0", "category-4", "category-7", "other"), library.groupIds())
        assertEquals(listOf("suggestions", "recent", "category-0", "category-4", "category-7", "other"), library.cards.map { it.id })
        assertEquals(listOf(chat.key), library.suggestions.map { it.key })
        assertEquals(listOf(chess.key, go.key, chat.key), library.recent.map { it.key })
    }

    @Test
    fun manualAssignmentWinsOverSystemCategoryAndEveryAppIsInOneGroup() {
        val config = LibraryConfig(categories = listOf(UserCategory("user-a", "Daily")), assignments = mapOf(chess.key to "user-a", chat.key to "category-7"))
        val library = build(all, config)
        assertEquals(listOf(chess.key), library.members("user-a"))
        assertEquals(listOf(go.key), library.members("category-0"))
        assertEquals(setOf(chat.key, notes.key), library.members("category-7").toSet())
        assertFalse(library.groupIds().contains("category-4"))
        assertEquals(all.size, library.groups.sumOf { it.apps.size })
        assertEquals(all.map { it.key }.toSet(), library.groups.flatMap { g -> g.apps.map { it.key } }.toSet())
        assertEquals("category-0", library.autoGroup(chess.key))
    }

    @Test
    fun brokenOrUnknownReferencesFallBackToAutomatic() {
        val config = LibraryConfig(assignments = mapOf(chess.key to "user-gone", go.key to "category-99", chat.key to "nonsense"))
        val library = build(all, config)
        assertEquals(setOf(chess.key, go.key), library.members("category-0").toSet())
        assertEquals(listOf(chat.key), library.members("category-4"))
        // A category number Android has no name for lands in "Other" instead of a nameless group.
        val odd = App("odd#0", "Odd", 23)
        assertEquals(setOf(tool.key, odd.key), build(all + odd).members("other").toSet())
    }

    @Test
    fun emptyUserCategoryHasNoCardButStaysInTheEditorList() {
        val config = LibraryConfig(categories = listOf(UserCategory("user-a", "Later")))
        val library = build(all, config)
        assertFalse(library.cards.any { it.id == "user-a" })
        assertEquals(GroupSummary("user-a", "Later", GroupKind.User, 0), library.summary("user-a"))
        assertEquals("user-a", library.allGroups.first().id)
    }

    @Test
    fun savedOrderIsKeptAndNewGroupsFollowDeterministically() {
        val config = LibraryConfig(
            categories = listOf(UserCategory("user-a", "Mine")),
            assignments = mapOf(tool.key to "user-a"),
            groupOrder = listOf("category-7", "other", "category-0"),
        )
        val library = build(all, config, scores = mapOf(go.key to 9.0))
        // Arranged first; then the user category; then the system group that wasn't arranged.
        assertEquals(listOf("category-7", "category-0", "user-a", "category-4"), library.groupIds())
        // More apps in a group doesn't reshuffle an arranged order.
        val more = all + App("social2#0", "Social Two", SOCIAL) + App("social3#0", "Social Three", SOCIAL)
        assertEquals(listOf("category-7", "category-0", "user-a", "category-4"), build(more, config).groupIds())
        // "Other" is last among the groups that weren't arranged.
        val other = config.copy(groupOrder = listOf("category-7"), assignments = emptyMap())
        assertEquals(listOf("category-7", "category-0", "category-4", "other"), build(all, other).groupIds())
    }

    @Test
    fun pinnedKeepSavedOrderWithoutHiddenOrMissingApps() {
        val config = LibraryConfig(pinned = listOf(notes.key, "gone#0", chess.key))
        val visible = all - chess
        val library = build(visible, config)
        assertEquals(listOf(notes.key), library.pinned.map { it.key })
        assertEquals("pinned", library.cards.first().id)
        assertEquals(listOf(notes.key, chess.key), build(all, config).pinned.map { it.key })
        // A pinned app is still in its regular group: pins are an extra view.
        assertTrue(build(all, config).members("category-7").contains(notes.key))
    }

    @Test
    fun smartGroupsCanBeTurnedOffAndLeaveNoCard() {
        val library = build(all, LibraryConfig(showSuggestions = false, showRecent = false), scores = mapOf(chat.key to 1.0))
        assertTrue(library.suggestions.isEmpty())
        assertTrue(library.recent.isEmpty())
        assertFalse(library.cards.any { it.kind == GroupKind.Suggestions || it.kind == GroupKind.Recent })
        val onlyRecent = build(all, LibraryConfig(showSuggestions = false), scores = mapOf(chat.key to 1.0))
        assertEquals(listOf("recent"), onlyRecent.cards.filter { it.kind != GroupKind.System }.map { it.id })
    }

    @Test
    fun samePackageOfTwoProfilesStaysApart() {
        val personal = App("com.mail/.Main#0", "Mail", PRODUCTIVITY)
        val work = App("com.mail/.Main#10", "Mail", PRODUCTIVITY)
        val config = LibraryConfig(categories = listOf(UserCategory("user-w", "Work")), assignments = mapOf(work.key to "user-w"), pinned = listOf(personal.key))
        val library = build(listOf(personal, work), config)
        assertEquals(listOf(personal.key), library.members("category-7"))
        assertEquals(listOf(work.key), library.members("user-w"))
        assertEquals(listOf(personal.key), library.pinned.map { it.key })
        assertEquals(listOf(personal.key, work.key), library.alphabetical.map { it.key })
    }

    @Test
    fun namesSortByLocaleWithKeyAsTieBreaker() {
        val apps = listOf(App("b#0", "Éclair"), App("a#0", "zebra"), App("d#10", "Apple"), App("c#0", "Apple"), App("e#0", "Яблоко"), App("f#0", "Банан"))
        val library = build(apps)
        // Accents sort with their letter, case doesn't split names, Latin before Cyrillic in English.
        assertEquals(listOf("c#0", "d#10", "b#0", "a#0", "f#0", "e#0"), library.alphabetical.map { it.key })
        assertEquals(listOf("A", "É", "Z", "Б", "Я"), library.sections.map { it.letter })
        // The same name in two profiles is ordered by key, whatever the input order.
        assertEquals(library.alphabetical, build(apps.reversed()).alphabetical)
        val russian = build(apps, names = titles.copy(locale = Locale.forLanguageTag("ru"))).alphabetical.map { it.label }
        assertEquals(listOf("Банан", "Яблоко"), russian.filter { it.first() in 'А'..'я' })
    }

    @Test
    fun customLabelsDecideTheOrderAndSections() {
        val renamed = listOf(chess.copy(label = "Zzz"), go)
        assertEquals(listOf(go.key, chess.key), build(renamed).alphabetical.map { it.key })
        assertEquals(listOf("G", "Z"), build(renamed).sections.map { it.letter })
    }

    @Test
    fun lettersAreUniqueEvenWhenTheOrderSplitsThem() {
        val sections = letterSections(listOf("Apple", "Ábaco", "Avocado", "1Password")) { it }
        assertEquals(listOf("A", "Á", "#"), sections.map { it.letter })
        assertEquals(listOf("Apple", "Avocado"), sections.first().apps)
    }

    @Test
    fun translatedTitlesChangeNamesButNotIds() {
        val config = LibraryConfig(categories = listOf(UserCategory("user-a", "Mine")), assignments = mapOf(tool.key to "user-a"))
        val russian = titles.copy(system = mapOf(GAME to "Игры", SOCIAL to "Общение", PRODUCTIVITY to "Работа"), other = "Другое")
        val english = build(all, config)
        val translated = build(all, config, names = russian)
        assertEquals(english.groupIds(), translated.groupIds())
        assertEquals("Игры", translated.groups.first { it.id == "category-0" }.title)
        assertEquals("Mine", translated.groups.first { it.id == "user-a" }.title)
    }

    private companion object {
        const val GAME = 0
        const val SOCIAL = 4
        const val PRODUCTIVITY = 7
    }
}
