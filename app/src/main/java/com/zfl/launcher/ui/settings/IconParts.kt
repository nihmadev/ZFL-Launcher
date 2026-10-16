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
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconAppearance
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.IconShape
import com.zfl.launcher.ui.theme.Tokens

/**
 * Live preview of real app icons at real size, drawn by the same loader as the home screen (so
 * in the saved appearance). [scale] and [corner] may be unsaved values while a slider is dragged:
 * the new shape is clipped on top until it is saved. Shared by Settings › Icons and the onboarding.
 */
@Composable
fun IconPreviewRow(apps: List<AppInfo>, settings: SettingsState, scale: Float = settings.iconScale, corner: Float = settings.iconCorner) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Tokens.IconSize * SettingsState.MAX_ICON_SCALE + 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconImage(app, Modifier.clip(IconShape(corner)), size = Tokens.IconSize * scale)
                if (settings.showLabels) {
                    Spacer(Modifier.height(6.dp))
                    IconLabel(settings.customLabels[app.key] ?: app.label)
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

private val IconAppearance.labelRes: Int
    get() = when (this) {
        IconAppearance.Default -> R.string.icons_appearance_default
        IconAppearance.Dark -> R.string.icons_appearance_dark
        IconAppearance.Tinted -> R.string.icons_appearance_tinted
        IconAppearance.Auto -> R.string.icons_appearance_auto
    }
