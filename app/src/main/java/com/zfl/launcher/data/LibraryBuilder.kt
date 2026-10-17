package com.zfl.launcher.data

import java.text.Collator
import java.util.Locale

/** What the App Library needs to know about an app; [AppInfo] in the launcher. */
interface LibraryApp {
    /** [AppInfo.key]: component and user, so the same package of two profiles stays apart. */
    val key: String
    /** Label as shown, with the user's custom name applied. */
    val label: String
    /** First install time, 0 when unknown. */
    val installTime: Long
    /** [android.content.pm.ApplicationInfo.category], [UNDEFINED_CATEGORY] when none. */
    val systemCategory: Int
}

/**
 * Everything that depends on the language: group names, read from Android at the UI boundary
 * ([libraryTitles]) so building the library stays plain Kotlin, and the [locale] names are
 * sorted in. Categories missing from [system] are shown under "Other".
 */
data class LibraryTitles(
    val locale: Locale,
    val system: Map<Int, String>,
    val other: String,
    val suggestions: String,
    val recent: String,
    val pinned: String,
)

enum class GroupKind { Pinned, Suggestions, Recent, System, User }

/** One group of apps: a card in the Categories view, or the full screen of one. */
data class LibraryGroup<T>(val id: String, val title: String, val kind: GroupKind, val apps: List<T>)

/** A regular group (system or user) for the category editor and the order list. */
data class GroupSummary(val id: String, val title: String, val kind: GroupKind, val size: Int)

/** Apps whose label starts with [letter] ("#" for digits and symbols). */
data class LetterSection<T>(val letter: String, val apps: List<T>)

/** Everything the three App Library views, the category screen and the editors show. */
data class LibraryState<T>(
    val view: LibraryView = LibraryView.Categories,
    /** Pinned apps in the user's order; only apps that are visible now. */
    val pinned: List<T> = emptyList(),
    /** Empty when turned off or nothing was launched yet. */
    val suggestions: List<T> = emptyList(),
    /** Empty when turned off. */
    val recent: List<T> = emptyList(),
    /** Regular groups that have apps, in order. */
    val groups: List<LibraryGroup<T>> = emptyList(),
    /** Regular groups in order, including empty user categories. */
    val allGroups: List<GroupSummary> = emptyList(),
    /** Every visible app by name. */
    val alphabetical: List<T> = emptyList(),
    val sections: List<LetterSection<T>> = emptyList(),
    /** App key → id of its regular group. */
    val groupOf: Map<String, String> = emptyMap(),
    /** App key → its automatic group, only for apps the user put in another group. */
    val autoGroupOf: Map<String, String> = emptyMap(),
    /** Cards of the Categories view: pinned, the smart groups, then the regular groups. */
    val cards: List<LibraryGroup<T>> = emptyList(),
) {
    fun summary(id: String): GroupSummary? = allGroups.firstOrNull { it.id == id }

    /** The group [appKey] would be in without the user's choice. */
    fun autoGroup(appKey: String): String? = autoGroupOf[appKey] ?: groupOf[appKey]
}

/**
 * Builds the App Library from the visible apps (hidden ones already left out, custom labels
 * applied). Every app is in exactly one regular group: the one the user assigned, otherwise
 * its system category, "Other" without one. Pinned, Suggestions and Recently Added are extra
 * views of the same apps. Smart group titles are filled in by [titles] where shown.
 */
fun <T : LibraryApp> buildLibrary(
    apps: List<T>,
    scores: Map<String, Double>,
    config: LibraryConfig,
    titles: LibraryTitles,
): LibraryState<T> {
    val collator = Collator.getInstance(titles.locale)
    val byName = compareBy<T, String>(collator) { it.label }.thenBy { it.key }
    val byUsage = compareByDescending<T> { scores[it.key] ?: 0.0 }.then(byName)

    fun naturalGroup(app: T): String =
        if (app.systemCategory in titles.system) LibraryIds.system(app.systemCategory) else LibraryIds.OTHER
    fun groupOf(app: T): String {
        val assigned = config.assignments[app.key] ?: return naturalGroup(app)
        return when {
            LibraryIds.isUser(assigned) -> assigned.takeIf { config.category(it) != null }
            LibraryIds.systemCategory(assigned).let { it != null && (it == UNDEFINED_CATEGORY || it in titles.system) } -> assigned
            else -> null
        } ?: naturalGroup(app)
    }

    val groupIds = apps.associate { it.key to groupOf(it) }
    val members = apps.groupBy { groupIds.getValue(it.key) }
    fun title(id: String): String = config.category(id)?.name
        ?: LibraryIds.systemCategory(id)?.let { titles.system[it] }
        ?: titles.other
    fun summary(id: String): GroupSummary = GroupSummary(
        id = id,
        title = title(id),
        kind = if (LibraryIds.isUser(id)) GroupKind.User else GroupKind.System,
        size = members[id]?.size ?: 0,
    )

    val systemIds = members.keys.filter { !LibraryIds.isUser(it) }
    val userIds = config.categories.map { it.id }
    val order = orderGroups(userIds, systemIds, config.groupOrder, sizeOf = { members[it]?.size ?: 0 })
    val allGroups = order.map(::summary)

    val visibleKeys = apps.associateBy { it.key }
    val alphabetical = apps.sortedWith(byName)
    val pinned = config.pinned.mapNotNull { visibleKeys[it] }
    val suggestions = if (!config.showSuggestions) emptyList() else
        apps.filter { (scores[it.key] ?: 0.0) > 0.0 }.sortedWith(byUsage).take(SMART_GROUP_SIZE)
    val recent = if (!config.showRecent) emptyList() else
        apps.filter { it.installTime > 0 }.sortedWith(compareByDescending<T> { it.installTime }.thenBy { it.key }).take(SMART_GROUP_SIZE)
    val groups = allGroups.filter { it.size > 0 }.map { group ->
        LibraryGroup(group.id, group.title, group.kind, members.getValue(group.id).sortedWith(byUsage))
    }
    val cards = listOfNotNull(
        LibraryGroup(LibraryIds.PINNED, titles.pinned, GroupKind.Pinned, pinned).takeIf { pinned.isNotEmpty() },
        LibraryGroup(LibraryIds.SUGGESTIONS, titles.suggestions, GroupKind.Suggestions, suggestions).takeIf { suggestions.isNotEmpty() },
        LibraryGroup(LibraryIds.RECENT, titles.recent, GroupKind.Recent, recent).takeIf { recent.isNotEmpty() },
    ) + groups
    return LibraryState(
        view = config.view,
        pinned = pinned,
        suggestions = suggestions,
        recent = recent,
        groups = groups,
        allGroups = allGroups,
        alphabetical = alphabetical,
        sections = letterSections(alphabetical) { it.label },
        groupOf = groupIds,
        autoGroupOf = apps.filter { it.key in config.assignments }
            .associate { it.key to naturalGroup(it) }
            .filter { (key, auto) -> groupIds[key] != auto },
        cards = cards,
    )
}

/**
 * Order of the regular groups. Groups the user arranged come first, in their order; the rest
 * follow deterministically. Without any arrangement: user categories, system categories by
 * size (as the App Library always did), "Other" last. After an arrangement, groups that appear
 * later (a new system category, a new user category) don't reshuffle the user's order: they
 * follow it, user categories first, then system ones by category number, "Other" last.
 */
internal fun orderGroups(
    userIds: List<String>,
    systemIds: Collection<String>,
    saved: List<String>,
    sizeOf: (String) -> Int,
): List<String> {
    val existing = (userIds + systemIds).toSet()
    val arranged = saved.filter { it in existing }.distinct()
    val rest = existing - arranged.toSet()
    val bySystem = compareBy<String> { if (it == LibraryIds.OTHER) 1 else 0 }
    val systemRest = systemIds.filter { it in rest }.sortedWith(
        if (saved.isEmpty()) bySystem.thenByDescending(sizeOf).thenBy { LibraryIds.systemCategory(it) }
        else bySystem.thenBy { LibraryIds.systemCategory(it) },
    )
    return arranged + userIds.filter { it in rest } + systemRest
}

/**
 * [items] grouped by the first letter of [label], keeping their order; digits and symbols go
 * under "#". Each letter appears once, even when the order puts it in two places.
 */
fun <T> letterSections(items: List<T>, label: (T) -> String): List<LetterSection<T>> =
    items.groupBy { sectionLetter(label(it)) }.map { (letter, apps) -> LetterSection(letter, apps) }

fun sectionLetter(label: String): String {
    val first = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first.isLetter()) first.toString() else "#"
}

/** Apps of a smart group. */
private const val SMART_GROUP_SIZE = 8
