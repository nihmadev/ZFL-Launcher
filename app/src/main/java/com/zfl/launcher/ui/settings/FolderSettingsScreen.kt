package com.zfl.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.settings.AreaStyle
import com.zfl.launcher.settings.FolderPreferences
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.theme.Tokens

/** A folder's surface and contents, with a draft while dragging and one save on release. */
@Composable
fun FolderSettingsScreen(vm: SettingsViewModel, onOpenIcons: () -> Unit, onBack: () -> Unit): Unit {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    var draft by remember(settings.folders) { mutableStateOf(settings.folders) }
    val apps = sampleApps(vm, 12)
    val save = { vm.settings.update { it.copy(folders = draft) } }
    val set: (FolderPreferences) -> Unit = { next -> draft = next; save() }

    SettingsPage(
        title = stringResource(R.string.area_folders),
        onBack = onBack,
        action = { ResetAction { vm.settings.update { it.copy(folders = FolderPreferences(), folderStyle = AreaStyle.Inherit) } } },
    ) {
        item { FolderPreview(apps, settings.copy(folders = draft)) }
        item { SectionHeader(stringResource(R.string.folders_panel)) }
        item {
            SettingsCard {
                SliderRow(
                    title = stringResource(R.string.folders_width),
                    value = draft.width,
                    valueText = percent(draft.width / Tokens.FolderPanelWidth.value),
                    onValueChange = { draft = draft.copy(width = it) },
                    onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                    valueRange = Tokens.FolderPanelMinWidth.value..Tokens.FolderPanelMaxWidth.value,
                )
                SliderRow(
                    title = stringResource(R.string.folders_height),
                    value = draft.height,
                    valueText = percent(draft.height / Tokens.FolderPanelHeight.value),
                    onValueChange = { draft = draft.copy(height = it) },
                    onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                    valueRange = Tokens.FolderPanelMinHeight.value..Tokens.FolderPanelMaxHeight.value,
                )
                SliderRow(
                    title = stringResource(R.string.icons_corner),
                    value = draft.radius,
                    valueText = percent(draft.radius / Tokens.FolderPanelMaxRadius.value),
                    onValueChange = { draft = draft.copy(radius = it) },
                    onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                    valueRange = 0f..Tokens.FolderPanelMaxRadius.value,
                )
                SliderRow(
                    title = stringResource(R.string.folders_dim),
                    value = draft.dim,
                    valueText = percent(draft.dim),
                    onValueChange = { draft = draft.copy(dim = it) },
                    onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                    valueRange = 0f..Tokens.FolderMaxScrimAlpha,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.row_glass)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.folders_custom_glass), draft.customGlass, { on ->
                    set(draft.copy(
                        customGlass = on,
                        tint = if (on) settings.glassTint else draft.tint,
                        edge = if (on) settings.glassEdge else draft.edge,
                    ))
                })
                if (draft.customGlass) {
                    SliderRow(
                        title = stringResource(R.string.glass_tint),
                        value = draft.tint,
                        valueText = percent(draft.tint),
                        onValueChange = { draft = draft.copy(tint = it) },
                        onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                        valueRange = 0f..SettingsState.MAX_GLASS_TINT,
                    )
                    SwitchRow(stringResource(R.string.glass_edge), draft.edge, { set(draft.copy(edge = it)) })
                }
            }
        }
        item { SectionHeader(stringResource(R.string.folders_content)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.folders_show_title), draft.showTitle, { set(draft.copy(showTitle = it)) })
                if (draft.showTitle) {
                    SliderRow(
                        title = stringResource(R.string.folders_title_size),
                        value = draft.titleScale,
                        valueText = percent(draft.titleScale),
                        onValueChange = { draft = draft.copy(titleScale = it) },
                        onValueChangeFinished = save,
                    onValueChangeCancelled = { draft = settings.folders },
                        valueRange = Tokens.FolderMinTitleScale..Tokens.FolderMaxTitleScale,
                    )
                }
                SwitchRow(stringResource(R.string.folders_page_indicator), draft.showPages, { set(draft.copy(showPages = it)) })
                NavRow(Icons.Rounded.Apps, stringResource(R.string.folders_app_icons), onOpenIcons, tileColor = SettingsStyle.Tile.Orange)
            }
        }
    }
}
