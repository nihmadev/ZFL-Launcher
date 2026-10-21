package com.zfl.launcher.settings

import android.content.SharedPreferences
import com.zfl.launcher.ui.theme.Tokens

/** Appearance shared by all folders; app order and folder names belong to the saved layout. */
data class FolderPreferences(
    val width: Float = Tokens.FolderPanelWidth.value,
    val height: Float = Tokens.FolderPanelHeight.value,
    val radius: Float = Tokens.FolderPanelRadius.value,
    val dim: Float = Tokens.FolderScrimAlpha,
    val showTitle: Boolean = true,
    val titleScale: Float = 1f,
    val showPages: Boolean = true,
    /** With this off, both the tile and the open panel follow the launcher's glass settings. */
    val customGlass: Boolean = false,
    val tint: Float = SettingsState.DEFAULT_GLASS_TINT,
    val edge: Boolean = true,
) {
    fun sanitized(): FolderPreferences = copy(
        width = width.bounded(Tokens.FolderPanelWidth.value, Tokens.FolderPanelMinWidth.value, Tokens.FolderPanelMaxWidth.value),
        height = height.bounded(Tokens.FolderPanelHeight.value, Tokens.FolderPanelMinHeight.value, Tokens.FolderPanelMaxHeight.value),
        radius = radius.bounded(Tokens.FolderPanelRadius.value, 0f, Tokens.FolderPanelMaxRadius.value),
        dim = dim.bounded(Tokens.FolderScrimAlpha, 0f, Tokens.FolderMaxScrimAlpha),
        titleScale = titleScale.bounded(1f, Tokens.FolderMinTitleScale, Tokens.FolderMaxTitleScale),
        tint = tint.bounded(SettingsState.DEFAULT_GLASS_TINT, 0f, SettingsState.MAX_GLASS_TINT),
    )

    /** Explicit keys keep reads tolerant of older installs and future unrelated settings. */
    fun storedValues(): Map<String, Any> = mapOf(
        "folder_width" to width, "folder_height" to height, "folder_radius" to radius,
        "folder_dim" to dim, "folder_show_title" to showTitle, "folder_title_scale" to titleScale,
        "folder_show_pages" to showPages, "folder_custom_glass" to customGlass,
        "folder_tint" to tint, "folder_edge" to edge,
    )

    companion object {
        fun fromStored(values: Map<String, *>): FolderPreferences {
            val defaults = FolderPreferences()
            fun number(key: String, fallback: Float): Float = (values[key] as? Number)?.toFloat() ?: fallback
            fun flag(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback
            return FolderPreferences(
                width = number("folder_width", defaults.width),
                height = number("folder_height", defaults.height),
                radius = number("folder_radius", defaults.radius),
                dim = number("folder_dim", defaults.dim),
                showTitle = flag("folder_show_title", defaults.showTitle),
                titleScale = number("folder_title_scale", defaults.titleScale),
                showPages = flag("folder_show_pages", defaults.showPages),
                customGlass = flag("folder_custom_glass", defaults.customGlass),
                tint = number("folder_tint", defaults.tint),
                edge = flag("folder_edge", defaults.edge),
            ).sanitized()
        }
    }
}

private fun Float.bounded(fallback: Float, min: Float, max: Float): Float =
    if (isFinite()) coerceIn(min, max) else fallback

internal fun SharedPreferences.Editor.putFolders(value: FolderPreferences) {
    value.storedValues().forEach { (key, setting) ->
        when (setting) {
            is Float -> putFloat(key, setting)
            is Boolean -> putBoolean(key, setting)
        }
    }
}
