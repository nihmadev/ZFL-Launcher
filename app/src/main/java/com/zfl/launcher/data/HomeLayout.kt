package com.zfl.launcher.data

import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.theme.Tokens

/** Something drawn in a home screen cell: [LayoutData] items resolved to real apps. */
sealed interface HomeEntry {
    val id: String
}

data class AppEntry(val app: AppInfo) : HomeEntry {
    override val id: String get() = app.key
}

data class FolderEntry(
    override val id: String,
    val name: String,
    val apps: List<AppInfo>,
) : HomeEntry

/** A widget drawn on a page: built-in ([WidgetEntry]) or another app's ([AppWidgetEntry]). */
sealed interface PageWidgetEntry : HomeEntry {
    val span: Span
}

data class WidgetEntry(
    override val id: String,
    val kind: WidgetKind,
    val size: WidgetSize,
    /** Version of the chosen photo ([WidgetKind.Photo]), null when none. */
    val photo: Long?,
) : PageWidgetEntry {
    override val span: Span get() = size.span
}

data class AppWidgetEntry(
    override val id: String,
    val appWidgetId: Int,
    /** Flattened AppWidgetProvider component. */
    val provider: String,
    override val span: Span,
) : PageWidgetEntry

/** The empty slot that opens up under the finger while dragging, as big as the dragged item. */
data class PlaceholderEntry(val span: Span = Span.One) : HomeEntry {
    override val id: String get() = "placeholder"
}

/** Grid footprint of an entry. */
val HomeEntry.span: Span
    get() = when (this) {
        is PageWidgetEntry -> span
        is PlaceholderEntry -> span
        else -> Span.One
    }

/** Everything the home screen renders. */
data class HomeState(
    val pages: List<List<HomeEntry>> = listOf(emptyList()),
    val dock: List<HomeEntry> = emptyList(),
    val rows: Int = Tokens.GridRows,
    /** Widgets of the Today page. */
    val today: List<PageWidgetEntry> = emptyList(),
    /** Resolved top-left cells, also shared by hit tests and drag previews. */
    val cells: Map<String, Int> = emptyMap(),
) {
    fun findEntry(id: String): HomeEntry? = (pages.flatten() + dock).firstOrNull { it.id == id }
}

/**
 * Resolves the saved [layout] against the installed [apps], applying custom labels.
 * Shared by the home screen and the live preview in settings, so both always agree.
 *
 * @param rows grid rows; when it differs from the layout's own (the settings preview),
 *   pages are re-flowed for the new size.
 */
fun buildHomeLayout(layout: LayoutData, apps: List<AppInfo>, prefs: SettingsState, rows: Int = prefs.gridRows): HomeState {
    val byKey = apps.associateBy { it.key }
    fun app(key: String): AppInfo? = byKey[key]?.let { app ->
        prefs.customLabels[key]?.let { AppInfo(app.info, it) } ?: app
    }
    fun resolve(item: HomeItem): HomeEntry? = when (item) {
        is AppItem -> app(item.appKey)?.let(::AppEntry)
        is FolderItem -> FolderEntry(item.id, item.name, item.apps.mapNotNull(::app)).takeIf { it.apps.isNotEmpty() }
        is PageWidget -> item.toEntry()
    }
    val arranged = layout.normalized(Tokens.GridColumns * rows, prefs.dockSlots).withPageCells(Tokens.GridColumns * rows)
    return HomeState(
        pages = arranged.pages.map { page -> page.mapNotNull(::resolve) },
        dock = arranged.dock.mapNotNull(::resolve),
        cells = arranged.cells,
        rows = rows,
        today = layout.today.map { it.toEntry() },
    )
}

private fun PageWidget.toEntry(): PageWidgetEntry = when (this) {
    is WidgetItem -> WidgetEntry(id, kind, size, photo)
    is AppWidgetItem -> AppWidgetEntry(id, appWidgetId, provider, span)
}
