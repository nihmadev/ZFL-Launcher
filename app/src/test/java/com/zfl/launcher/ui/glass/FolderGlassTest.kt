package com.zfl.launcher.ui.glass

import com.zfl.launcher.settings.FolderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FolderGlassTest {
    @Test
    fun inheritedFoldersFollowLiveGlassSettings() {
        val preferences = FolderPreferences(tint = 0.4f, edge = false)
        val first = GlassStyle(tintAlpha = 0.2f, edge = true, mode = GlassMode.Frosted)
        val changed = first.copy(tintAlpha = 0.3f, mode = GlassMode.Liquid)
        assertSame(first, first.forFolder(preferences))
        assertSame(changed, changed.forFolder(preferences))
    }

    @Test
    fun customGlassChangesOnlyFolderBrightnessAndEdge() {
        val base = GlassStyle(tintAlpha = 0.2f, edge = true, mode = GlassMode.Liquid, refraction = 0.6f)
        val custom = base.forFolder(FolderPreferences(customGlass = true, tint = 0.4f, edge = false))
        assertEquals(base.copy(tintAlpha = 0.4f, edge = false), custom)
        assertSame(base, base.forFolder(FolderPreferences()))
    }
}
