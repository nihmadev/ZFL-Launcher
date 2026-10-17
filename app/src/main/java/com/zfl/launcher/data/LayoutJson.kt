package com.zfl.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * layout.json format. Version 3 stores the grid next to the cells it addresses, so a crash
 * can never leave cells of one grid with the size of another: the file is written in one
 * atomic rename (see LayoutFile). Version 2 and older had no grid; their cells belong to the
 * four-column grid with the rows saved in the settings, which the caller passes as legacyGrid.
 */

/** Current version of [toJson]. */
const val LAYOUT_JSON_VERSION: Int = 3

fun LayoutData.toJson(): String = JSONObject()
    .put("version", LAYOUT_JSON_VERSION)
    .put("grid", JSONObject().put("columns", grid.columns).put("rows", grid.rows))
    .put("pages", JSONArray(pages.map { page -> JSONArray(page.map(::itemToJson)) }))
    .put("dock", JSONArray(dock.map(::itemToJson)))
    .put("removed", JSONArray(removed.toList()))
    .put("today", JSONArray(today.map(::itemToJson)))
    .put("cells", JSONObject(cells))
    .apply { known?.let { put("known", JSONArray(it.toList())) } }
    .toString()

/**
 * Parses [toJson] output; returns null for unreadable data.
 *
 * @param legacyGrid grid of files written before the grid was saved in them (version 2 and older).
 */
fun layoutFromJson(json: String, legacyGrid: GridSpec): LayoutData? = runCatching {
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
    // Missing in files written before new installs were told apart (see LayoutData.known).
    val known = root.optJSONArray("known")?.let { arr -> List(arr.length()) { arr.getString(it) }.toSet() }
    LayoutData(pages, root.getJSONArray("dock").toItems(), removed.orEmpty(), today, cells, root.savedGrid() ?: legacyGrid, known)
}.getOrNull()

/**
 * The saved grid, kept as written even outside the sizes offered in settings, because the
 * cells are addressed in it; null when missing (an older file) or unusable.
 */
private fun JSONObject.savedGrid(): GridSpec? {
    val grid = optJSONObject("grid") ?: return null
    val columns = grid.optInt("columns", 0)
    val rows = grid.optInt("rows", 0)
    return if (columns in 1..MAX_SAVED_GRID && rows in 1..MAX_SAVED_GRID) GridSpec(columns, rows) else null
}

/** Sanity bound for grids read from a file. */
private const val MAX_SAVED_GRID = 16

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
            Span(obj.optInt("cols", 4).coerceIn(1, MAX_WIDGET_SPAN), obj.optInt("rows", 2).coerceIn(1, MAX_WIDGET_SPAN)),
        )
        obj.has("app") -> AppItem(obj.getString("app"))
        else -> null
    }
}.filterNotNull()

/** Largest hosted widget footprint read from a file, on either axis. */
private const val MAX_WIDGET_SPAN = 6
