package com.zfl.launcher.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.ui.theme.Tokens

/** Where the home grid sits in the free height when its rows can't grow any taller. */
enum class GridAlignment { Top, Center, Bottom }

/** Space around the home grid, in dp: [side] left and right, [top] above it, [bottom] above the dock. */
data class GridMargins(val side: Dp, val top: Dp, val bottom: Dp) {
    /** Brings every margin back into its range, see [Tokens]. */
    fun sanitized(): GridMargins = GridMargins(
        side = side.safe(Default.side).coerceIn(0.dp, Tokens.GridSideMarginMax),
        top = top.safe(Default.top).coerceIn(0.dp, Tokens.GridVerticalMarginMax),
        bottom = bottom.safe(Default.bottom).coerceIn(0.dp, Tokens.GridVerticalMarginMax),
    )

    companion object {
        /** The margins the home grid always had. */
        val Default: GridMargins = GridMargins(Tokens.GridSidePadding, Tokens.GridTopPadding, 0.dp)
    }
}

private fun Dp.safe(fallback: Dp): Dp = if (value.isNaN()) fallback else this

/** Parts of the Home Screen whose icons can have their own size. */
enum class IconArea { Home, Dock, Folder }

/**
 * The icon look of one area. A null field follows the general setting: the icon size of
 * Settings › Icons, the label size of Settings › General, and the names switch.
 */
data class AreaStyle(
    val iconScale: Float? = null,
    val labelScale: Float? = null,
    val showLabels: Boolean? = null,
) {
    val isInherited: Boolean get() = this == Inherit

    fun sanitized(): AreaStyle = copy(
        iconScale = iconScale?.takeUnless { it.isNaN() }?.coerceIn(SettingsState.MIN_ICON_SCALE, SettingsState.MAX_ICON_SCALE),
        labelScale = labelScale?.takeUnless { it.isNaN() }?.coerceIn(SettingsState.MIN_TEXT_SCALE, SettingsState.MAX_TEXT_SCALE),
    )

    companion object {
        val Inherit: AreaStyle = AreaStyle()
    }
}

/** What an area actually draws: [AreaStyle] with the inherited values filled in. */
data class AreaLook(val iconScale: Float, val labelScale: Float, val showLabels: Boolean) {
    /** Requested icon size; a grid may draw it smaller when the cells are narrower. */
    val iconSize: Dp get() = Tokens.IconSize * iconScale
}
