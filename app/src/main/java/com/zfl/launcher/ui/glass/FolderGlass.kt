package com.zfl.launcher.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.zfl.launcher.settings.FolderPreferences

/** Folder overrides leave the material, refraction and light shared with the rest of Home. */
internal fun GlassStyle.forFolder(preferences: FolderPreferences): GlassStyle =
    if (preferences.customGlass) copy(tintAlpha = preferences.tint, edge = preferences.edge) else this

/** The closed tile, open panel and settings preview resolve the same glass appearance. */
@Composable
internal fun FolderGlass(preferences: FolderPreferences, content: @Composable () -> Unit): Unit {
    val style = LocalGlassStyle.current.forFolder(preferences)
    CompositionLocalProvider(LocalGlassStyle provides style, content = content)
}
