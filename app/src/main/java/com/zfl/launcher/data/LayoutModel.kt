package com.zfl.launcher.data

import org.json.JSONArray
import org.json.JSONObject

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
    /** Top-left grid cell by item id, for home pages only. */
    val cells: Map<String, Int> = emptyMap(),
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

    fun toJson(): String = JSONObject()
        .put("version", 2)
        .put("pages", JSONArray(pages.map { page -> JSONArray(page.map(::itemToJson)) }))
        .put("dock", JSONArray(dock.map(::itemToJson)))
        .put("removed", JSONArray(removed.toList()))
        .put("today", JSONArray(today.map(::itemToJson)))
        .put("cells", JSONObject(cells))
        .toString()

    companion object {
        val Empty: LayoutData = LayoutData(pages = listOf(emptyList()), dock = emptyList())

        /** Parses [toJson] output; returns null for unreadable data. */
        fun fromJson(json: String): LayoutData? = runCatching {
            val root = JSONObject(json)
            val pages = root.getJSONArray("pages").let { arr ->
                List(arr.length()) { i -> arr.getJSONArray(i).toItems() }
            }
            val removed = root.optJSONArray("removed")?.let { arr -> List(arr.length()) { arr.getString(it) }.toSet() }
            // Layouts saved before widgets existed get the default Today page.
            val today = root.optJSONArray("today")?.toItems()?.filterIsInstance<PageWidget>() ?: DEFAULT_TODAY_WIDGETS
            val cells = root.optJSONObject("cells")?.let { saved ->
                saved.keys().asSequence().mapNotNull { id ->
                    saved.optInt(id, -1).takeIf { it >= 0 }?.let { id to it }
                }.toMap()
            }.orEmpty()
            LayoutData(pages, root.getJSONArray("dock").toItems(), removed.orEmpty(), today, cells)
        }.getOrNull()

        private fun itemToJson(item: HomeItem): JSONObject = when (item) {
            is AppItem -> JSONObject().put("app", item.appKey)
            is FolderItem -> JSONObject()
                .put("folder", item.id)
                .put("name", item.name)
                .put("apps", JSONArray(item.apps))
            is WidgetItem -> JSONObject()
                .put("widget", item.id)
                .put("kind", item.kind.name)
                .put("size", item.size.name)
                .apply { item.photo?.let { put("photo", it) } }
            is AppWidgetItem -> JSONObject()
                .put("appwidget", item.id)
                .put("widgetId", item.appWidgetId)
                .put("provider", item.provider)
                .put("cols", item.span.columns)
                .put("rows", item.span.rows)
            // "widget" + an unknown "kind" make builds without stacks skip this entry instead of
            // failing to read the whole layout.
            is WidgetStack -> JSONObject()
                .put("stack", item.id)
                .put("widget", item.id)
                .put("kind", "stack")
                .put("size", item.size.name)
                .put("current", item.current)
                .put("items", JSONArray(item.items.map(::itemToJson)))
        }

        /** Entries this version can't read (from a newer one) are skipped, the rest is kept. */
        private fun JSONArray.toItems(): List<HomeItem> = List(length()) { i ->
            val obj = optJSONObject(i) ?: return@List null
            when {
                obj.has("stack") -> {
                    val size = WidgetSize.entries.firstOrNull { it.name == obj.optString("size") } ?: return@List null
                    val items = obj.optJSONArray("items")?.toItems()?.filterIsInstance<PageWidget>().orEmpty()
                    WidgetStack(obj.getString("stack"), size, items, obj.optInt("current", 0)).normalizedStack()
                }
                obj.has("folder") -> {
                    val apps = obj.getJSONArray("apps")
                    FolderItem(obj.getString("folder"), obj.optString("name"), List(apps.length()) { apps.getString(it) })
                }
                obj.has("widget") -> {
                    // Unknown kinds/sizes (from a newer version) are skipped instead of failing the whole layout.
                    val kind = WidgetKind.entries.firstOrNull { it.name == obj.optString("kind") } ?: return@List null
                    val size = WidgetSize.entries.firstOrNull { it.name == obj.optString("size") } ?: kind.sizes.first()
                    WidgetItem(obj.getString("widget"), kind, size, obj.optLong("photo").takeIf { obj.has("photo") })
                }
                obj.has("appwidget") -> AppWidgetItem(
                    obj.getString("appwidget"),
                    obj.getInt("widgetId"),
                    obj.getString("provider"),
                    Span(obj.optInt("cols", 4).coerceIn(1, 4), obj.optInt("rows", 2).coerceIn(1, 6)),
                )
                obj.has("app") -> AppItem(obj.getString("app"))
                else -> null
            }
        }.filterNotNull()
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

/**
 * Brings the layout in sync with [installed] (the visible apps): drops uninstalled or
 * hidden apps, dissolves folders left with one app, and appends new (or un-hidden) apps
 * at the end, like iOS.
 */
fun LayoutData.reconcile(installed: List<String>, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    val installedSet = installed.toSet()
    fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
        when (item) {
            is AppItem -> item.takeIf { it.appKey in installedSet }
            is FolderItem -> item.copy(apps = item.apps.filter { it in installedSet }).simplified()
            is PageWidget -> item
        }
    }

    val cleanedPages = pages.map(::clean)
    val inherited = pages.flatten().filterIsInstance<FolderItem>().mapNotNull { folder ->
        val remaining = folder.apps.filter { it in installedSet }
        if (remaining.size == 1) cells[folder.id]?.let { remaining.single() to it } else null
    }.toMap()
    val cleaned = copy(pages = cleanedPages, dock = clean(dock), cells = cells + inherited,
        removed = removed.filterTo(mutableSetOf()) { it in installedSet })
    val known = cleaned.allAppKeys()
    val newApps = installed.filter { it !in known && it !in cleaned.removed }.map(::AppItem)
    if (newApps.isEmpty()) return cleaned.normalized(pageCapacity, dockCapacity)

    val pages = cleaned.pages.toMutableList()
    val last = pages.lastOrNull().orEmpty()
    pages[pages.lastIndex.coerceAtLeast(0)] = last + newApps
    return cleaned.copy(pages = pages).normalized(pageCapacity, dockCapacity)
}

/**
 * Pushes overflow to the following pages (cascading) and removes empty pages. Dock items
 * beyond [dockCapacity] (the user chose a smaller dock) move to the end of the last page.
 */
fun LayoutData.normalized(pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    val overflow = dock.drop(dockCapacity)
    val withOverflow = if (overflow.isEmpty()) pages else pages.dropLast(1) + listOf(pages.lastOrNull().orEmpty() + overflow)
    val arranged = cascadePages(withOverflow, pageCapacity, dropEmpty = cells.isEmpty(), cell = { cells[it.id] }) { it.span }
        .let { pages -> if (cells.isEmpty()) pages else pages.dropLastWhile { it.isEmpty() }.ifEmpty { listOf(emptyList()) } }
    val next = copy(pages = arranged, dock = dock.take(dockCapacity))
    return if (cells.isEmpty()) next else next.withPageCells(pageCapacity)
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
fun LayoutData.addToHome(appKey: String, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    if (appKey in allAppKeys()) return this
    val pages = pages.toMutableList()
    pages[pages.lastIndex] = pages.last() + AppItem(appKey)
    return copy(pages = pages, removed = removed - appKey).normalized(pageCapacity, dockCapacity)
}

/** "Remove from Home Screen": the app leaves the layout and is not re-added automatically. */
fun LayoutData.removeFromHome(appKey: String, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData =
    without(appKey).copy(removed = removed + appKey).normalized(pageCapacity, dockCapacity)

/**
 * Moves [item] (an app or folder that may currently be anywhere) to [position].
 * Returns the unchanged layout when the drop is not allowed (e.g. full dock).
 */
fun LayoutData.move(
    item: HomeItem,
    position: DropPosition,
    pageCapacity: Int,
    dockCapacity: Int = DEFAULT_DOCK_CAPACITY,
    folderName: () -> String,
): LayoutData {
    val result = moveUnchecked(item, position, pageCapacity, dockCapacity, folderName)
        // An app placed on the Home Screen is no longer "removed".
        .let { it.copy(removed = it.removed - it.allAppKeys()) }
    // Safety net: a drop must never lose an app (e.g. merging onto a folder that dissolved
    // when the item left it). If anything went missing, keep the old layout.
    return if (result.allAppKeys() == allAppKeys()) result else this
}

private fun LayoutData.moveUnchecked(
    item: HomeItem,
    position: DropPosition,
    pageCapacity: Int,
    dockCapacity: Int,
    folderName: () -> String,
): LayoutData {
    if (position is DropPosition.PageCell) return moveToCell(item, position, pageCapacity, dockCapacity)
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
        return dropOnWidget(item, position.targetId, "stack-${System.currentTimeMillis()}", pageCapacity, dockCapacity)
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
            rest.copy(pages = pages).normalized(pageCapacity, dockCapacity)
        }

        is DropPosition.Dock -> {
            if (rest.dock.size >= dockCapacity) return this
            val dock = rest.dock.toMutableList()
            dock.add(position.index.coerceIn(0, dock.size), item)
            rest.copy(dock = dock).normalized(pageCapacity, dockCapacity)
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
            }.normalized(pageCapacity, dockCapacity)
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
