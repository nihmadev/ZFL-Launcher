package com.zfl.launcher.data

/** How the App Library page shows the apps. */
enum class LibraryView { Categories, Grid, List }

/** Where an app installed after the first launch goes. */
enum class NewAppPlacement {
    /** On the Home Screen (end of the last page) and in the App Library, like iOS. */
    HomeAndLibrary,
    /** Only in the App Library and search; "Add to Home Screen" places it on demand. */
    LibraryOnly,
}

/** A category made by the user; [id] never changes, [name] is the user's own text. */
data class UserCategory(val id: String, val name: String)

/**
 * The user's App Library choices. Only preferences are stored here, never the app list: groups
 * are computed from the installed apps by [buildLibrary] each time.
 *
 * App keys of apps that are missing right now (uninstalled, hidden, a paused work profile)
 * are kept, so a reinstalled app returns to its category and pin; [withOrphansCapped] bounds them.
 */
data class LibraryConfig(
    val view: LibraryView = LibraryView.Categories,
    val showSuggestions: Boolean = true,
    val showRecent: Boolean = true,
    val newApps: NewAppPlacement = NewAppPlacement.HomeAndLibrary,
    /** User categories, in the order they were created. */
    val categories: List<UserCategory> = emptyList(),
    /** App key → regular group id ([LibraryIds]); a key without an entry uses its system category. */
    val assignments: Map<String, String> = emptyMap(),
    /** Pinned app keys, in display order. */
    val pinned: List<String> = emptyList(),
    /** Regular group ids in the order the user arranged them; empty means automatic order. */
    val groupOrder: List<String> = emptyList(),
    /** Manual order of the full grid; an empty list keeps the original automatic view. */
    val gridOrder: List<String> = emptyList(),
    /** Manual app order inside regular categories. */
    val groupAppOrder: Map<String, List<String>> = emptyMap(),
) {
    fun category(id: String): UserCategory? = categories.firstOrNull { it.id == id }
}

/**
 * Stable group ids. They never depend on a translated name, so a language change keeps
 * assignments and order. System ids are the ones the App Library always used.
 */
object LibraryIds {
    const val PINNED: String = "pinned"
    const val SUGGESTIONS: String = "suggestions"
    const val RECENT: String = "recent"
    const val OTHER: String = "other"
    private const val SYSTEM_PREFIX = "category-"
    private const val USER_PREFIX = "user-"

    fun system(category: Int): String = if (category < 0) OTHER else "$SYSTEM_PREFIX$category"

    /** The system category of a system group id, -1 for [OTHER], null for any other id. */
    fun systemCategory(id: String): Int? = when {
        id == OTHER -> UNDEFINED_CATEGORY
        id.startsWith(SYSTEM_PREFIX) -> id.removePrefix(SYSTEM_PREFIX).toIntOrNull()?.takeIf { it >= 0 }
        else -> null
    }

    fun isUser(id: String): Boolean = id.startsWith(USER_PREFIX)

    /** A new user category id that isn't used yet; [seed] makes it unique (a timestamp). */
    fun newUser(seed: Long, taken: Collection<String>): String {
        var n = seed
        while ("$USER_PREFIX${n.toString(36)}" in taken) n++
        return "$USER_PREFIX${n.toString(36)}"
    }
}

/** [android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED], kept here so this file is plain Kotlin. */
const val UNDEFINED_CATEGORY: Int = -1

const val MAX_USER_CATEGORIES: Int = 50
const val MAX_CATEGORY_NAME: Int = 40

/** Missing apps whose assignment or pin is remembered, per kind; the oldest go first. */
const val MAX_REMEMBERED_MISSING: Int = 100

/** Why a category name was refused. */
enum class NameProblem { Empty, Duplicate, TooMany }

/** Result of creating or renaming a category. */
sealed interface CategoryEdit {
    data class Done(val config: LibraryConfig, val id: String) : CategoryEdit
    data class Refused(val problem: NameProblem) : CategoryEdit
}

// region Pure operations. Each returns a new LibraryConfig; nothing is mutated.

/**
 * A new empty category named [name] (trimmed). Names must differ from the other user
 * categories and from [reserved] (the system groups shown now), ignoring case.
 */
fun LibraryConfig.createCategory(name: String, id: String, reserved: Collection<String>): CategoryEdit {
    if (categories.size >= MAX_USER_CATEGORIES) return CategoryEdit.Refused(NameProblem.TooMany)
    val clean = cleanName(name, ownId = null, reserved) ?: return refusal(name)
    return CategoryEdit.Done(copy(categories = categories + UserCategory(id, clean)), id)
}

fun LibraryConfig.renameCategory(id: String, name: String, reserved: Collection<String>): CategoryEdit {
    val existing = category(id) ?: return CategoryEdit.Refused(NameProblem.Empty)
    val clean = cleanName(name, ownId = id, reserved) ?: return refusal(name)
    if (clean == existing.name) return CategoryEdit.Done(this, id)
    return CategoryEdit.Done(copy(categories = categories.map { if (it.id == id) it.copy(name = clean) else it }), id)
}

private fun LibraryConfig.refusal(name: String): CategoryEdit.Refused =
    CategoryEdit.Refused(if (name.trim().isEmpty()) NameProblem.Empty else NameProblem.Duplicate)

private fun LibraryConfig.cleanName(name: String, ownId: String?, reserved: Collection<String>): String? {
    val clean = name.trim().take(MAX_CATEGORY_NAME).trim()
    if (clean.isEmpty()) return null
    val others = categories.filter { it.id != ownId }.map { it.name } + reserved
    return clean.takeIf { others.none { other -> other.equals(clean, ignoreCase = true) } }
}

/** Deletes a user category; its apps go back to their automatic category. Apps and the Home Screen stay as they are. */
fun LibraryConfig.deleteCategory(id: String): LibraryConfig = copy(
    categories = categories.filter { it.id != id },
    assignments = assignments.filterValues { it != id },
    groupOrder = groupOrder - id,
    groupAppOrder = groupAppOrder - id,
)

/**
 * Puts the app in the regular group [groupId] (a user category or a system group), or back
 * in its automatic one when null. An unknown user category changes nothing.
 */
fun LibraryConfig.assign(appKey: String, groupId: String?): LibraryConfig {
    if (groupId == null) return if (appKey in assignments) copy(assignments = assignments - appKey) else this
    val valid = if (LibraryIds.isUser(groupId)) category(groupId) != null else LibraryIds.systemCategory(groupId) != null
    if (!valid || assignments[appKey] == groupId) return this
    // Re-inserted at the end: the newest choices are the last ones forgotten (withOrphansCapped).
    return copy(assignments = (assignments - appKey) + (appKey to groupId))
}

fun LibraryConfig.pin(appKey: String): LibraryConfig = if (appKey in pinned) this else copy(pinned = pinned + appKey)

fun LibraryConfig.unpin(appKey: String): LibraryConfig = if (appKey in pinned) copy(pinned = pinned - appKey) else this

/**
 * Moves pinned app [appKey] to position [to] among the pins that are shown ([shown]). Pins of
 * missing apps aren't shown; they keep their order after the shown ones.
 */
fun LibraryConfig.movePinned(appKey: String, to: Int, shown: Collection<String>): LibraryConfig {
    val shownSet = shown.toSet()
    val visible = pinned.filter { it in shownSet }
    val from = visible.indexOf(appKey)
    if (from < 0) return this
    val next = visible.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) }
    return if (next == visible) this else copy(pinned = next + pinned.filter { it !in shownSet })
}

/**
 * Moves the group at [from] to [to] in [order], the regular groups as they are shown now
 * ([LibraryState.allGroups]). From then on the order is the user's: it no longer follows
 * the number of apps. Saved ids of groups that are empty right now are kept after them.
 */
fun LibraryConfig.moveGroup(order: List<String>, from: Int, to: Int): LibraryConfig {
    if (from !in order.indices) return this
    val next = order.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) }
    return copy(groupOrder = next + groupOrder.filter { it !in next })
}

/** Back to the automatic order: user categories, then system groups by size, "Other" last. */
fun LibraryConfig.resetGroupOrder(): LibraryConfig = copy(groupOrder = emptyList())

/**
 * Forgets the oldest assignments and pins of missing apps beyond [MAX_REMEMBERED_MISSING].
 * An app counts as missing when it isn't in [present]; apps of [keepUsers] (profiles that are
 * paused or locked right now) are never forgotten.
 */
fun LibraryConfig.withOrphansCapped(present: Set<String>, keepUsers: Set<String>): LibraryConfig {
    fun forgettable(key: String) = key !in present && userOf(key) !in keepUsers
    fun <T> List<T>.capped(key: (T) -> String): List<T> {
        val extra = count { forgettable(key(it)) } - MAX_REMEMBERED_MISSING
        if (extra <= 0) return this
        var dropped = 0
        return filter { item -> !(forgettable(key(item)) && dropped < extra).also { if (it) dropped++ } }
    }
    val nextAssignments = assignments.entries.toList().capped { it.key }.associate { it.key to it.value }
    val nextPinned = pinned.capped { it }
    val nextGrid = gridOrder.capped { it }
    val nextGroups = groupAppOrder.mapValues { (_, apps) -> apps.capped { it } }
    return if (nextAssignments.size == assignments.size && nextPinned.size == pinned.size && nextGrid.size == gridOrder.size && nextGroups == groupAppOrder) this
    else copy(assignments = nextAssignments, pinned = nextPinned, gridOrder = nextGrid, groupAppOrder = nextGroups)
}

// endregion
