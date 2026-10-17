package com.zfl.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import com.zfl.launcher.ui.icon.luma
import com.zfl.launcher.ui.icon.rgbToHsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    @Test
    fun noColorOrGreyWallpaperHasNoAccent() {
        assertNull(dynamicAccent(null))
        assertNull(dynamicAccent(0xFF808080.toInt()))
        assertNull(dynamicAccent(0xFF101010.toInt()))
    }

    @Test
    fun accentKeepsTheWallpaperHueAndStaysReadable() {
        val darkRed = 0xFF4A1010.toInt()
        val accent = dynamicAccent(darkRed)
        assertNotNull(accent)

        assertEquals(rgbToHsv(darkRed)[0], rgbToHsv(accent!!)[0], 1f)
        assertTrue(luma(accent) >= MIN_ACCENT_LUMA - 0.01f)
    }

    @Test
    fun deepBlueIsLightenedToTheMinimumLuminance() {
        val accent = dynamicAccent(0xFF0A0A60.toInt())!!
        assertTrue(luma(accent) >= MIN_ACCENT_LUMA - 0.01f)
    }

    @Test
    fun resolveFallsBackToDefaults() {
        assertEquals(AccentColors.Default, AccentColors.resolve(dynamic = false, wallpaperColor = 0xFFFF0000.toInt()))
        assertEquals(AccentColors.Default, AccentColors.resolve(dynamic = true, wallpaperColor = null))
    }

    @Test
    fun brightAccentGetsDarkText() {
        val yellow = AccentColors.resolve(dynamic = true, wallpaperColor = 0xFF806010.toInt())
        assertEquals(Color.Black, yellow.onPrimary)
        assertEquals(yellow.primary, yellow.switchOn)
    }
}
