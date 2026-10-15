package com.zfl.launcher.ui.menu

import com.zfl.launcher.settings.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuActionsTest {
    @Test
    fun appOnHomeShowsDockToggleButNotAddToHome() {
        val actions = menuActionsFor(isFolder = false, onHome = true, hidden = emptySet())
        assertEquals(
            listOf(
                MenuAction.Shortcuts, MenuAction.AppInfo, MenuAction.Dock,
                MenuAction.HideApp, MenuAction.EditHome, MenuAction.RemoveApp,
            ),
            actions,
        )
    }

    @Test
    fun libraryAppOffersAddToHomeInsteadOfDock() {
        val actions = menuActionsFor(isFolder = false, onHome = false, hidden = emptySet())
        assertEquals(true, MenuAction.AddToHome in actions)
        assertEquals(false, MenuAction.Dock in actions)
    }

    @Test
    fun hiddenActionsAreRemovedAndOrderKept() {
        val hidden = setOf(MenuAction.HideApp, MenuAction.RemoveApp, MenuAction.Shortcuts)
        assertEquals(
            listOf(MenuAction.AppInfo, MenuAction.Dock, MenuAction.EditHome),
            menuActionsFor(isFolder = false, onHome = true, hidden = hidden),
        )
    }

    @Test
    fun folderMenuOnlyHasFolderActions() {
        assertEquals(
            listOf(MenuAction.RenameFolder, MenuAction.EditHome),
            menuActionsFor(isFolder = true, onHome = true, hidden = setOf(MenuAction.Dock, MenuAction.AppInfo)),
        )
    }
}
