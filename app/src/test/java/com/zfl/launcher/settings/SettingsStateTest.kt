package com.zfl.launcher.settings

import com.zfl.launcher.ui.theme.Tokens
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStateTest {
    @Test
    fun sanitizedClampsOutOfRangeValues() {
        val state = SettingsState(
            textScale = 9f,
            glassTint = -1f,
            homeBlurRadius = 500f,
            menuDim = 2f,
            iconScale = 0.1f,
            iconCorner = 3f,
        ).sanitized()
        assertEquals(SettingsState.MAX_TEXT_SCALE, state.textScale)
        assertEquals(0f, state.glassTint)
        assertEquals(SettingsState.MAX_HOME_BLUR, state.homeBlurRadius)
        assertEquals(SettingsState.MAX_MENU_DIM, state.menuDim)
        assertEquals(SettingsState.MIN_ICON_SCALE, state.iconScale)
        assertEquals(SettingsState.MAX_ICON_CORNER, state.iconCorner)
    }

    @Test
    fun sanitizedClampsGlassRefraction() {
        assertEquals(1f, SettingsState(glassRefraction = 4f).sanitized().glassRefraction)
        assertEquals(0f, SettingsState(glassRefraction = -1f).sanitized().glassRefraction)
        assertEquals(SettingsState.DEFAULT_GLASS_REFRACTION, SettingsState(glassRefraction = Float.NaN).sanitized().glassRefraction)
    }

    @Test
    fun defaultsAreAlreadySanitized() {
        assertEquals(SettingsState(), SettingsState().sanitized())
    }

    @Test
    fun bottomBarShrinksWithHiddenDockAndPill() {
        val full = SettingsState()
        assertEquals(
            Tokens.PillHeight + SettingsState.PILL_MARGIN * 2 + Tokens.DockHeight + Tokens.DockBottomMargin,
            full.bottomBarHeight(0.dp),
        )
        assertEquals(0f, full.copy(showDock = false, showPagePill = false).bottomBarHeight(0.dp).value)
    }

    @Test
    fun dockGrowsWithIcons() {
        val big = SettingsState(iconScale = 1.2f)
        assertEquals((Tokens.DockHeight + Tokens.IconSize * 0.2f).value, big.dockHeight(0.dp).value, 0.01f)
    }
}
