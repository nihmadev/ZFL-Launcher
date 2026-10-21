package com.zfl.launcher.settings

import android.content.SharedPreferences
import com.zfl.launcher.ui.theme.Tokens
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FolderPreferencesTest {
    @Test
    fun upgradesPreserveTheCurrentAppearanceAndExistingIconOverrides() {
        val legacy = mapOf("area_folder_icon_scale" to 1.2f, "glass_tint" to 0.3f, "unrelated" to true)
        assertEquals(FolderPreferences(), FolderPreferences.fromStored(legacy))
        assertEquals(FolderPreferences(), FolderPreferences.fromStored(emptyMap<String, Any>()))
        assertFalse(FolderPreferences().customGlass)
    }

    @Test
    fun everyAppearanceValueSurvivesTheActualPreferenceWriter() {
        val value = FolderPreferences(
            width = 390f, height = 420f, radius = 12f, dim = 0.4f,
            showTitle = false, titleScale = 1.2f, showPages = false,
            customGlass = true, tint = 0.3f, edge = false,
        )
        val stored = mutableMapOf<String, Any>("area_folder_icon_scale" to 1.2f, "home_setting" to true)
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putFloat", "putBoolean" -> { stored[args!![0] as String] = args[1]; editor }
                else -> error("Unexpected preference operation: ${method.name}")
            }
        } as SharedPreferences.Editor
        editor.putFolders(value)
        assertEquals(value, FolderPreferences.fromStored(stored))
        assertEquals(1.2f, stored["area_folder_icon_scale"])
        assertEquals(true, stored["home_setting"])
    }

    @Test
    fun corruptTypesAndNonFiniteValuesRestoreDefaults() {
        val bad = mapOf(
            "folder_width" to Float.NaN, "folder_height" to Float.POSITIVE_INFINITY,
            "folder_radius" to Float.NEGATIVE_INFINITY, "folder_dim" to "50%",
            "folder_title_scale" to Float.NaN, "folder_tint" to Float.NaN,
            "folder_show_title" to 0, "folder_show_pages" to "false", "folder_custom_glass" to "true",
        )
        assertEquals(FolderPreferences(), FolderPreferences.fromStored(bad))
    }

    @Test
    fun settingsBoundaryClampsUnsafePanelValues() {
        val state = SettingsState(folders = FolderPreferences(
            width = -1f, height = 10000f, radius = -1f, dim = 1f,
            titleScale = 10f, tint = -0.5f,
        )).sanitized()
        assertEquals(Tokens.FolderPanelMinWidth.value, state.folders.width)
        assertEquals(Tokens.FolderPanelMaxHeight.value, state.folders.height)
        assertEquals(0f, state.folders.radius)
        assertEquals(Tokens.FolderMaxScrimAlpha, state.folders.dim)
        assertEquals(Tokens.FolderMaxTitleScale, state.folders.titleScale)
        assertEquals(0f, state.folders.tint)
    }

    @Test
    fun validDefaultsAndBoundsRoundTripWithoutDrift() {
        val values = listOf(
            FolderPreferences(),
            FolderPreferences(width = Tokens.FolderPanelMinWidth.value, height = Tokens.FolderPanelMinHeight.value,
                radius = 0f, dim = 0f, titleScale = Tokens.FolderMinTitleScale, tint = 0f),
            FolderPreferences(width = Tokens.FolderPanelMaxWidth.value, height = Tokens.FolderPanelMaxHeight.value,
                radius = Tokens.FolderPanelMaxRadius.value, dim = Tokens.FolderMaxScrimAlpha,
                titleScale = Tokens.FolderMaxTitleScale, tint = SettingsState.MAX_GLASS_TINT),
        )
        values.forEach {
            assertEquals(it, it.sanitized())
            assertEquals(it, FolderPreferences.fromStored(it.storedValues()))
        }
    }
}
