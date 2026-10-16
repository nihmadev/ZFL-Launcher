package com.zfl.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.icon.ICON_TINT_PALETTE
import com.zfl.launcher.ui.icon.IconAppearance
import com.zfl.launcher.ui.icon.LocalIconLoader

private val Defaults = SettingsState()

@Composable
fun IconSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    // Local values while dragging; persisted when the finger is lifted.
    var scale by remember(settings.iconScale) { mutableFloatStateOf(settings.iconScale) }
    var corner by remember(settings.iconCorner) { mutableFloatStateOf(settings.iconCorner) }
    var saturation by remember(settings.iconTintSaturation) { mutableFloatStateOf(settings.iconTintSaturation) }
    val apps = sampleApps(vm, 3)

    SettingsPage(
        title = stringResource(R.string.row_icons),
        onBack = onBack,
        action = {
            ResetAction {
                vm.settings.update {
                    it.copy(
                        iconScale = Defaults.iconScale,
                        iconCorner = Defaults.iconCorner,
                        showLabels = Defaults.showLabels,
                        iconAppearance = Defaults.iconAppearance,
                        iconTintFromWallpaper = Defaults.iconTintFromWallpaper,
                        iconTintColor = Defaults.iconTintColor,
                        iconTintSaturation = Defaults.iconTintSaturation,
                        tintCustomIcons = Defaults.tintCustomIcons,
                        iconEdge = Defaults.iconEdge,
                    )
                }
            }
        },
    ) {
        item { SettingsCard { IconPreviewRow(apps, settings, scale, corner) } }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            SettingsCard {
                SliderRow(
                    title = stringResource(R.string.icons_size),
                    value = scale,
                    valueText = percent(scale),
                    onValueChange = { scale = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(iconScale = scale) } },
                    valueRange = SettingsState.MIN_ICON_SCALE..SettingsState.MAX_ICON_SCALE,
                )
                SliderRow(
                    title = stringResource(R.string.icons_corner),
                    value = corner,
                    valueText = percent(corner / SettingsState.MAX_ICON_CORNER),
                    onValueChange = { corner = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(iconCorner = corner) } },
                    valueRange = 0f..SettingsState.MAX_ICON_CORNER,
                )
                SwitchRow(stringResource(R.string.icons_edge), settings.iconEdge, onCheckedChange = { value ->
                    vm.settings.update { it.copy(iconEdge = value) }
                })
                SwitchRow(stringResource(R.string.icons_labels), settings.showLabels, onCheckedChange = { value ->
                    vm.settings.update { it.copy(showLabels = value) }
                })
            }
        }
        item { SectionFooter(stringResource(R.string.icons_footer)) }
        item { SectionHeader(stringResource(R.string.icons_section_appearance)) }
        item {
            SettingsCard {
                IconAppearanceRows(settings.iconAppearance) { appearance ->
                    vm.settings.update { it.copy(iconAppearance = appearance) }
                }
                // Only offered when there is something to opt out: custom icons and a restyling appearance.
                if (settings.customIcons.isNotEmpty() && settings.iconAppearance != IconAppearance.Default) {
                    SwitchRow(stringResource(R.string.icons_tint_custom), settings.tintCustomIcons, onCheckedChange = { value ->
                        vm.settings.update { it.copy(tintCustomIcons = value) }
                    })
                }
            }
        }
        if (settings.iconAppearance == IconAppearance.Tinted) {
            item { SectionHeader(stringResource(R.string.icons_section_tint)) }
            item {
                SettingsCard {
                    TintSwatches(
                        fromWallpaper = settings.iconTintFromWallpaper,
                        color = settings.iconTintColor,
                        onWallpaper = { vm.settings.update { it.copy(iconTintFromWallpaper = true) } },
                        onColor = { color -> vm.settings.update { it.copy(iconTintFromWallpaper = false, iconTintColor = color) } },
                    )
                    SliderRow(
                        title = stringResource(R.string.icons_tint_saturation),
                        value = saturation,
                        valueText = percent(saturation),
                        onValueChange = { saturation = it },
                        onValueChangeFinished = { vm.settings.update { it.copy(iconTintSaturation = saturation) } },
                        valueRange = 0f..1f,
                    )
                }
            }
        }
    }
}

/** "From wallpaper" followed by the palette; the selected one has a ring. Swatches show the vivid hue. */
@Composable
private fun TintSwatches(fromWallpaper: Boolean, color: Int, onWallpaper: () -> Unit, onColor: (Int) -> Unit) {
    val wallpaperHue = LocalIconLoader.current.wallpaperTint(saturation = 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val wallpaperLabel = stringResource(R.string.icons_tint_wallpaper)
        Swatch(wallpaperHue?.let(::Color) ?: SettingsStyle.SecondaryText, fromWallpaper, wallpaperLabel, onWallpaper) {
            Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
        ICON_TINT_PALETTE.forEach { swatch ->
            Swatch(Color(swatch), !fromWallpaper && color == swatch, null, onClick = { onColor(swatch) })
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    description: String?,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                if (description != null) contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private val SWATCH_SIZE = 32.dp
