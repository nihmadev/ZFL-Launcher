package com.zfl.launcher.ui.menu

import com.zfl.launcher.settings.MenuAction

/**
 * Items of the long-press menu, listed from the icon outwards ("Remove App" always ends up
 * farthest from the icon), without the ones the user turned off in [hidden].
 *
 * @param onHome the app is already on the Home Screen (or in the dock), as opposed to the App Library.
 * @param locked the layout is locked: items that would rearrange the Home Screen are left out.
 * @param inLibrary opened in the App Library: pinning and the category are offered there.
 *   They change only the App Library, so a locked layout keeps them.
 */
fun menuActionsFor(
    isFolder: Boolean,
    onHome: Boolean,
    hidden: Set<MenuAction>,
    locked: Boolean = false,
    inLibrary: Boolean = false,
): List<MenuAction> {
    val all = if (isFolder) {
        listOf(MenuAction.RenameFolder, MenuAction.Dock, MenuAction.EditHome)
    } else {
        listOfNotNull(
            MenuAction.Shortcuts,
            MenuAction.AddToHome.takeIf { !onHome },
            MenuAction.LibraryPin.takeIf { inLibrary },
            MenuAction.LibraryCategory.takeIf { inLibrary },
            MenuAction.AppInfo,
            MenuAction.Dock.takeIf { onHome },
            MenuAction.HideApp,
            MenuAction.EditHome,
            MenuAction.RemoveApp,
        )
    }
    return all.filter { it !in hidden && !(locked && it in ARRANGING_ACTIONS) }
}

/** Menu items that change the Home Screen layout, unavailable while it is locked. */
val ARRANGING_ACTIONS: Set<MenuAction> = setOf(
    MenuAction.RenameFolder, MenuAction.AddToHome, MenuAction.Dock, MenuAction.HideApp, MenuAction.EditHome,
)
