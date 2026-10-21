package com.zfl.launcher

import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.data.AppSnapshot
import com.zfl.launcher.data.LibraryConfig
import com.zfl.launcher.data.LibraryTitles
import com.zfl.launcher.data.LibraryView
import com.zfl.launcher.data.assign
import com.zfl.launcher.data.libraryStates
import com.zfl.launcher.data.pin
import com.zfl.launcher.data.placeInGrid
import com.zfl.launcher.data.placeInGroup
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.LibraryIds
import com.zfl.launcher.data.unpin
import com.zfl.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * App Library state and actions of the home screen. The library itself is computed
 * ([libraryStates]); every action is one pure [LibraryConfig] operation saved through
 * [LauncherSettings.updateLibrary], so one write and one emission.
 */
class LibraryActions internal constructor(
    scope: CoroutineScope,
    private val settings: LauncherSettings,
    private val snapshot: StateFlow<AppSnapshot>,
    visibleApps: StateFlow<List<AppInfo>>,
    scores: StateFlow<Map<String, Double>>,
    private val closeMenu: () -> Unit,
) {
    /** Group names in the UI language; set by the home screen, null until then. */
    val titles: MutableStateFlow<LibraryTitles?> = MutableStateFlow(null)

    /** The App Library; null until the first build. */
    val state: StateFlow<AppLibraryState?> =
        libraryStates(visibleApps, settings.state, scores, titles).stateIn(scope, SharingStarted.Eagerly, null)

    /** Group opened full screen (its id), or null. */
    val openGroup: MutableStateFlow<String?> = MutableStateFlow(null)

    /** App whose category is being chosen ("Category" in the long-press menu), or null. */
    val choosingCategory: MutableStateFlow<AppInfo?> = MutableStateFlow(null)

    fun open(groupId: String) {
        openGroup.value = groupId
    }

    fun close() {
        openGroup.value = null
    }

    fun isPinned(app: AppInfo): Boolean = app.key in settings.state.value.library.pinned

    fun togglePin(app: AppInfo) {
        closeMenu()
        edit { if (app.key in it.pinned) it.unpin(app.key) else it.pin(app.key) }
    }

    fun chooseCategory(app: AppInfo) {
        closeMenu()
        choosingCategory.value = app
    }

    /** [groupId] null puts the app back in its automatic category. */
    fun assign(app: AppInfo, groupId: String?) {
        choosingCategory.value = null
        edit { it.assign(app.key, groupId) }
    }

    fun closeCategoryChooser() {
        choosingCategory.value = null
    }

    /** Switches between Categories, Grid and List (the button next to the search pill). */
    fun setView(view: LibraryView) {
        edit { it.copy(view = view) }
    }

    /** Commits the same neighbor that the grid's insertion marker previewed. */
    fun place(appKey: String, position: DropPosition.Library): Boolean {
        val library = state.value ?: return false
        if (library.alphabetical.none { it.key == appKey }) return false
        val group = position.groupId?.let { id -> library.cards.firstOrNull { it.id == id } ?: return false }
        if (group != null && group.id != LibraryIds.PINNED && library.summary(group.id) == null) return false
        val shown = (group?.apps ?: library.gridApps).map { it.key }
        val beforeKey = position.beforeKey
        if (beforeKey == appKey || (beforeKey != null && beforeKey !in shown)) return false
        edit {
            if (group == null) it.placeInGrid(appKey, beforeKey, shown)
            else it.placeInGroup(appKey, group.id, beforeKey, shown)
                .copy(groupOrder = library.allGroups.map { summary -> summary.id })
        }
        return true
    }

    private fun edit(transform: (LibraryConfig) -> LibraryConfig) {
        val apps = snapshot.value
        settings.updateLibrary(apps.apps.mapTo(HashSet()) { it.key }, apps.unavailableUsers, transform)
    }
}
