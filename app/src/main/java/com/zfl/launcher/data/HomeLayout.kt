package com.zfl.launcher.data

import com.zfl.launcher.settings.SettingsState

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

/** A Smart Stack ([WidgetStack]): built-in and hosted widgets of one size, [current] shown. */
data class WidgetStackEntry(
    override val id: String,
    val size: WidgetSize,
    val items: List<PageWidgetEntry>,
    val current: Int,
) : PageWidgetEntry {
    override val span: Span get() = size.span
    val currentItem: PageWidgetEntry get() = items[current.coerceIn(0, items.lastIndex)]
}

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
    /** Grid of the pages, the one [cells] are addressed in. */
    val grid: GridSpec = GridSpec.Default,
    /** Widgets of the Today page. */
    val today: List<PageWidgetEntry> = emptyList(),
    /** Resolved top-left cells, also shared by hit tests and drag previews. */
    val cells: Map<String, Int> = emptyMap(),
) {
    fun findEntry(id: String): HomeEntry? = (pages.flatten() + dock).firstOrNull { it.id == id }

    fun findStack(id: String): WidgetStackEntry? = (pages.flatten() + today).firstOrNull { it.id == id } as? WidgetStackEntry
}

/**
 * [reconcile] with what the launcher knows now: the installed apps and paused profiles of
 * [apps], the hidden apps, the dock size and where new installs go. The launcher and the
 * settings previews both use it, so they agree.
 */
fun LayoutData.syncedWith(apps: AppSnapshot, prefs: SettingsState): LayoutData {
    val (hidden, visible) = apps.apps.partition { it.key in prefs.hiddenApps }
    return reconcile(
        installed = visible.map { it.key },
        dockCapacity = prefs.dockSlots,
        hidden = hidden.mapTo(HashSet()) { it.key },
        unavailableUsers = apps.unavailableUsers,
        newApps = prefs.library.newApps,
    )
}

/**
 * Resolves the saved [layout] against the installed [apps], applying custom labels.
 * Shared by the home screen and the live preview in settings, so both always agree; the
 * preview of another grid passes the layout already moved to it ([withGrid]).
 */
fun buildHomeLayout(layout: LayoutData, apps: List<AppInfo>, prefs: SettingsState): HomeState {
    val byKey = apps.associateBy { it.key }
    fun app(key: String): AppInfo? = byKey[key]?.let { app ->
        prefs.customLabels[key]?.let(app::withLabel) ?: app
    }
    fun resolve(item: HomeItem): HomeEntry? = when (item) {
        is AppItem -> app(item.appKey)?.let(::AppEntry)
        is FolderItem -> FolderEntry(item.id, item.name, item.apps.mapNotNull(::app)).takeIf { it.apps.isNotEmpty() }
        is PageWidget -> item.toEntry()
    }
    val arranged = layout.normalized(prefs.dockSlots).withPageCells()
    return HomeState(
        pages = arranged.pages.map { page -> page.mapNotNull(::resolve) },
        dock = arranged.dock.mapNotNull(::resolve),
        cells = arranged.cells,
        grid = arranged.grid,
        today = layout.today.map { it.toEntry() },
    )
}

private fun PageWidget.toEntry(): PageWidgetEntry = when (this) {
    is WidgetItem -> WidgetEntry(id, kind, size, photo)
    is AppWidgetItem -> AppWidgetEntry(id, appWidgetId, provider, span)
    is WidgetStack -> WidgetStackEntry(id, size, items.map { it.toEntry() }, current)
}
