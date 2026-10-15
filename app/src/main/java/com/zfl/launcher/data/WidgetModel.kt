package com.zfl.launcher.data

/** Widget footprint on the home grid, in cells (the grid is 4 columns wide), like iOS. */
enum class WidgetSize(val span: Span) {
    Small(Span(2, 2)),
    Medium(Span(4, 2)),
    Large(Span(4, 4)),
}

/** Built-in widgets, each available in a few [sizes] (the order of the gallery pages). */
enum class WidgetKind(val sizes: List<WidgetSize>) {
    Clock(listOf(WidgetSize.Small, WidgetSize.Medium)),
    Calendar(listOf(WidgetSize.Small, WidgetSize.Medium)),
    Weather(listOf(WidgetSize.Small, WidgetSize.Medium, WidgetSize.Large)),
    Photo(listOf(WidgetSize.Small, WidgetSize.Medium, WidgetSize.Large)),
    Battery(listOf(WidgetSize.Small)),
    Suggestions(listOf(WidgetSize.Small, WidgetSize.Medium)),
}

/** Anything that lives on home pages and the Today page but never in the dock or folders. */
sealed interface PageWidget : HomeItem {
    val span: Span
}

/**
 * A built-in widget on a home screen page or on the Today page.
 *
 * @param photo version stamp of the image chosen for a [WidgetKind.Photo] widget
 *   (the file is named after [id]); null when none was chosen yet.
 */
data class WidgetItem(
    override val id: String,
    val kind: WidgetKind,
    val size: WidgetSize,
    val photo: Long? = null,
) : PageWidget {
    override val span: Span get() = size.span
}

/**
 * A widget of another app, hosted through Android's AppWidget framework.
 *
 * @param appWidgetId id allocated by our AppWidgetHost and bound to [provider].
 * @param provider the widget's AppWidgetProvider component, flattened.
 */
data class AppWidgetItem(
    override val id: String,
    val appWidgetId: Int,
    val provider: String,
    override val span: Span,
) : PageWidget

/** Where a new widget goes: the Today page, or a home screen page. */
sealed interface WidgetTarget {
    data object Today : WidgetTarget
    data class Page(val page: Int) : WidgetTarget
}

/** Widgets shown on the Today page of a fresh install, like iOS27L (minus its ad). */
val DEFAULT_TODAY_WIDGETS: List<WidgetItem> = listOf(
    WidgetItem("widget-today-suggestions", WidgetKind.Suggestions, WidgetSize.Medium),
    WidgetItem("widget-today-calendar", WidgetKind.Calendar, WidgetSize.Medium),
    WidgetItem("widget-today-clock", WidgetKind.Clock, WidgetSize.Small),
    WidgetItem("widget-today-battery", WidgetKind.Battery, WidgetSize.Small),
)

/** Every widget anywhere in the layout, built-in or hosted. */
fun LayoutData.allPageWidgets(): List<PageWidget> = pages.flatten().filterIsInstance<PageWidget>() + today

/** Every built-in widget anywhere in the layout. */
fun LayoutData.allWidgets(): List<WidgetItem> = allPageWidgets().filterIsInstance<WidgetItem>()

fun LayoutData.findWidget(id: String): PageWidget? = allPageWidgets().firstOrNull { it.id == id }

/** Removes hosted widgets whose app is gone ([isAlive] false), e.g. after an uninstall. */
fun LayoutData.withoutDeadAppWidgets(isAlive: (Int) -> Boolean, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    val dead = allPageWidgets().filterIsInstance<AppWidgetItem>().filterNot { isAlive(it.appWidgetId) }
    return if (dead.isEmpty()) this else dead.fold(this) { layout, w -> layout.without(w.id) }.normalized(pageCapacity, dockCapacity)
}

/**
 * Sizes offered for a hosted widget: its default size and the usual iOS-like sizes it
 * allows, within its min/max (in cells), smallest first.
 */
fun appWidgetSpans(default: Span, resizable: Boolean, min: Span, max: Span?): List<Span> {
    val candidates = if (resizable) listOf(default, Span(4, 1), Span(2, 2), Span(4, 2), Span(4, 4)) else listOf(default)
    return candidates.distinct()
        .filter { it == default || (it.columns >= min.columns && it.rows >= min.rows && (max == null || (it.columns <= max.columns && it.rows <= max.rows))) }
        .sortedWith(compareBy({ it.columns * it.rows }, { it.columns }))
}

/** Adds a widget at the top of a home page (pushing the rest down, like iOS) or at the end of Today. */
fun LayoutData.addWidget(widget: PageWidget, target: WidgetTarget, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData =
    when (target) {
        WidgetTarget.Today -> copy(today = today + widget)
        is WidgetTarget.Page -> {
            if (cells.isNotEmpty()) {
                moveToCell(widget, DropPosition.PageCell(target.page.coerceIn(0, pages.size), 0), pageCapacity, dockCapacity)
            } else {
                val pages = pages.toMutableList()
                val index = target.page.coerceIn(0, pages.size)
                if (index == pages.size) pages += listOf(widget) else pages[index] = listOf(widget) + pages[index]
                copy(pages = pages).normalized(pageCapacity, dockCapacity)
            }
        }
    }

/** Replaces the widget with the same id (new size or photo), re-flowing the pages. */
fun LayoutData.updateWidget(widget: PageWidget, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    fun swap(item: HomeItem): HomeItem = if (item.id == widget.id) widget else item
    val next = copy(
        pages = pages.map { page -> page.map(::swap) },
        today = today.map { if (it.id == widget.id) widget else it },
    )
    val page = pages.indexOfFirst { items -> items.any { it.id == widget.id } }
    val cell = cells[widget.id]
    return if (page >= 0 && cell != null) {
        next.moveToCell(widget, DropPosition.PageCell(page, cell), pageCapacity, dockCapacity)
    } else next.normalized(pageCapacity, dockCapacity)
}

fun LayoutData.removeWidget(id: String, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData =
    without(id).normalized(pageCapacity, dockCapacity)
