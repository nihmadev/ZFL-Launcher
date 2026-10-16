package com.zfl.launcher.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.produceState
import com.zfl.launcher.ui.widgets.WidgetProviderGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.LauncherViewModel
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeState
import com.zfl.launcher.data.LibraryCategory
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.library.CategoryOverlay
import com.zfl.launcher.ui.library.LibraryCallbacks
import com.zfl.launcher.ui.menu.ContextMenuOverlay
import com.zfl.launcher.ui.menu.WidgetMenuActions
import com.zfl.launcher.ui.search.SearchCallbacks
import com.zfl.launcher.ui.search.SearchOverlay
import com.zfl.launcher.ui.widgets.WidgetGallery
import com.zfl.launcher.ui.theme.Motion

/**
 * Everything drawn above the home screen pages: the edit-mode bar, an open folder or App
 * Library category, search, the long-press menu, the widget gallery and dialogs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeOverlays(
    vm: LauncherViewModel,
    actions: LauncherActions,
    state: HomeState,
    drag: DragController,
    library: List<LibraryCategory>,
    visibleApps: List<AppInfo>,
    folderIcons: HomeIconCallbacks,
    libraryCallbacks: LibraryCallbacks,
    searchCallbacks: SearchCallbacks,
    widgetMenu: WidgetMenuActions,
    onAddWidget: (WidgetKind, WidgetSize) -> Unit,
    onAddAppWidget: (AppWidgetProviderInfo) -> Unit,
    onAddStack: (WidgetSize) -> Unit,
) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val removeRequest by vm.removeRequest.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val openCategoryId by vm.openCategory.collectAsStateWithLifecycle()
    val gallery by vm.widgetGallery.collectAsStateWithLifecycle()
    val editingStack by vm.stacks.editing.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = editMode,
        enter = fadeIn(Motion.fade()),
        exit = fadeOut(Motion.fade()),
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(top = 4.dp),
    ) {
        EditTopBar(
            onDone = vm::exitEditMode,
            onAddWidget = vm::openWidgetGallery,
            onCustomize = actions::openSettings,
            onWallpapers = actions::openWallpaperPicker,
        )
    }

    FolderOverlay(
        open = openFolder,
        folder = openFolder?.let { state.findEntry(it.id) as? FolderEntry },
        editMode = editMode,
        drag = drag,
        iconCallbacks = folderIcons,
        onClose = vm::closeFolder,
        onRename = vm::renameFolder,
    )

    CategoryOverlay(
        category = library.firstOrNull { it.id == openCategoryId },
        callbacks = libraryCallbacks,
        onClose = { vm.openCategory.value = null },
    )

    SearchOverlay(
        mode = search,
        apps = visibleApps,
        suggestions = vm.launchStats.suggestions(visibleApps, 8),
        callbacks = searchCallbacks,
    )

    // Apps with widgets, read (off the main thread) each time the gallery opens.
    val appWidgets by produceState(emptyList<WidgetProviderGroup>(), gallery) {
        if (gallery) value = withContext(Dispatchers.IO) { vm.appWidgets.providers() }
    }
    WidgetGallery(
        visible = gallery,
        appWidgets = appWidgets,
        onAdd = onAddWidget,
        onAddApp = onAddAppWidget,
        onAddStack = onAddStack,
        onClose = vm::closeWidgetGallery,
    )

    ContextMenuOverlay(
        target = menu,
        dockIsFull = vm.dockIsFull(),
        repository = vm.repository,
        onDismiss = vm::closeMenu,
        onToggleDock = vm::toggleDock,
        onHide = vm::hideApp,
        onEditHome = vm::enterEditMode,
        onRenameFolder = { entry, bounds -> vm.openFolder(entry.id, bounds, renaming = true) },
        onAddToHome = vm::addToHome,
        widgetActions = widgetMenu,
        actions = actions,
    )

    StackEditor(
        stack = editingStack?.let(state::findStack),
        actions = remember(vm) { stackEditorActions(vm) },
    )

    removeRequest?.let { app ->
        RemoveAppDialog(
            app = app,
            onRemoveFromHome = { vm.removeFromHome(app) },
            onUninstall = {
                vm.removeRequest.value = null
                actions.uninstall(app)
            },
            onDismiss = { vm.removeRequest.value = null },
        )
    }
}
