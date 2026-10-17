package com.zfl.launcher.ui.menu

import android.content.pm.ShortcutInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.zfl.launcher.MenuTarget
import com.zfl.launcher.R
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.settings.MenuAction
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.home.LauncherActions

/*
 * The rows of the long-press menu for an app, a folder or a widget; ContextMenuOverlay draws them.
 */

/** App Library actions of the menu: pinning and the category of an app. */
interface LibraryMenuActions {
    fun isPinned(app: AppInfo): Boolean
    fun togglePin(app: AppInfo)
    fun chooseCategory(app: AppInfo)
}

/** What is drawn on the left side of a menu row. */
internal sealed interface MenuIcon {
    data class Vector(val image: ImageVector) : MenuIcon
    data class Shortcut(val shortcut: ShortcutInfo) : MenuIcon
    data object MinusCircle : MenuIcon
}

/** One row of the menu. */
internal data class MenuItem(
    val label: String,
    val icon: MenuIcon,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun buildEntries(
    target: MenuTarget,
    settings: SettingsState,
    dockIsFull: Boolean,
    onDismiss: () -> Unit,
    onToggleDock: (HomeEntry) -> Unit,
    onHide: (AppInfo) -> Unit,
    onEditHome: () -> Unit,
    onRenameFolder: (FolderEntry, Rect) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    library: LibraryMenuActions,
    actions: LauncherActions,
): List<MenuItem> {
    val entry = target.entry
    val folder = entry as? FolderEntry
    val app = (entry as? AppEntry)?.app
    if (folder == null && app == null) return emptyList()
    return menuActionsFor(folder != null, target.onHome, settings.hiddenMenuActions, settings.layoutLocked, target.inLibrary).flatMap { action ->
        when (action) {
            MenuAction.Shortcuts -> target.shortcuts.map { shortcut ->
                MenuItem(
                    label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
                    icon = MenuIcon.Shortcut(shortcut),
                    onClick = { onDismiss(); actions.launchShortcut(shortcut, target.iconBounds) },
                )
            }
            MenuAction.RenameFolder -> listOf(
                MenuItem(stringResource(R.string.menu_rename_folder), MenuIcon.Vector(Icons.Outlined.Edit)) {
                    onRenameFolder(folder!!, target.iconBounds)
                },
            )
            MenuAction.AddToHome -> listOf(
                MenuItem(stringResource(R.string.menu_add_to_home), MenuIcon.Vector(Icons.Outlined.AddBox)) { onAddToHome(app!!) },
            )
            MenuAction.LibraryPin -> listOf(
                if (library.isPinned(app!!)) {
                    MenuItem(stringResource(R.string.menu_library_unpin), MenuIcon.Vector(Icons.Outlined.PushPin)) { library.togglePin(app) }
                } else {
                    MenuItem(stringResource(R.string.menu_library_pin), MenuIcon.Vector(Icons.Outlined.PushPin)) { library.togglePin(app) }
                },
            )
            MenuAction.LibraryCategory -> listOf(
                MenuItem(stringResource(R.string.menu_library_category), MenuIcon.Vector(Icons.Outlined.Folder)) { library.chooseCategory(app!!) },
            )
            MenuAction.AppInfo -> listOf(
                MenuItem(stringResource(R.string.menu_app_info), MenuIcon.Vector(Icons.Outlined.Info)) {
                    onDismiss(); actions.openAppInfo(app!!)
                },
            )
            MenuAction.Dock -> listOf(
                when {
                    target.inDock -> MenuItem(stringResource(R.string.menu_remove_from_dock), MenuIcon.Vector(Icons.Outlined.Close)) {
                        onToggleDock(entry)
                    }
                    dockIsFull -> MenuItem(stringResource(R.string.menu_dock_full), MenuIcon.Vector(Icons.Outlined.Star), enabled = false) {}
                    else -> MenuItem(stringResource(R.string.menu_add_to_dock), MenuIcon.Vector(Icons.Outlined.Star)) {
                        onToggleDock(entry)
                    }
                },
            )
            MenuAction.HideApp -> listOf(
                MenuItem(stringResource(R.string.menu_hide_app), MenuIcon.Vector(Icons.Outlined.VisibilityOff)) { onHide(app!!) },
            )
            MenuAction.EditHome -> listOf(
                MenuItem(stringResource(R.string.menu_edit_home), MenuIcon.Vector(Icons.Outlined.Apps)) { onEditHome() },
            )
            MenuAction.RemoveApp -> listOf(
                MenuItem(stringResource(R.string.menu_remove_app), MenuIcon.MinusCircle, destructive = true) {
                    onDismiss(); actions.uninstall(app!!)
                },
            )
        }
    }
}

@Composable
internal fun widgetEntries(widget: PageWidgetEntry, hidden: Set<MenuAction>, locked: Boolean, onEditHome: () -> Unit, actions: WidgetMenuActions): List<MenuItem> {
    val stack = widget as? WidgetStackEntry
    // In a stack, the photo choice applies to the widget being shown.
    val shown = stack?.currentItem ?: widget
    return listOfNotNull(
        // A locked layout keeps only what changes no arrangement: the photo of a photo widget.
        MenuItem(stringResource(R.string.stack_edit), MenuIcon.Vector(Icons.Outlined.Layers)) { actions.editStack(widget.id) }
            .takeIf { stack != null && !locked },
        MenuItem(stringResource(R.string.widget_choose_photo), MenuIcon.Vector(Icons.Outlined.Image)) {
            actions.pickPhoto(shown.id)
        }.takeIf { shown is WidgetEntry && shown.kind == WidgetKind.Photo },
        MenuItem(stringResource(R.string.menu_edit_home), MenuIcon.Vector(Icons.Outlined.Apps)) { onEditHome() }
            .takeIf { MenuAction.EditHome !in hidden && !locked },
        MenuItem(stringResource(if (stack != null) R.string.stack_remove else R.string.widget_remove), MenuIcon.MinusCircle, destructive = true) {
            actions.remove(widget.id)
        }.takeIf { !locked },
    )
}
