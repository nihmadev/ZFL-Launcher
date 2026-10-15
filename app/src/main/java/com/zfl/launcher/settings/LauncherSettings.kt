package com.zfl.launcher.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** Default values, used when a key was never written. */
private val Defaults: SettingsState = SettingsState()

/**
 * Process-wide settings store backed by SharedPreferences.
 *
 * Both the home screen and the settings screen observe [state], so a change made in
 * settings is reflected on the home screen immediately.
 */
class LauncherSettings private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _state.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Applies [transform] to the current settings and saves the result (values are clamped to their ranges). */
    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value).sanitized()
        prefs.edit {
            putBoolean(KEY_HIDE_NAV, next.hideNavigation)
            putBoolean(KEY_BLUR, next.blurEffect)
            putBoolean(KEY_CUSTOM_TEXT, next.customTextSize)
            putFloat(KEY_TEXT_SCALE, next.textScale)
            putInt(KEY_GRID_ROWS, next.gridRows)
            putString(KEY_TRANSITION, next.pageTransition.name)
            putBoolean(KEY_OPEN_ANIM, next.openCloseAnimation)
            putBoolean(KEY_RESUME_ANIM, next.resumeAnimation)
            putFloat(KEY_GLASS_TINT, next.glassTint)
            putBoolean(KEY_GLASS_EDGE, next.glassEdge)
            putBoolean(KEY_HOME_BLUR, next.homeBlur)
            putFloat(KEY_HOME_BLUR_RADIUS, next.homeBlurRadius)
            putFloat(KEY_MENU_DIM, next.menuDim)
            putFloat(KEY_ICON_SCALE, next.iconScale)
            putFloat(KEY_ICON_CORNER, next.iconCorner)
            putBoolean(KEY_SHOW_LABELS, next.showLabels)
            putBoolean(KEY_SHOW_DOCK, next.showDock)
            putInt(KEY_DOCK_SLOTS, next.dockSlots)
            putBoolean(KEY_DOCK_BACKGROUND, next.dockBackground)
            putBoolean(KEY_SHOW_PILL, next.showPagePill)
            putString(KEY_SEARCH_SWIPE, next.searchSwipe.name)
            putBoolean(KEY_APP_LIBRARY, next.appLibrary)
            putStringSet(KEY_HIDDEN_MENU, next.hiddenMenuActions.map { it.name }.toSet())
        }
    }

    fun setHideNavigation(value: Boolean): Unit = update { it.copy(hideNavigation = value) }
    fun setBlurEffect(value: Boolean): Unit = update { it.copy(blurEffect = value) }
    fun setCustomTextSize(value: Boolean): Unit = update { it.copy(customTextSize = value) }
    fun setOpenCloseAnimation(value: Boolean): Unit = update { it.copy(openCloseAnimation = value) }
    fun setResumeAnimation(value: Boolean): Unit = update { it.copy(resumeAnimation = value) }
    fun setPageTransition(value: PageTransition): Unit = update { it.copy(pageTransition = value) }
    fun setTextScale(value: Float): Unit = update { it.copy(textScale = value) }
    fun setGridRows(value: Int): Unit = update { it.copy(gridRows = value) }

    fun setMenuActionEnabled(action: MenuAction, enabled: Boolean): Unit = update {
        it.copy(hiddenMenuActions = if (enabled) it.hiddenMenuActions - action else it.hiddenMenuActions + action)
    }

    fun setAppHidden(appKey: String, hidden: Boolean) {
        val next = _state.value.hiddenApps.let { if (hidden) it + appKey else it - appKey }
        prefs.edit { putStringSet(KEY_HIDDEN, next) }
    }

    /** Sets a custom label, or restores the original one when [label] is null or blank. */
    fun setCustomLabel(appKey: String, label: String?) {
        val next = _state.value.customLabels.toMutableMap()
        if (label.isNullOrBlank()) next.remove(appKey) else next[appKey] = label.trim()
        prefs.edit { putString(KEY_LABELS, JSONObject(next as Map<*, *>).toString()) }
    }

    /** Where the custom icon image of an app is stored (it may not exist). */
    fun customIconFile(appKey: String): File =
        File(appContext.filesDir, "icons/${appKey.replace(Regex("[^A-Za-z0-9._-]"), "_")}.png")

    /** Call after writing [customIconFile]; bumps the version so every icon cache reloads it. */
    fun markCustomIconChanged(appKey: String) {
        val next = _state.value.customIcons + (appKey to System.currentTimeMillis())
        prefs.edit { putString(KEY_ICONS, JSONObject(next as Map<*, *>).toString()) }
    }

    fun resetCustomIcon(appKey: String) {
        customIconFile(appKey).delete()
        val next = _state.value.customIcons - appKey
        prefs.edit { putString(KEY_ICONS, JSONObject(next as Map<*, *>).toString()) }
    }

    private fun read(): SettingsState = readRaw().sanitized()

    private fun readRaw(): SettingsState = SettingsState(
        hideNavigation = prefs.getBoolean(KEY_HIDE_NAV, false),
        blurEffect = prefs.getBoolean(KEY_BLUR, true),
        customTextSize = prefs.getBoolean(KEY_CUSTOM_TEXT, false),
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f),
        gridRows = prefs.getInt(KEY_GRID_ROWS, 6),
        pageTransition = prefs.getString(KEY_TRANSITION, null)
            ?.let { name -> PageTransition.entries.firstOrNull { it.name == name } }
            ?: PageTransition.Default,
        openCloseAnimation = prefs.getBoolean(KEY_OPEN_ANIM, true),
        resumeAnimation = prefs.getBoolean(KEY_RESUME_ANIM, true),
        glassTint = prefs.getFloat(KEY_GLASS_TINT, Defaults.glassTint),
        glassEdge = prefs.getBoolean(KEY_GLASS_EDGE, Defaults.glassEdge),
        homeBlur = prefs.getBoolean(KEY_HOME_BLUR, Defaults.homeBlur),
        homeBlurRadius = prefs.getFloat(KEY_HOME_BLUR_RADIUS, Defaults.homeBlurRadius),
        menuDim = prefs.getFloat(KEY_MENU_DIM, Defaults.menuDim),
        iconScale = prefs.getFloat(KEY_ICON_SCALE, Defaults.iconScale),
        iconCorner = prefs.getFloat(KEY_ICON_CORNER, Defaults.iconCorner),
        showLabels = prefs.getBoolean(KEY_SHOW_LABELS, Defaults.showLabels),
        showDock = prefs.getBoolean(KEY_SHOW_DOCK, Defaults.showDock),
        dockSlots = prefs.getInt(KEY_DOCK_SLOTS, Defaults.dockSlots),
        dockBackground = prefs.getBoolean(KEY_DOCK_BACKGROUND, Defaults.dockBackground),
        showPagePill = prefs.getBoolean(KEY_SHOW_PILL, Defaults.showPagePill),
        searchSwipe = SearchSwipe.fromStored(
            prefs.getString(KEY_SEARCH_SWIPE, null),
            legacyEnabled = prefs.getBoolean(KEY_SWIPE_SEARCH, true),
        ),
        appLibrary = prefs.getBoolean(KEY_APP_LIBRARY, Defaults.appLibrary),
        hiddenMenuActions = prefs.getStringSet(KEY_HIDDEN_MENU, emptySet()).orEmpty()
            .mapNotNull { name -> MenuAction.entries.firstOrNull { it.name == name } }.toSet(),
        hiddenApps = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet(),
        customLabels = readJsonMap(KEY_LABELS) { obj, key -> obj.getString(key) },
        customIcons = readJsonMap(KEY_ICONS) { obj, key -> obj.getLong(key) },
    )

    private fun <T> readJsonMap(prefKey: String, value: (JSONObject, String) -> T): Map<String, T> {
        val json = prefs.getString(prefKey, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { value(obj, it) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val KEY_HIDE_NAV = "hide_navigation"
        private const val KEY_BLUR = "blur_effect"
        private const val KEY_CUSTOM_TEXT = "custom_text_size"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_GRID_ROWS = "grid_rows"
        private const val KEY_TRANSITION = "page_transition"
        private const val KEY_OPEN_ANIM = "open_close_animation"
        private const val KEY_RESUME_ANIM = "resume_animation"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_LABELS = "custom_labels"
        private const val KEY_ICONS = "custom_icons"
        private const val KEY_GLASS_TINT = "glass_tint"
        private const val KEY_GLASS_EDGE = "glass_edge"
        private const val KEY_HOME_BLUR = "home_blur"
        private const val KEY_HOME_BLUR_RADIUS = "home_blur_radius"
        private const val KEY_MENU_DIM = "menu_dim"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_ICON_CORNER = "icon_corner"
        private const val KEY_SHOW_LABELS = "show_labels"
        private const val KEY_SHOW_DOCK = "show_dock"
        private const val KEY_DOCK_SLOTS = "dock_slots"
        private const val KEY_DOCK_BACKGROUND = "dock_background"
        private const val KEY_SHOW_PILL = "show_page_pill"
        private const val KEY_SWIPE_SEARCH = "swipe_down_search"
        private const val KEY_SEARCH_SWIPE = "search_swipe"
        private const val KEY_APP_LIBRARY = "app_library"
        private const val KEY_HIDDEN_MENU = "hidden_menu_actions"

        @Volatile
        private var instance: LauncherSettings? = null

        fun get(context: Context): LauncherSettings =
            instance ?: synchronized(this) { instance ?: LauncherSettings(context).also { instance = it } }
    }
}
