package com.zfl.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.island.IslandAccess
import com.zfl.launcher.settings.IslandPreferences
import kotlin.math.roundToInt

/** Intent and actual access are separate; every access request is an explicit user action. */
@Composable
fun IslandSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, onBack: () -> Unit): Unit {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val grants by IslandAccess.state.collectAsStateWithLifecycle()
    val status = when {
        !settings.island.enabled -> R.string.island_off
        !grants.window -> R.string.island_need_window
        !grants.notifications -> R.string.island_need_music
        !grants.connected -> R.string.island_need_connection
        else -> R.string.island_ready
    }
    val island = settings.island
    fun save(change: (IslandPreferences) -> IslandPreferences) = vm.settings.update { it.copy(island = change(it.island)) }
    // Sliders keep the dragged value locally and save it once released.
    var collapse by remember(island.collapseSeconds) { mutableFloatStateOf(island.collapseSeconds) }
    var width by remember(island.compactWidth) { mutableFloatStateOf(island.compactWidth) }
    var margin by remember(island.sideMargin) { mutableFloatStateOf(island.sideMargin) }
    var offset by remember(island.topOffset) { mutableFloatStateOf(island.topOffset) }
    SettingsPage(stringResource(R.string.island_title), onBack) {
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.island_enable), settings.island.enabled, { enabled ->
                    vm.settings.update { it.copy(island = it.island.copy(enabled = enabled)) }
                    if (enabled) actions.reconnectIsland()
                })
            }
            SectionFooter(stringResource(status))
        }
        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Layers, stringResource(R.string.island_window_access), actions::requestIslandWindowAccess,
                    value = stringResource(if (grants.window) R.string.island_allowed else R.string.island_allow), tileColor = SettingsStyle.Tile.Purple)
                NavRow(Icons.Rounded.MusicNote, stringResource(R.string.island_music_access), actions::requestIslandMusicAccess,
                    value = stringResource(if (grants.notifications) R.string.island_allowed else R.string.island_allow), tileColor = SettingsStyle.Tile.Pink)
                if (grants.notifications && !grants.connected) {
                    NavRow(Icons.Rounded.MusicNote, stringResource(R.string.island_connect), actions::reconnectIsland)
                }
            }
        }
        if (!grants.window) {
            item { SectionGap() }
            item {
                SettingsCard {
                    NavRow(Icons.Rounded.Lock, stringResource(R.string.island_restricted), actions::openIslandAppInfo,
                        tileColor = SettingsStyle.Tile.Gray)
                }
                SectionFooter(stringResource(R.string.island_restricted_footer))
            }
        }
        item { SectionHeader(stringResource(R.string.island_section_behavior)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.island_always_on), island.alwaysOn, { value -> save { it.copy(alwaysOn = value) } })
                SwitchRow(stringResource(R.string.island_timers_setting), island.timers, { value -> save { it.copy(timers = value) } })
                SwitchRow(stringResource(R.string.island_charge_setting), island.charging, { value -> save { it.copy(charging = value) } })
                SliderRow(
                    title = stringResource(R.string.island_collapse_delay),
                    value = collapse,
                    valueText = stringResource(R.string.island_seconds, collapse.roundToInt()),
                    onValueChange = { collapse = it },
                    onValueChangeFinished = { save { it.copy(collapseSeconds = collapse.roundToInt().toFloat()) } },
                    valueRange = IslandPreferences.COLLAPSE_RANGE,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.island_section_look)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.island_blur_setting), island.blur, { value -> save { it.copy(blur = value) } })
                SwitchRow(stringResource(R.string.island_artwork_tint), island.artworkTint, { value -> save { it.copy(artworkTint = value) } })
                SliderRow(
                    title = stringResource(R.string.island_pill_width),
                    value = width,
                    valueText = "${width.roundToInt()} dp",
                    onValueChange = { width = it },
                    onValueChangeFinished = { save { it.copy(compactWidth = width.roundToInt().toFloat()) } },
                    valueRange = IslandPreferences.COMPACT_WIDTH_RANGE,
                )
                SliderRow(
                    title = stringResource(R.string.island_side_margin),
                    value = margin,
                    valueText = "${margin.roundToInt()} dp",
                    onValueChange = { margin = it },
                    onValueChangeFinished = { save { it.copy(sideMargin = margin.roundToInt().toFloat()) } },
                    valueRange = IslandPreferences.SIDE_MARGIN_RANGE,
                )
                SliderRow(
                    title = stringResource(R.string.island_top_offset),
                    value = offset,
                    valueText = "${offset.roundToInt()} dp",
                    onValueChange = { offset = it },
                    onValueChangeFinished = { save { it.copy(topOffset = offset.roundToInt().toFloat()) } },
                    valueRange = IslandPreferences.TOP_OFFSET_RANGE,
                )
            }
            if (island.blur) SectionFooter(stringResource(R.string.island_blur_footer))
        }
    }
}
