package com.zfl.launcher.ui.settings

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppRepository
import com.zfl.launcher.data.HomeState
import com.zfl.launcher.data.ImportPreview
import com.zfl.launcher.data.ImportSource
import com.zfl.launcher.data.LauncherImporter
import com.zfl.launcher.data.LayoutRepository
import com.zfl.launcher.data.LayoutStore
import com.zfl.launcher.data.toLayout
import com.zfl.launcher.data.buildHomeLayout
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.data.AppSnapshot
import com.zfl.launcher.data.CategoryEdit
import com.zfl.launcher.data.GroupKind
import com.zfl.launcher.data.LaunchStats
import com.zfl.launcher.data.LibraryConfig
import com.zfl.launcher.data.LibraryIds
import com.zfl.launcher.data.LibraryTitles
import com.zfl.launcher.data.createCategory
import com.zfl.launcher.data.libraryStates
import com.zfl.launcher.data.renameCategory
import com.zfl.launcher.data.syncedWith
import com.zfl.launcher.data.visibleApps
import com.zfl.launcher.data.GridChange
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.data.LayoutData
import com.zfl.launcher.data.withGrid
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.GridMargins
import com.zfl.launcher.settings.LauncherSettings
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.GlassSource
import com.zfl.launcher.ui.glass.WallpaperBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State shared by all settings screens. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val settings: LauncherSettings = LauncherSettings.get(app)

    /** All installed apps, including hidden ones (the Hidden Apps screen needs them). */
    val repository: AppRepository = AppRepository(app)

    private val layoutStore = LayoutStore(app)

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    /** Sharp wallpaper for the live home screen preview. */
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper

    private val _glass = MutableStateFlow<GlassSource?>(null)
    /** Blurred wallpaper used as the background of sub pages. */
    val glass: StateFlow<GlassSource?> = _glass

    // Declared before init, which loads the layout.
    private val layoutRepository = LayoutRepository.get(app)

    /** The saved Home Screen layout; null until loaded or on a fresh install that never showed it. */
    val layout: StateFlow<LayoutData?> = layoutRepository.layout

    init {
        refreshGlass()
        viewModelScope.launch { layoutRepository.load() }
    }

    /** Reloads wallpaper-based images (e.g. after the user granted access). */
    fun refreshGlass() {
        viewModelScope.launch {
            // The sharp preview doubles as the rim of Liquid glass in the glass preview.
            val sharp = WallpaperBlur.loadPreview(getApplication())
            _wallpaper.value = sharp
            _glass.value = WallpaperBlur.load(getApplication(), sharp)
        }
    }

    /** The layout to show and change: the saved one, or what a fresh install would start with. */
    private fun currentLayout(apps: List<AppInfo>, prefs: SettingsState): LayoutData {
        val snapshot = repository.snapshot.value.takeIf { it.apps === apps } ?: AppSnapshot(apps, emptySet())
        return (layout.value ?: layoutStore.initialLayout(visibleApps(apps, prefs))).syncedWith(snapshot, prefs)
    }

    /** Grid of the saved layout, or the one a fresh install starts with. */
    fun savedGrid(): GridSpec = layout.value?.grid ?: layoutStore.legacyGrid()

    /** The home screen as it is, for previews. */
    fun homePreview(apps: List<AppInfo>, prefs: SettingsState): HomeState = buildHomeLayout(currentLayout(apps, prefs), apps, prefs)

    /**
     * The home screen moved to [grid], for the Screen Grid preview; nothing is saved. Null
     * when an item doesn't fit [grid] ([GridChange.Blocked]).
     */
    fun gridPreview(apps: List<AppInfo>, prefs: SettingsState, grid: GridSpec): HomeState? =
        (currentLayout(apps, prefs).withGrid(grid) as? GridChange.Applied)?.let { buildHomeLayout(it.layout, apps, prefs) }

    /**
     * "Apply" on the Screen Grid page: the grid change is one transformation and one save of
     * the layout (grid and cells in the same file), the margins one settings update.
     * Returns false and changes nothing when an item doesn't fit [grid].
     */
    suspend fun applyGrid(grid: GridSpec, margins: GridMargins, alignment: GridAlignment): Boolean {
        val prefs = settings.state.value
        val change = layoutRepository.changeGrid(grid) { currentLayout(repository.apps.value, prefs) }
        if (change is GridChange.Blocked) return false
        if (margins != prefs.gridMargins || alignment != prefs.gridAlignment) {
            settings.update { it.copy(gridMargins = margins, gridAlignment = alignment) }
        }
        return true
    }

    // region App Library

    private val launchStats = LaunchStats.get(app)

    /** Visible apps with custom labels, for the category and pin editors. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(repository.apps, settings.state.map { it.hiddenApps to it.customLabels }.distinctUntilChanged()) { apps, _ ->
            visibleApps(apps, settings.state.value)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Group names in the UI language; set by the settings activity's composition. */
    val libraryTitles: MutableStateFlow<LibraryTitles?> = MutableStateFlow(null)

    /** The App Library as the launcher shows it, built by the same code. */
    val library: StateFlow<AppLibraryState?> =
        libraryStates(visibleApps, settings.state, launchStats.scores, libraryTitles).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One App Library change, one save; see [LauncherSettings.updateLibrary]. */
    fun editLibrary(transform: (LibraryConfig) -> LibraryConfig) {
        val apps = repository.snapshot.value
        settings.updateLibrary(apps.apps.mapTo(HashSet()) { it.key }, apps.unavailableUsers, transform)
    }

    /** Names a user category may not take: the system groups shown now. */
    private fun reservedNames(): List<String> =
        library.value?.allGroups.orEmpty().filter { it.kind == GroupKind.System }.map { it.title }

    /** Creates an empty category; saved only when the name is accepted. */
    fun createCategory(name: String): CategoryEdit {
        val config = settings.state.value.library
        val id = LibraryIds.newUser(System.currentTimeMillis(), config.categories.map { it.id })
        return config.createCategory(name, id, reservedNames()).also { edit ->
            if (edit is CategoryEdit.Done) editLibrary { edit.config }
        }
    }

    fun renameCategory(id: String, name: String): CategoryEdit =
        settings.state.value.library.renameCategory(id, name, reservedNames()).also { edit ->
            if (edit is CategoryEdit.Done) editLibrary { edit.config }
        }

    // endregion

    // region Layout import

    private val importer = LauncherImporter(app)

    /** Launchers whose layout can be imported (a package query, so off the main thread). */
    suspend fun importSources(): List<ImportSource> = withContext(Dispatchers.IO) { importer.findSources() }

    /**
     * Reads another launcher's layout for review. Fails with a [SecurityException] when
     * the launcher protects its layout database.
     */
    suspend fun previewImport(source: ImportSource): Result<ImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = settings.state.value
            val visible = repository.apps.value.filter { it.key !in prefs.hiddenApps }
            val grid = layoutRepository.load()?.grid ?: layoutStore.legacyGrid()
            importer.read(source, visible, grid, prefs.dockSlots)
        }
    }

    /**
     * Replaces the Home Screen with the imported layout; the home screen updates right away.
     * A locked layout is never replaced: the user unlocks it first. Returns whether it was applied.
     */
    fun applyImport(preview: ImportPreview, removeOtherApps: Boolean): Boolean {
        if (settings.state.value.layoutLocked) return false
        layoutRepository.update(preview.toLayout(removeOtherApps))
        return true
    }

    // endregion

    override fun onCleared() {
        repository.close()
    }
}

/** Things only the hosting Activity can do. */
interface SettingsActions {
    /** Whether ZFL is currently the default home app. */
    val isDefaultHome: Boolean
    val versionName: String
    /** Whether the wallpaper can be read (needed for the glass effect). */
    val hasWallpaperAccess: Boolean
    /** Whether Android lets ZFL ask to become the home app. */
    val isHomeRoleAvailable: Boolean

    fun requestDefaultHome()
    fun requestWallpaperAccess()
    fun requestIslandWindowAccess(): Unit
    /** App info, where Android lifts "restricted settings" for apps installed from a file. */
    fun openIslandAppInfo(): Unit
    fun requestIslandMusicAccess(): Unit
    fun reconnectIsland(): Unit
    /** Switches ZFL's UI language (`null` = follow the system); recreates the activity. */
    fun setAppLanguage(tag: String?)
    /** "Get Started": marks the onboarding as done and goes home. */
    fun completeOnboarding()
}
