package com.zfl.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import com.zfl.launcher.island.IslandGrants
import com.zfl.launcher.settings.BadgeStyle
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconAppearance
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.IconShape
import com.zfl.launcher.ui.theme.Tokens

/**
 * Live preview of real app icons at real size, drawn by the same loader as the home screen (so
 * in the saved appearance). [scale], [corner] and [labelScale] may be unsaved values while a
 * slider is dragged: the bitmap keeps the saved [savedScale] until then, and the new shape is
 * clipped on top. Shared by Settings › Icons, the sizes of each area and the onboarding.
 */
@Composable
fun IconPreviewRow(
    apps: List<AppInfo>,
    settings: SettingsState,
    scale: Float = settings.iconScale,
    corner: Float = settings.iconCorner,
    savedScale: Float = settings.iconScale,
    labelScale: Float = settings.labelScale,
    showLabels: Boolean = settings.showLabels,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Tokens.IconSize * SettingsState.MAX_ICON_SCALE + 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconImage(app, Modifier.clip(IconShape(corner)), size = Tokens.IconSize * scale, renderSize = Tokens.IconSize * savedScale)
                if (showLabels) {
                    Spacer(Modifier.height(6.dp))
                    IconLabel(settings.customLabels[app.key] ?: app.label, scale = labelScale)
                }
            }
        }
    }
}

/** Default / Dark / Tinted / Automatic as check rows, for use inside a [SettingsCard]. */
@Composable
fun IconAppearanceRows(selected: IconAppearance, onSelect: (IconAppearance) -> Unit) {
    IconAppearance.entries.forEach { appearance ->
        CheckRow(stringResource(appearance.labelRes), selected == appearance, onClick = { onSelect(appearance) })
    }
}

/**
 * Badge style choices and, while badges are on, the one notification access the Island also
 * uses: allowed or not, and reconnecting a granted listener the system didn't bind.
 */
@Composable
fun BadgeRows(selected: BadgeStyle, grants: IslandGrants, actions: SettingsActions, onSelect: (BadgeStyle) -> Unit) {
    BadgeStyle.entries.forEach { style ->
        CheckRow(stringResource(style.labelRes), selected == style, onClick = { onSelect(style) })
    }
    if (selected == BadgeStyle.Off) return
    NavRow(Icons.Rounded.Notifications, stringResource(R.string.badges_access), actions::requestIslandMusicAccess,
        value = stringResource(if (grants.notifications) R.string.island_allowed else R.string.island_allow), tileColor = SettingsStyle.Tile.Red)
    if (grants.notifications && !grants.connected) {
        NavRow(Icons.Rounded.Notifications, stringResource(R.string.badges_connect), actions::reconnectIsland, tileColor = SettingsStyle.Tile.Red)
    }
}

private val BadgeStyle.labelRes: Int
    get() = when (this) {
        BadgeStyle.Off -> R.string.badges_off
        BadgeStyle.Number -> R.string.badges_number
        BadgeStyle.Dot -> R.string.badges_dot
    }

private val IconAppearance.labelRes: Int
    get() = when (this) {
        IconAppearance.Default -> R.string.icons_appearance_default
        IconAppearance.Dark -> R.string.icons_appearance_dark
        IconAppearance.Tinted -> R.string.icons_appearance_tinted
        IconAppearance.Auto -> R.string.icons_appearance_auto
    }
