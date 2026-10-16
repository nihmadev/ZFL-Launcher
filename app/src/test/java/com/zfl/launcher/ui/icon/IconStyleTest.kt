package com.zfl.launcher.ui.icon

import com.zfl.launcher.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconStyleTest {
    private val wallpaper = WallpaperTint(0xFF203060.toInt())

    private fun spec(state: SettingsState, night: Boolean = false, tint: WallpaperTint? = wallpaper): IconSpec =
        state.iconSpec(BASE, night, tint)!!

    @Test
    fun autoFollowsNightModeOthersIgnoreIt() {
        assertEquals(IconAppearance.Default, IconAppearance.Auto.effective(nightMode = false))
        assertEquals(IconAppearance.Dark, IconAppearance.Auto.effective(nightMode = true))
        for (appearance in listOf(IconAppearance.Default, IconAppearance.Dark, IconAppearance.Tinted)) {
            assertEquals(appearance, appearance.effective(nightMode = false))
            assertEquals(appearance, appearance.effective(nightMode = true))
        }
    }

    @Test
    fun storedAppearanceFallsBackToDefault() {
        for (appearance in IconAppearance.entries) assertEquals(appearance, IconAppearance.fromStored(appearance.name))
        assertEquals(IconAppearance.Default, IconAppearance.fromStored(null))
        assertEquals(IconAppearance.Default, IconAppearance.fromStored("Sepia"))
        assertEquals(IconAppearance.Default, SettingsState().iconAppearance)
        assertTrue(SettingsState().iconEdge)
    }

    @Test
    fun autoReusesTheBitmapsOfTheModeItResolvesTo() {
        val auto = SettingsState(iconAppearance = IconAppearance.Auto)
        assertEquals(spec(SettingsState()), spec(auto, night = false))
        assertEquals(spec(SettingsState(iconAppearance = IconAppearance.Dark)), spec(auto, night = true))
    }

    @Test
    fun nightModeAndWallpaperOnlyMatterWhenTheyAreUsed() {
        val dark = SettingsState(iconAppearance = IconAppearance.Dark)
        assertEquals(spec(dark, tint = null), spec(dark, tint = WallpaperTint(0xFFFF0000.toInt())))
        assertEquals(spec(SettingsState(), night = true), spec(SettingsState(), night = false))

        val picked = SettingsState(iconAppearance = IconAppearance.Tinted, iconTintFromWallpaper = false)
        assertEquals(spec(picked, tint = null), spec(picked, tint = WallpaperTint(0xFFFF0000.toInt())))

        val fromWallpaper = SettingsState(iconAppearance = IconAppearance.Tinted)
        assertNotEquals(spec(fromWallpaper, tint = WallpaperTint(0xFF00FF00.toInt())), spec(fromWallpaper, tint = WallpaperTint(0xFFFF0000.toInt())))
    }

    @Test
    fun tintFromWallpaperWaitsForTheColorsAndFallsBackWithoutThem() {
        val tinted = SettingsState(iconAppearance = IconAppearance.Tinted)
        assertNull(tinted.iconSpec(BASE, nightMode = false, wallpaper = null))
        val style = spec(tinted, tint = WallpaperTint(null)).style
        assertEquals(IconStyle.Tinted(tintColor(DEFAULT_ICON_TINT, tinted.iconTintSaturation)), style)
    }

    @Test
    fun cacheKeyChangesWithEverythingBakedIntoTheBitmap() {
        val base = spec(SettingsState())
        val key = iconCacheKey(APP, 0, null, base)
        assertNotEquals(key, iconCacheKey(APP, 1, null, base))
        assertNotEquals(key, iconCacheKey(APP, 0, 42L, base))
        assertNotEquals(iconCacheKey(APP, 0, 42L, base), iconCacheKey(APP, 0, 43L, base))
        assertNotEquals(key, iconCacheKey(APP, 0, null, spec(SettingsState(iconScale = 1.2f))))
        assertNotEquals(key, iconCacheKey(APP, 0, null, spec(SettingsState(iconCorner = 0.3f))))
        assertNotEquals(key, iconCacheKey(APP, 0, null, spec(SettingsState(iconAppearance = IconAppearance.Dark))))
        assertNotEquals(key, iconCacheKey(APP, 0, null, spec(SettingsState(iconEdge = false))))
        val tinted = SettingsState(iconAppearance = IconAppearance.Tinted, iconTintFromWallpaper = false)
        assertNotEquals(
            iconCacheKey(APP, 0, null, spec(tinted.copy(iconTintColor = ICON_TINT_PALETTE[0]))),
            iconCacheKey(APP, 0, null, spec(tinted.copy(iconTintColor = ICON_TINT_PALETTE[5]))),
        )
        assertNotEquals(key, iconCacheKey("other#0", 0, null, base))
    }

    @Test
    fun customIconsCanOptOutOfRestyling() {
        val dark = SettingsState(iconAppearance = IconAppearance.Dark)
        val optOut = dark.copy(tintCustomIcons = false)
        assertEquals(iconCacheKey(APP, 0, 7L, spec(SettingsState())), iconCacheKey(APP, 0, 7L, spec(optOut)))
        assertNotEquals(iconCacheKey(APP, 0, 7L, spec(SettingsState())), iconCacheKey(APP, 0, 7L, spec(dark)))
        // App icons are restyled either way.
        assertNotEquals(iconCacheKey(APP, 0, null, spec(SettingsState())), iconCacheKey(APP, 0, null, spec(optOut)))
    }

    @Test
    fun keysOfASpecMatchItsSuffixesOnly() {
        val tinted = spec(SettingsState(iconAppearance = IconAppearance.Tinted, tintCustomIcons = false))
        val keys = listOf(iconCacheKey(APP, 3, null, tinted), iconCacheKey(APP, 3, 9L, tinted))
        for (key in keys) assertTrue(tinted.keySuffixes().any(key::endsWith))
        val old = iconCacheKey(APP, 3, null, spec(SettingsState(iconAppearance = IconAppearance.Dark)))
        assertTrue(tinted.keySuffixes().none(old::endsWith))
    }

    @Test
    fun sanitizedKeepsTintValuesUsable() {
        val state = SettingsState(iconTintColor = 0x00336699, iconTintSaturation = 3f).sanitized()
        assertEquals(0xFF336699.toInt(), state.iconTintColor)
        assertEquals(1f, state.iconTintSaturation)
        assertEquals(0f, SettingsState(iconTintSaturation = -1f).sanitized().iconTintSaturation)
        assertEquals(SettingsState.DEFAULT_TINT_SATURATION, SettingsState(iconTintSaturation = Float.NaN).sanitized().iconTintSaturation)
        assertEquals(SettingsState(), SettingsState().sanitized())
    }

    @Test
    fun tintKeepsTheHueAndStaysReadable() {
        for (color in ICON_TINT_PALETTE + 0xFF0000FF.toInt() + 0xFF101830.toInt()) {
            for (saturation in listOf(0f, 0.5f, 1f)) {
                val tint = tintColor(color, saturation)
                assertEquals(255, alpha(tint))
                assertTrue("luma of ${Integer.toHexString(tint)}", luma(tint) >= MIN_TINT_LUMA - 0.01f)
                if (saturation > 0f) assertEquals(rgbToHsv(color)[0], rgbToHsv(tint)[0], 3f)
            }
        }
        assertEquals(WHITE, tintColor(ICON_TINT_PALETTE[3], 0f))
        // A gray wallpaper has no hue to take: the tint is neutral.
        assertEquals(WHITE, tintColor(0xFF6E6E70.toInt(), 1f))
    }

    @Test
    fun hsvRoundTrip() {
        for (color in ICON_TINT_PALETTE + listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF808080.toInt())) {
            val (h, s, v) = rgbToHsv(color).toList()
            val back = hsvToRgb(h, s, v)
            for (shift in listOf(16, 8, 0)) {
                assertEquals((color shr shift) and 0xFF, (back shr shift) and 0xFF, 1)
            }
        }
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("expected $expected ± $delta, was $actual", kotlin.math.abs(expected - actual) <= delta)

    private companion object {
        const val BASE = 164
        const val APP = "com.example/.Main#0"
    }
}
