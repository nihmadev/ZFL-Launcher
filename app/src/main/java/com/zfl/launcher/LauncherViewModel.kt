package com.zfl.launcher

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import com.zfl.launcher.data.AppWidgetItem
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.allPageWidgets
import com.zfl.launcher.data.withoutDeadAppWidgets
import com.zfl.launcher.ui.widgets.AppWidgets
import android.net.Uri
import com.zfl.launcher.data.CalendarEvent
import com.zfl.launcher.data.CalendarEvents
import com.zfl.launcher.data.WeatherRepository
import com.zfl.launcher.data.WidgetItem
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetPhotos
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.data.WidgetTarget
import com.zfl.launcher.data.addWidget
import com.zfl.launcher.data.allWidgets
import com.zfl.launcher.data.findWidget
import com.zfl.launcher.data.removeWidget
import com.zfl.launcher.data.updateWidget
import com.zfl.launcher.ui.widgets.WidgetEnvironment
import android.content.pm.ShortcutInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppItem
import com.zfl.launcher.data.AppRepository
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.HomeItem
import com.zfl.launcher.data.HomeState
import com.zfl.launcher.data.LayoutData
import com.zfl.launcher.data.LaunchStats
import com.zfl.launcher.data.LayoutRepository
import com.zfl.launcher.data.LibraryCategory
import com.zfl.launcher.data.addToHome
import com.zfl.launcher.data.buildAppLibrary
import com.zfl.launcher.data.LayoutStore
import com.zfl.launcher.data.removeFromHome
import com.zfl.launcher.data.buildHomeLayout
import com.zfl.launcher.data.move
import com.zfl.launcher.data.reconcile
import com.zfl.launcher.data.renameFolder
import com.zfl.launcher.settings.LauncherSettings
import com.zfl.launcher.ui.glass.GlassSource
import com.zfl.launcher.ui.glass.WallpaperBlur
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Long-press menu that is currently open: for which item, and where its icon is on screen. */
data class MenuTarget(
    val entry: HomeEntry,
    val iconBounds: Rect,
    val inDock: Boolean,
    val shortcuts: List<ShortcutInfo>,
    /** False for apps opened from the App Library that aren't on the Home Screen. */
    val onHome: Boolean = true,
)

/** Which search UI is open. */
enum class SearchMode {
    /** Home Screen search: suggestions and a search field above the keyboard. */
    Spotlight,
    /** The App Library search: every app in alphabetical order with a letter index. */
    Library,
}

/** A folder opened on top of the home screen; it grows out of [iconBounds]. */
data class OpenFolder(
    val id: String,
    val iconBounds: Rect,
    /** Opened via "Rename": focus the title field right away. */
    val renaming: Boolean = false,
)

/** Holds the home screen state and survives configuration changes. */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repository: AppRepository = AppRepository(app)
    val settings: LauncherSettings = LauncherSettings.get(app)
    private val layoutRepository = LayoutRepository.get(app)
    private val store: LayoutStore = layoutRepository.store

    /** The persisted arrangement; null until loaded. Always normalized for the current grid size. */
    private val layout: StateFlow<LayoutData?> = layoutRepository.layout

    val state: StateFlow<HomeState> = combine(layout.filterNotNull(), repository.apps, settings.state) { layout, apps, prefs ->
        buildHomeLayout(layout, apps, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    val launchStats: LaunchStats = LaunchStats.get(app)

    /** Visible (not hidden) apps with custom labels applied, alphabetically. Used by search. */
    val visibleApps: StateFlow<List<AppInfo>> = combine(repository.apps, settings.state) { apps, prefs ->
        apps.filter { it.key !in prefs.hiddenApps }
            .map { app -> prefs.customLabels[app.key]?.let { AppInfo(app.info, it) } ?: app }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** App Library groups, rebuilt when apps, settings or usage change. */
    val library: StateFlow<List<LibraryCategory>> = combine(visibleApps, launchStats.scores) { apps, scores ->
        buildAppLibrary(getApplication(), apps, scores)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val search: MutableStateFlow<SearchMode?> = MutableStateFlow(null)

    /** App Library category opened full screen (its id). */
    val openCategory: MutableStateFlow<String?> = MutableStateFlow(null)

    val weather: WeatherRepository = WeatherRepository.get(app)
    val appWidgets: AppWidgets = AppWidgets.get(app)

    /** Upcoming events for the "Up Next" calendar widget; refreshed by [refreshWidgets]. */
    private val calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val calendarAllowed = MutableStateFlow(CalendarEvents.hasPermission(app))

    /** Everything widgets display, in one value for the composition. */
    val widgetEnvironment: StateFlow<WidgetEnvironment> = combine(
        weather.state,
        combine(visibleApps, launchStats.scores) { apps, _ -> launchStats.suggestions(apps, SUGGESTED_APPS) },
        calendarEvents,
        calendarAllowed,
    ) { weather, suggestions, events, allowed -> WidgetEnvironment(weather, suggestions, events, allowed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WidgetEnvironment())

    /** The "Add Widget" sheet is open. */
    val widgetGallery: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** iOS "jiggle mode": icons can be dragged, removed and grouped into folders. */
    val editMode: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** The open long-press menu, or `null`. */
    val menu: MutableStateFlow<MenuTarget?> = MutableStateFlow(null)

    val openFolder: MutableStateFlow<OpenFolder?> = MutableStateFlow(null)

    /** App whose "−" badge was tapped: shows the remove dialog. */
    val removeRequest: MutableStateFlow<AppInfo?> = MutableStateFlow(null)

    /** Emits when the user comes back to the home screen from an app (for the resume animation). */
    val returnedHome: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)

    /** Emits when Home is pressed while already on the home screen: close everything, go to page 1. */
    val homePressed: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)

    /** Blurred wallpaper for glass surfaces; `null` until loaded or when not accessible. */
    val glass: MutableStateFlow<GlassSource?> = MutableStateFlow(null)
    private val _homeWallpaper = MutableStateFlow<ImageBitmap?>(null)
    /** Unsaturated wallpaper for the full backdrop blur, independent of the glass toggle. */
    val homeWallpaper: StateFlow<ImageBitmap?> = _homeWallpaper
    private var wallpaperId = Int.MIN_VALUE
    private var wallpaperJob: Job? = null

    private val pageCapacity: Int get() = Tokens.GridColumns * settings.state.value.gridRows
    private val dockCapacity: Int get() = settings.state.value.dockSlots

    init {
        viewModelScope.launch {
            layoutRepository.load()?.let { saved ->
                // Widget ids left over from an add that never finished (e.g. the app was killed).
                val used = saved.allPageWidgets().filterIsInstance<AppWidgetItem>().map { it.appWidgetId }.toSet()
                appWidgets.orphanIds(used).forEach(appWidgets::deleteId)
            }
            // Keep the layout in sync with installed apps, hidden apps and the grid size.
            combine(repository.apps, settings.state) { apps, prefs -> apps to prefs }.collect { (apps, prefs) ->
                if (apps.isEmpty()) return@collect
                val visible = apps.filter { it.key !in prefs.hiddenApps }
                val capacity = Tokens.GridColumns * prefs.gridRows
                val base = layout.value ?: store.initialLayout(visible, capacity)
                val next = base.reconcile(visible.map { it.key }, capacity, prefs.dockSlots)
                    // A widget's app was uninstalled: its widget goes too.
                    .withoutDeadAppWidgets(appWidgets::isAlive, capacity, prefs.dockSlots)
                if (next != layout.value) update(next)
            }
        }
    }

    private fun update(next: LayoutData) = layoutRepository.update(next)

    // region Drag and drop

    /**
     * Moves the item [id] (a top-level item, or an app inside a folder) to [position].
     * New folders are named after the category of the app they were created on.
     */
    fun drop(id: String, position: DropPosition) {
        val current = layout.value ?: return
        val item = findItem(current, id) ?: return
        val next = current.move(item, position, pageCapacity, dockCapacity) {
            val target = (position as? DropPosition.Merge)?.targetId
            store.folderNameFor(repository.apps.value.firstOrNull { it.key == target })
        }
        if (next != current) update(next)
    }

    private fun findItem(layout: LayoutData, id: String): HomeItem? =
        (layout.pages.flatten() + layout.dock).firstOrNull { it.id == id }
            ?: AppItem(id).takeIf { id in layout.allAppKeys() }

    // endregion

    // region Edit mode, menus, folders

    fun enterEditMode() {
        closeMenu()
        editMode.value = true
    }

    fun exitEditMode() {
        editMode.value = false
    }

    fun openMenu(entry: HomeEntry, bounds: Rect, inDock: Boolean) {
        val shortcuts = (entry as? AppEntry)?.let { repository.shortcuts(it.app) }.orEmpty()
        val onHome = entry !is AppEntry || layout.value?.allAppKeys()?.contains(entry.id) != false
        menu.value = MenuTarget(entry, bounds, inDock, shortcuts, onHome)
    }

    /** "Add to Home Screen" from the App Library: the app goes to the end of the last page. */
    fun addToHome(app: AppInfo) {
        closeMenu()
        val current = layout.value ?: return
        update(current.addToHome(app.key, pageCapacity, dockCapacity))
    }

    fun openSearch(mode: SearchMode) {
        closeMenu()
        search.value = mode
    }

    fun closeSearch() {
        search.value = null
    }

    fun closeMenu() {
        menu.value = null
    }

    fun openFolder(id: String, bounds: Rect, renaming: Boolean = false) {
        closeMenu()
        if (renaming) editMode.value = true
        openFolder.value = OpenFolder(id, bounds, renaming)
    }

    fun closeFolder() {
        openFolder.value = null
    }

    fun renameFolder(id: String, name: String) {
        val current = layout.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || current.findFolder(id)?.name == trimmed) return
        update(current.renameFolder(id, trimmed))
    }

    /** Menu action: put the item in the dock, or send it from the dock to the last page. */
    fun toggleDock(entry: HomeEntry) {
        closeMenu()
        val current = layout.value ?: return
        val inDock = current.dock.any { it.id == entry.id }
        drop(entry.id, if (inDock) DropPosition.Page(current.pages.lastIndex, Int.MAX_VALUE) else DropPosition.Dock(current.dock.size))
    }

    fun dockIsFull(): Boolean = (layout.value?.dock?.size ?: 0) >= dockCapacity

    /** "Remove from Home Screen": the app stays installed but is no longer on the Home Screen. */
    fun removeFromHome(app: AppInfo) {
        removeRequest.value = null
        val current = layout.value ?: return
        update(current.removeFromHome(app.key, pageCapacity, dockCapacity))
    }

    /** "Hide App": hidden everywhere until shown again in Settings → Hidden Apps. */
    fun hideApp(app: AppInfo) {
        closeMenu()
        removeRequest.value = null
        settings.setAppHidden(app.key, hidden = true)
    }

    // endregion

    // region Widgets

    fun openWidgetGallery() {
        closeMenu()
        widgetGallery.value = true
    }

    fun closeWidgetGallery() {
        widgetGallery.value = false
    }

    /** Adds a widget at the top of a home page or at the end of the Today page. */
    fun addWidget(kind: WidgetKind, size: WidgetSize, target: WidgetTarget) {
        closeWidgetGallery()
        val current = layout.value ?: return
        val widget = WidgetItem("widget-${System.currentTimeMillis()}", kind, size)
        update(current.addWidget(widget, target, pageCapacity, dockCapacity))
        refreshWidgets()
    }

    /** Another app's widget was bound (and configured): place it with the size it asks for. */
    fun addAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo, target: WidgetTarget) {
        closeWidgetGallery()
        val current = layout.value ?: return appWidgets.deleteId(appWidgetId)
        val widget = AppWidgetItem("appwidget-$appWidgetId", appWidgetId, info.provider.flattenToString(), appWidgets.defaultSpan(info))
        update(current.addWidget(widget, target, pageCapacity, dockCapacity))
    }

    /** New size in cells; built-in widgets only take their own sizes. */
    fun resizeWidget(id: String, span: Span) {
        closeMenu()
        val current = layout.value ?: return
        val resized = when (val widget = current.findWidget(id)) {
            is WidgetItem -> widget.kind.sizes.firstOrNull { it.span == span }?.let { widget.copy(size = it) }
            is AppWidgetItem -> widget.copy(span = span)
            null -> null
        } ?: return
        update(current.updateWidget(resized, pageCapacity, dockCapacity))
    }

    fun removeWidget(id: String) {
        closeMenu()
        val current = layout.value ?: return
        val widget = current.findWidget(id)
        update(current.removeWidget(id, pageCapacity, dockCapacity))
        when (widget) {
            is WidgetItem -> WidgetPhotos.delete(getApplication(), id)
            is AppWidgetItem -> appWidgets.deleteId(widget.appWidgetId)
            null -> Unit
        }
    }

    /** Stores the picked image for a photo widget and shows it. */
    fun setWidgetPhoto(id: String, image: Uri) {
        viewModelScope.launch {
            if (!WidgetPhotos.import(getApplication(), id, image)) return@launch
            val current = layout.value ?: return@launch
            val widget = current.findWidget(id) as? WidgetItem ?: return@launch
            update(current.updateWidget(widget.copy(photo = System.currentTimeMillis()), pageCapacity, dockCapacity))
        }
    }

    /** Refreshes the data of the widgets that are placed: weather (if stale) and calendar events. */
    fun refreshWidgets() {
        val kinds = layout.value?.allWidgets()?.map { it.kind }?.toSet().orEmpty()
        calendarAllowed.value = CalendarEvents.hasPermission(getApplication())
        viewModelScope.launch {
            if (WidgetKind.Weather in kinds) weather.refreshIfStale()
        }
        viewModelScope.launch {
            calendarEvents.value = if (WidgetKind.Calendar in kinds) CalendarEvents.upcoming(getApplication()) else emptyList()
        }
    }

    // endregion

    /** Re-blurs the wallpaper if it changed since the last call (or always when [force]). */
    fun refreshWallpaper(force: Boolean = false) {
        val id = WallpaperBlur.wallpaperId(getApplication())
        if (!force && id == wallpaperId && glass.value != null && _homeWallpaper.value != null) return
        wallpaperId = id
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            val background = WallpaperBlur.loadPreview(getApplication())
            val surfaces = WallpaperBlur.load(getApplication())
            _homeWallpaper.value = background
            glass.value = surfaces
        }
    }

    override fun onCleared() {
        repository.close()
    }

    private companion object {
        /** Apps a medium suggestions widget shows. */
        const val SUGGESTED_APPS = 8
    }
}
