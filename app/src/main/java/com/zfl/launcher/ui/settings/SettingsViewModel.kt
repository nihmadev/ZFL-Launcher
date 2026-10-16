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
import com.zfl.launcher.data.reconcile
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.settings.LauncherSettings
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.GlassSource
import com.zfl.launcher.ui.glass.WallpaperBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        refreshGlass()
    }

    /** Reloads wallpaper-based images (e.g. after the user granted access). */
    fun refreshGlass() {
        viewModelScope.launch { _glass.value = WallpaperBlur.load(getApplication()) }
        viewModelScope.launch { _wallpaper.value = WallpaperBlur.loadPreview(getApplication()) }
    }

    /** The home screen as it would look with [rows] grid rows, for the Screen Grid preview. */
    fun homePreview(apps: List<AppInfo>, prefs: SettingsState, rows: Int): HomeState {
        val visible = apps.filter { it.key !in prefs.hiddenApps }
        val capacity = Tokens.GridColumns * rows
        val layout = (layoutRepository.layout.value ?: layoutStore.load() ?: layoutStore.initialLayout(visible, capacity))
            .reconcile(visible.map { it.key }, capacity, prefs.dockSlots)
        return buildHomeLayout(layout, apps, prefs, rows)
    }

    // region Layout import

    private val importer = LauncherImporter(app)
    private val layoutRepository = LayoutRepository.get(app)

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
            importer.read(source, visible, Tokens.GridColumns * prefs.gridRows, prefs.dockSlots)
        }
    }

    /** Replaces the Home Screen with the imported layout; the home screen updates right away. */
    fun applyImport(preview: ImportPreview, removeOtherApps: Boolean) {
        layoutRepository.update(preview.toLayout(removeOtherApps))
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
    fun openLanguageSettings()
    fun requestWallpaperAccess()
    /** Switches ZFL's UI language (`null` = follow the system); recreates the activity. */
    fun setAppLanguage(tag: String?)
    /** "Get Started": marks the onboarding as done and goes home. */
    fun completeOnboarding()
}
