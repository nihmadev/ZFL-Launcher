package com.zfl.launcher.data

/**
 * One persisted thing on the home screen or in the dock: an app or a folder of apps.
 * Apps are referenced by [AppInfo.key], so the layout survives app updates.
 */
sealed interface HomeItem {
    val id: String
}

data class AppItem(val appKey: String) : HomeItem {
    override val id: String get() = appKey
}

data class FolderItem(
    override val id: String,
    val name: String,
    /** App keys in display order. A folder always holds at least two apps. */
    val apps: List<String>,
) : HomeItem

/**
 * The user's home screen arrangement. Saved cells preserve intentional gaps;
 * layouts without saved cells retain their original automatic placement.
 */
data class LayoutData(
    val pages: List<List<HomeItem>>,
    val dock: List<HomeItem>,
    /**
     * Apps the user removed from the Home Screen (they stay installed, like iOS "Remove from
     * Home Screen"). They are not re-added automatically; newly installed apps are.
     */
    val removed: Set<String> = emptySet(),
    /** Widgets on the Today page (left of the first home page), top to bottom. */
    val today: List<PageWidget> = DEFAULT_TODAY_WIDGETS,
    /** Top-left grid cell by item id, for home pages only, addressed in [grid]. */
    val cells: Map<String, Int> = emptyMap(),
    /**
     * Grid of the home pages. It is saved with the cells (see [toJson]), so the two always
     * agree; every operation below places items on this grid.
     */
    val grid: GridSpec = GridSpec.Default,
    /**
     * Every installed app this layout has already seen (hidden ones and those of a paused
     * profile included), so [reconcile] can tell a new install from an app that was simply
     * not on the Home Screen. Null in layouts saved before it existed: the next reconcile
     * fills it in without treating any installed app as new.
     */
    val known: Set<String>? = null,
) {
    /** Every app key referenced anywhere, including inside folders. */
    fun allAppKeys(): Set<String> = (pages.flatten() + dock).flatMapTo(mutableSetOf()) { item ->
        when (item) {
            is AppItem -> listOf(item.appKey)
            is FolderItem -> item.apps
            is PageWidget -> emptyList()
        }
    }

    fun findFolder(id: String): FolderItem? =
        (pages.flatten() + dock).firstOrNull { it.id == id } as? FolderItem

    companion object {
        val Empty: LayoutData = LayoutData(pages = listOf(emptyList()), dock = emptyList())
    }
}

/** Where an item can be dropped. */
sealed interface DropPosition {
    /** Insert at [index] of home screen [page] (a page equal to the page count creates a new page). */
    data class Page(val page: Int, val index: Int) : DropPosition
    /** Place at an exact top-left grid cell, preserving empty space. */
    data class PageCell(val page: Int, val cell: Int) : DropPosition
    data class Dock(val index: Int) : DropPosition
    /** Reorder inside an open folder. */
    data class InFolder(val folderId: String, val index: Int) : DropPosition
    /** Drop onto another item: creates a folder, or adds to an existing one. */
    data class Merge(val targetId: String) : DropPosition
}

/** Dock slots on a fresh install, like iOS. */
const val DEFAULT_DOCK_CAPACITY: Int = 4

/** Dock slots the user can choose from. */
val DOCK_CAPACITY_OPTIONS: List<Int> = listOf(4, 5)

// region Pure layout operations. Each returns a new LayoutData; nothing is mutated.

/** Profile part of an [AppInfo.key] ("10" for a work profile), as used by [reconcile]. */
fun userOf(appKey: String): String = appKey.substringAfterLast('#', "")

/**
 * Brings the layout in sync with the installed apps, like iOS: drops uninstalled or hidden
 * apps, dissolves folders left with one app, and appends apps that should be on the Home
 * Screen but aren't at the end.
 *
 * Which apps are appended: a new install (not in [LayoutData.known]) unless [newApps] is
 * [NewAppPlacement.LibraryOnly], which marks it removed instead; and an app that left the
 * layout without being removed (an un-hidden app returns, it isn't a new install). Apps the
 * user removed stay removed. Changing [newApps] only affects apps installed afterwards.
 *
 * @param installed visible apps (installed, not hidden), in the order new ones are appended.
 * @param hidden installed but hidden apps: never placed, but known and removed stay remembered.
 * @param unavailableUsers profiles ([userOf]) that exist but are paused or locked now. Their
 *   apps missing from the list are kept in place, so the Home Screen doesn't lose them.
 */
fun LayoutData.reconcile(
    installed: List<String>,
    dockCapacity: Int = DEFAULT_DOCK_CAPACITY,
    hidden: Set<String> = emptySet(),
    unavailableUsers: Set<String> = emptySet(),
    newApps: NewAppPlacement = NewAppPlacement.HomeAndLibrary,
): LayoutData {
    val visible = installed.toSet()
    val present = visible + hidden
    fun suspended(key: String) = key !in present && userOf(key) in unavailableUsers
    fun keep(key: String) = key in visible || suspended(key)
    fun remembered(key: String) = key in present || suspended(key)
    fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
        when (item) {
            is AppItem -> item.takeIf { keep(it.appKey) }
            is FolderItem -> item.copy(apps = item.apps.filter(::keep)).simplified()
            is PageWidget -> item
        }
    }

    val cleanedPages = pages.map(::clean)
    val inherited = pages.flatten().filterIsInstance<FolderItem>().mapNotNull { folder ->
        val remaining = folder.apps.filter(::keep)
        if (remaining.size == 1) cells[folder.id]?.let { remaining.single() to it } else null
    }.toMap()
    val placed = (cleanedPages.flatten() + clean(dock)).flatMapTo(HashSet()) { item ->
        when (item) {
            is AppItem -> listOf(item.appKey)
            is FolderItem -> item.apps
            is PageWidget -> emptyList()
        }
    }
    // Seen for the first time; null `known` (an older layout) treats nothing as new.
    val discovered = known?.let { present - it }.orEmpty()
    val libraryOnly = if (newApps == NewAppPlacement.LibraryOnly) discovered - placed else emptySet()
    val cleaned = copy(
        pages = cleanedPages,
        dock = clean(dock),
        cells = cells + inherited,
        removed = removed.filterTo(mutableSetOf(), ::remembered) + libraryOnly,
        known = known.orEmpty().filterTo(mutableSetOf(), ::remembered) + present,
    )
    val toPlace = installed.filter { it !in placed && it !in cleaned.removed }.map(::AppItem)
    if (toPlace.isEmpty()) return cleaned.normalized(dockCapacity)

    val pages = cleaned.pages.toMutableList()
    val last = pages.lastOrNull().orEmpty()
    pages[pages.lastIndex.coerceAtLeast(0)] = last + toPlace
    return cleaned.copy(pages = pages).normalized(dockCapacity)
}

/**
 * Pushes overflow to the following pages (cascading) and removes empty pages. Dock items
 * beyond [dockCapacity] (the user chose a smaller dock) move to the end of the last page.
 */
fun LayoutData.normalized(dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    val overflow = dock.drop(dockCapacity)
    val withOverflow = if (overflow.isEmpty()) pages else pages.dropLast(1) + listOf(pages.lastOrNull().orEmpty() + overflow)
    val arranged = cascadePages(withOverflow, grid, dropEmpty = cells.isEmpty(), cell = { cells[it.id] }) { it.span }
        .let { pages -> if (cells.isEmpty()) pages else pages.dropLastWhile { it.isEmpty() }.ifEmpty { listOf(emptyList()) } }
    val next = copy(pages = arranged, dock = dock.take(dockCapacity))
    return if (cells.isEmpty()) next else next.withPageCells()
}

/** Removes the app or folder [id] from wherever it is; apps inside folders are taken out too. */
fun LayoutData.without(id: String): LayoutData {
    fun strip(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
        when {
            item.id == id -> null
            item is FolderItem && id in item.apps -> item.copy(apps = item.apps - id).simplified()
            else -> item.withoutStackItem(id)
        }
    }
    val nextPages = pages.map(::strip)
    val nextCells = cells.toMutableMap().apply {
        remove(id)
        // When a folder dissolves, its remaining app inherits the folder's cell.
        pages.flatten().filterIsInstance<FolderItem>().forEach { folder ->
            if (id in folder.apps && folder.apps.size == 2) cells[folder.id]?.let { put((folder.apps - id).single(), it) }
        }
        // Likewise when a stack is left with one widget.
        pages.flatten().filterIsInstance<WidgetStack>().forEach { stack ->
            val rest = stack.withoutStackItem(id)
            if (rest != null && rest.id != stack.id) cells[stack.id]?.let { put(rest.id, it) }
        }
    }
    val nextToday = today.mapNotNull { if (it.id == id) null else it.withoutStackItem(id) as PageWidget? }
    return copy(pages = nextPages, dock = strip(dock), today = nextToday, cells = nextCells)
}

/** "Add to Home Screen" (from the App Library): appends the app to the last page. */
fun LayoutData.addToHome(appKey: String, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    if (appKey in allAppKeys()) return this
    val pages = pages.toMutableList()
    pages[pages.lastIndex] = pages.last() + AppItem(appKey)
    return copy(pages = pages, removed = removed - appKey).normalized(dockCapacity)
}

/** "Remove from Home Screen": the app leaves the layout and is not re-added automatically. */
fun LayoutData.removeFromHome(appKey: String, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData =
    without(appKey).copy(removed = removed + appKey).normalized(dockCapacity)

/**
 * Moves [item] (an app or folder that may currently be anywhere) to [position].
 * Returns the unchanged layout when the drop is not allowed (e.g. full dock).
 */
fun LayoutData.move(
    item: HomeItem,
    position: DropPosition,
    dockCapacity: Int = DEFAULT_DOCK_CAPACITY,
    folderName: () -> String,
): LayoutData {
    val result = moveUnchecked(item, position, dockCapacity, folderName)
        // An app placed on the Home Screen is no longer "removed".
        .let { it.copy(removed = it.removed - it.allAppKeys()) }
    // Safety net: a drop must never lose an app (e.g. merging onto a folder that dissolved
    // when the item left it). If anything went missing, keep the old layout.
    return if (result.allAppKeys() == allAppKeys()) result else this
}

private fun LayoutData.moveUnchecked(
    item: HomeItem,
    position: DropPosition,
    dockCapacity: Int,
    folderName: () -> String,
): LayoutData {
    if (position is DropPosition.PageCell) return moveToCell(item, position, dockCapacity)
    if (position is DropPosition.InFolder) {
        // Reorder in place: taking the app out first could dissolve a two-app folder.
        val appKey = (item as? AppItem)?.appKey ?: return this
        return mapItems { existing ->
            if (existing is FolderItem && existing.id == position.folderId && appKey in existing.apps) {
                val apps = (existing.apps - appKey).toMutableList()
                apps.add(position.index.coerceIn(0, apps.size), appKey)
                existing.copy(apps = apps)
            } else existing
        }
    }
    // A widget dropped on a widget of its size makes (or grows) a stack.
    if (item is PageWidget && position is DropPosition.Merge) {
        return dropOnWidget(item, position.targetId, "stack-${System.currentTimeMillis()}", dockCapacity)
    }
    // Widgets live only on home pages: no dock, no folders.
    if (item is PageWidget && position !is DropPosition.Page) return this
    val rest = without(item.id)
    return when (position) {
        is DropPosition.Page -> {
            val pages = rest.pages.toMutableList()
            while (pages.size <= position.page) pages += emptyList<HomeItem>()
            val page = pages[position.page].toMutableList()
            page.add(position.index.coerceIn(0, page.size), item)
            pages[position.page] = page
            rest.copy(pages = pages).normalized(dockCapacity)
        }

        is DropPosition.Dock -> {
            if (rest.dock.size >= dockCapacity) return this
            val dock = rest.dock.toMutableList()
            dock.add(position.index.coerceIn(0, dock.size), item)
            rest.copy(dock = dock).normalized(dockCapacity)
        }

        is DropPosition.InFolder, is DropPosition.PageCell -> this // handled above

        is DropPosition.Merge -> {
            // Folders can't be nested: dropping a folder onto something just reorders nothing.
            val appKey = (item as? AppItem)?.appKey ?: return this
            if (isWidget(position.targetId)) return this
            rest.mapItems { existing ->
                when {
                    existing.id != position.targetId -> existing
                    existing is FolderItem -> existing.copy(apps = existing.apps + appKey)
                    existing is AppItem -> FolderItem(
                        id = "folder-${System.currentTimeMillis()}",
                        name = folderName(),
                        apps = listOf(existing.appKey, appKey),
                    )
                    else -> existing
                }
            }.normalized(dockCapacity)
        }
    }
}

fun LayoutData.renameFolder(id: String, name: String): LayoutData =
    mapItems { if (it is FolderItem && it.id == id) it.copy(name = name) else it }

private fun LayoutData.mapItems(transform: (HomeItem) -> HomeItem): LayoutData {
    val nextCells = cells.toMutableMap()
    fun replace(item: HomeItem): HomeItem = transform(item).also { next ->
        if (next.id != item.id) cells[item.id]?.let { nextCells[next.id] = it }
    }
    return copy(pages = pages.map { it.map(::replace) }, dock = dock.map(transform), cells = nextCells)
}

private fun LayoutData.isWidget(id: String): Boolean = pages.flatten().any { it is PageWidget && it.id == id }

/** A folder with one app becomes that app; an empty one disappears. */
private fun FolderItem.simplified(): HomeItem? = when (apps.size) {
    0 -> null
    1 -> AppItem(apps.single())
    else -> this
}

// endregion
