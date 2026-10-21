package com.zfl.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.settings.AreaStyle
import com.zfl.launcher.settings.FolderPreferences
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.settings.SettingsState

/**
 * Icon size, name size and names of one [area]. Each value either follows the general
 * setting or is the area's own; Reset makes the whole area follow the general settings again.
 */
@Composable
fun AreaIconsScreen(vm: SettingsViewModel, area: IconArea, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val style = settings.style(area)
    val look = settings.look(area)
    // Local values while dragging; persisted when the finger is lifted.
    var scale by remember(look.iconScale) { mutableFloatStateOf(look.iconScale) }
    var labelScale by remember(look.labelScale) { mutableFloatStateOf(look.labelScale) }
    val set: (AreaStyle) -> Unit = { next -> vm.settings.update { it.withStyle(area, next) } }

    SettingsPage(
        title = stringResource(area.titleRes),
        onBack = onBack,
        action = { ResetAction { set(AreaStyle.Inherit) } },
    ) {
        item {
            SettingsCard {
                IconPreviewRow(
                    sampleApps(vm, 3), settings,
                    scale = scale, savedScale = look.iconScale, labelScale = labelScale, showLabels = look.showLabels,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.area_custom_icon_size), style.iconScale != null, onCheckedChange = { on ->
                    set(style.copy(iconScale = if (on) look.iconScale else null))
                })
                SliderRow(
                    title = stringResource(R.string.icons_size),
                    value = scale,
                    valueText = percent(scale),
                    onValueChange = { scale = it },
                    onValueChangeFinished = { set(style.copy(iconScale = scale)) },
                    valueRange = SettingsState.MIN_ICON_SCALE..SettingsState.MAX_ICON_SCALE,
                    enabled = style.iconScale != null,
                )
                SwitchRow(stringResource(R.string.area_custom_label_size), style.labelScale != null, onCheckedChange = { on ->
                    set(style.copy(labelScale = if (on) look.labelScale else null))
                })
                SliderRow(
                    title = stringResource(R.string.area_label_size),
                    value = labelScale,
                    valueText = percent(labelScale),
                    onValueChange = { labelScale = it },
                    onValueChangeFinished = { set(style.copy(labelScale = labelScale)) },
                    valueRange = SettingsState.MIN_TEXT_SCALE..SettingsState.MAX_TEXT_SCALE,
                    enabled = style.labelScale != null,
                )
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.icons_labels))
                    Spacer(Modifier.height(10.dp))
                    val choices = listOf(null, true, false)
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.area_labels_default),
                            stringResource(R.string.area_labels_show),
                            stringResource(R.string.area_labels_hide),
                        ),
                        selectedIndex = choices.indexOf(style.showLabels),
                        onSelect = { set(style.copy(showLabels = choices[it])) },
                    )
                }
            }
        }
    }
}

/** Rows of Settings › Icons that open the size settings of each area. */
@Composable
fun AreaIconRows(settings: SettingsState, onOpen: (IconArea) -> Unit) {
    IconArea.entries.forEach { area ->
        NavRow(
            area.icon,
            stringResource(area.titleRes),
            onClick = { onOpen(area) },
            value = if (!settings.style(area).isInherited || (area == IconArea.Folder && settings.folders != FolderPreferences()))
                stringResource(R.string.area_custom) else null,
        )
    }
}

private val IconArea.titleRes: Int
    get() = when (this) {
        IconArea.Home -> R.string.area_home
        IconArea.Dock -> R.string.area_dock
        IconArea.Folder -> R.string.area_folders
    }

private val IconArea.icon: ImageVector
    get() = when (this) {
        IconArea.Home -> Icons.Rounded.GridView
        IconArea.Dock -> Icons.Rounded.Dock
        IconArea.Folder -> Icons.Rounded.Folder
    }
