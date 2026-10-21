package com.zfl.launcher.ui.home

import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.zfl.launcher.LauncherViewModel
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.AppWidgetEntry
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.ui.menu.LibraryMenuActions
import com.zfl.launcher.ui.menu.WidgetMenuActions
import com.zfl.launcher.ui.widgets.suggestionAt
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.ui.library.LibraryCallbacks
import com.zfl.launcher.ui.search.SearchCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Connects icon gestures to the view model and the drag controller for one [source]. */
internal fun iconCallbacks(
    vm: LauncherViewModel,
    actions: LauncherActions,
    drag: DragController,
    source: DragSource,
): HomeIconCallbacks = object : HomeIconCallbacks {
    override fun onTap(entry: HomeEntry, iconBounds: Rect) {
        when (entry) {
            is AppEntry -> if (!vm.editMode.value) {
                if (source == DragSource.Folder) vm.closeFolder()
                actions.launch(entry.app, iconBounds)
            }
            is FolderEntry -> vm.openFolder(entry.id, iconBounds)
            is PlaceholderEntry, is PageWidgetEntry -> Unit
        }
    }

    override fun onLongPress(entry: HomeEntry, iconBounds: Rect, pointer: Offset) {
        vm.openMenu(entry, iconBounds, inDock = source == DragSource.Dock)
        // A locked layout shows the menu, but the icon can't be dragged out of it.
        if (vm.canArrange) drag.prepare(entry, source, iconBounds, pointer)
    }

    override fun onDragStart(entry: HomeEntry, iconBounds: Rect, pointer: Offset) {
        if (vm.canArrange) drag.start(entry, source, iconBounds, pointer)
    }

    override fun onRemove(entry: AppEntry) {
        vm.removeRequest.value = entry.app
    }
}

/**
 * Widget gestures: a tap does what the widget is about (open the clock, the calendar…),
 * a long-press opens the widget menu and, on home pages, may turn into a drag.
 */
internal fun widgetCallbacks(
    vm: LauncherViewModel,
    actions: LauncherActions,
    drag: DragController,
    onToday: Boolean,
): WidgetCallbacks = object : WidgetCallbacks {
    override fun onTap(entry: PageWidgetEntry, position: Offset, bounds: Rect) {
        // Other apps' widgets handle their own taps.
        if (vm.editMode.value || entry !is WidgetEntry) return
        val environment = vm.widgetEnvironment.value
        when (entry.kind) {
            WidgetKind.Clock -> actions.openClock()
            WidgetKind.Calendar -> if (environment.calendarAllowed) actions.openCalendar() else actions.requestCalendarPermission()
            WidgetKind.Weather -> actions.openWeatherSettings()
            WidgetKind.Photo -> if (entry.photo == null) actions.pickWidgetPhoto(entry.id)
            WidgetKind.Battery -> actions.openBatterySettings()
            WidgetKind.Suggestions -> suggestionAt(entry, environment.suggestions, position)?.let { actions.launch(it, bounds) }
        }
    }

    override fun onLongPress(entry: PageWidgetEntry, bounds: Rect, pointer: Offset) {
        vm.openMenu(entry, bounds, inDock = false)
        if (!onToday && vm.canArrange) drag.prepare(entry, DragSource.Page, bounds, pointer)
    }

    override fun onDragStart(entry: PageWidgetEntry, bounds: Rect, pointer: Offset) {
        if (!onToday && vm.canArrange) drag.start(entry, DragSource.Page, bounds, pointer)
    }

    override fun onRemove(entry: PageWidgetEntry) = vm.removeWidget(entry.id)

    override fun onStackSettled(stackId: String, index: Int) = vm.stacks.show(stackId, index)
}

internal fun widgetMenuActions(vm: LauncherViewModel, actions: LauncherActions): WidgetMenuActions = object : WidgetMenuActions {
    override fun resize(id: String, span: Span) = vm.resizeWidget(id, span)
    override fun remove(id: String) = vm.removeWidget(id)
    override fun pickPhoto(id: String) {
        vm.closeMenu()
        actions.pickWidgetPhoto(id)
    }

    override fun sizes(widget: PageWidgetEntry): List<Span> = when (widget) {
        is WidgetEntry -> widget.kind.sizes.map { it.span }
        is AppWidgetEntry -> vm.appWidgets.info(widget.appWidgetId)?.let { vm.appWidgets.spans(it, vm.state.value.grid) } ?: listOf(widget.span)
        // Every size one of its widgets takes; the others are shown but unavailable.
        is WidgetStackEntry -> widget.items.flatMap(::sizes).filter { span -> WidgetSize.entries.any { it.span == span } }.distinct()
            .sortedWith(compareBy({ it.columns * it.rows }, { it.columns }))
    }

    override fun isSizeAvailable(widget: PageWidgetEntry, span: Span): Boolean =
        widget !is WidgetStackEntry || widget.items.all { span in sizes(it) }

    override fun editStack(id: String) = vm.stacks.openEditor(id)
}

internal fun stackEditorActions(vm: LauncherViewModel): StackEditorActions = object : StackEditorActions {
    override fun move(stackId: String, from: Int, to: Int) = vm.stacks.move(stackId, from, to)
    override fun remove(stackId: String, itemId: String) = vm.stacks.remove(stackId, itemId)
    override fun takeOut(stackId: String, itemId: String) = vm.stacks.takeOut(stackId, itemId)
    override fun close() = vm.stacks.closeEditor()
}

internal fun libraryCallbacks(vm: LauncherViewModel, actions: LauncherActions, drag: DragController): LibraryCallbacks = object : LibraryCallbacks {
    override fun onLaunch(app: AppInfo, bounds: Rect) {
        vm.library.close()
        actions.launch(app, bounds)
    }

    override fun onLongPress(app: AppInfo, bounds: Rect, pointer: Offset) {
        val entry = AppEntry(app)
        vm.openMenu(entry, bounds, inDock = false, inLibrary = true)
        if (vm.canArrange) drag.prepare(entry, DragSource.Library, bounds, pointer)
    }
}

internal fun libraryMenuActions(vm: LauncherViewModel): LibraryMenuActions = object : LibraryMenuActions {
    override fun isPinned(app: AppInfo): Boolean = vm.library.isPinned(app)
    override fun togglePin(app: AppInfo) = vm.library.togglePin(app)
    override fun chooseCategory(app: AppInfo) = vm.library.chooseCategory(app)
}

internal fun searchCallbacks(vm: LauncherViewModel, actions: LauncherActions): SearchCallbacks = object : SearchCallbacks {
    override fun onLaunch(app: AppInfo, bounds: Rect) {
        vm.closeSearch()
        actions.launch(app, bounds)
    }

    override fun onLongPress(app: AppInfo, bounds: Rect) = vm.openMenu(AppEntry(app), bounds, inDock = false)

    override fun onWebSearch(query: String) {
        vm.closeSearch()
        actions.webSearch(query)
    }

    override fun onClose() = vm.closeSearch()
}

/** [firstHomePage] is the pager index of home page 0 (the Today page comes before it). */
internal class HomeDragCallbacks(
    private val vm: LauncherViewModel,
    private val scope: CoroutineScope,
    private val pager: PagerState,
    private val firstHomePage: () -> Int,
) : DragController.Callbacks {
    override fun onDragActivated() {
        vm.enterEditMode()
    }

    override fun onDrop(id: String, position: DropPosition) = vm.drop(id, position)

    override fun onFlipPage(page: Int) {
        vm.library.close()
        scope.launch { pager.animateScrollToPage(page + firstHomePage()) }
    }

    override fun onLeaveFolder() = vm.closeFolder()
}
