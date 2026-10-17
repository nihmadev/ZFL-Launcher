package com.zfl.launcher.settings

import com.zfl.launcher.data.LibraryConfig
import com.zfl.launcher.data.NewAppPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Apps that aren't on the Home Screen must stay reachable through search or the App Library. */
class AppAccessTest {
    @Test
    fun libraryCanBeTurnedOffWhileSearchIsReachable() {
        assertEquals(false, SettingsState(appLibrary = false).sanitized().appLibrary)
        assertEquals(false, SettingsState(appLibrary = false, showPagePill = false).sanitized().appLibrary)
        assertEquals(false, SettingsState(appLibrary = false, homeBehavior = HomeBehavior(swipeDown = HomeAction.None)).sanitized().appLibrary)
    }

    @Test
    fun disablingLibraryPreservesItsConfigurationWithoutEnablingThePage() {
        val locked = SettingsState(
            appLibrary = false,
            showPagePill = false,
            homeBehavior = HomeBehavior(swipeDown = HomeAction.None),
            library = LibraryConfig(newApps = NewAppPlacement.LibraryOnly),
        ).sanitized()
        assertEquals(false, locked.appLibrary)
        // Its configuration is kept as it was.
        assertEquals(NewAppPlacement.LibraryOnly, locked.library.newApps)
    }
}
