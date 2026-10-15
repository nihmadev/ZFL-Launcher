package com.zfl.launcher.ui.menu

import com.zfl.launcher.settings.MenuAction

/**
 * Items of the long-press menu, listed from the icon outwards ("Remove App" always ends up
 * farthest from the icon), without the ones the user turned off in [hidden].
 *
 * @param onHome the app is already on the Home Screen (or in the dock), as opposed to the App Library.
 */
fun menuActionsFor(isFolder: Boolean, onHome: Boolean, hidden: Set<MenuAction>): List<MenuAction> {
    val all = if (isFolder) {
        listOf(MenuAction.RenameFolder, MenuAction.Dock, MenuAction.EditHome)
    } else {
        listOfNotNull(
            MenuAction.Shortcuts,
            MenuAction.AddToHome.takeIf { !onHome },
            MenuAction.AppInfo,
            MenuAction.Dock.takeIf { onHome },
            MenuAction.HideApp,
            MenuAction.EditHome,
            MenuAction.RemoveApp,
        )
    }
    return all.filter { it !in hidden }
}
