package com.zfl.launcher.settings

import org.junit.Assert.*
import org.junit.Test

class HomeBehaviorTest {
    @Test fun allLegacyCombinationsAndNewKeyPriority() {
        for (legacy in listOf(false, true)) {
            for (name in listOf(null, "unknown") + SearchSwipe.entries.map { it.name }) {
                val old = SearchSwipe.fromStored(name, legacy)
                for (up in listOf(false, true)) {
                    val expected = if ((up && old == SearchSwipe.Up) || (!up && old == SearchSwipe.Down)) HomeAction.Search else HomeAction.None
                    assertEquals(expected, migratedSwipe(null, false, old, up))
                    assertEquals(HomeAction.Settings, migratedSwipe("settings", true, old, up))
                    assertEquals(HomeAction.None, migratedSwipe("future", true, old, up))
                    assertEquals(HomeAction.None, migratedSwipe(null, true, old, up))
                }
            }
        }
    }

    @Test fun codecRoundTripsExactComponentAndProfile() {
        val app = HomeAction.OpenApp(AppTarget("org.example/org.example.Entry\$Alias", "10"))
        for (action in listOf(HomeAction.None, HomeAction.Search, HomeAction.Settings, HomeAction.AppLibrary, HomeAction.MainPage, app)) {
            assertEquals(action, HomeActionCodec.decode(HomeActionCodec.encode(action)))
        }
        for (value in listOf(null, "future", "app1:garbage:!", "app1:", "intent:#Intent;end", "search:future")) {
            assertEquals(HomeAction.None, HomeActionCodec.decode(value))
        }
    }

    @Test fun missingTargetAndUnavailableProfileNeverResolveToAnotherApp() {
        val target = AppTarget("a/.Entry", "10")
        val apps = listOf("a/.Entry#0", "b/.Entry#10")
        assertNull(resolveHomeTarget(target, apps, emptySet()) { it })
        assertNull(resolveHomeTarget(target, apps + target.key, setOf("10")) { it })
        assertEquals(target.key, resolveHomeTarget(target, apps + target.key, emptySet()) { it })
    }

    @Test fun defaultsAndValidation() {
        val defaults = HomeBehavior()
        assertEquals(HomeAction.Search, defaults.swipeDown)
        assertEquals(HomeAction.None, defaults.swipeUp)
        assertEquals(HomeAction.None, defaults.emptyDoubleTap)
        assertEquals(HomeAction.MainPage, defaults.repeatHome)
        assertEquals(HomeReturn.Stay, defaults.returnPolicy)
        assertTrue(defaults.showToday && defaults.haptics)
        assertEquals(0, defaults.copy(mainPage = -9).sanitized().mainPage)
        assertEquals(HomeAction.None, defaults.copy(swipeUp = HomeAction.MainPage).sanitized().swipeUp)
        assertFalse(SettingsState(appLibrary = false, homeBehavior = defaults.copy(swipeDown = HomeAction.None), showPagePill = false).sanitized().appLibrary)
    }
}
