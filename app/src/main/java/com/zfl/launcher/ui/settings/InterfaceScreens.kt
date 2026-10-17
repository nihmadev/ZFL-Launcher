package com.zfl.launcher.ui.settings

import android.os.Build
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableStateOf
import com.zfl.launcher.ui.glass.GlassMode
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.WallpaperBlur
import com.zfl.launcher.ui.glass.glassStyle
import com.zfl.launcher.ui.glass.isLiquidGlassSupported
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.DOCK_CAPACITY_OPTIONS
import com.zfl.launcher.settings.MenuAction
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.GlassStyle
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.glass.LocalGlassStyle
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.home.HomePreview
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

private val Defaults = SettingsState()

/** Top bar button that restores the defaults of the current screen. */
@Composable
internal fun ResetAction(onReset: () -> Unit) {
    GlassCircleButton(Icons.Rounded.Restore, stringResource(R.string.action_reset), onClick = onReset)
}

internal fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

/** A few apps for the previews (not the settings app itself). */
@Composable
internal fun sampleApps(vm: SettingsViewModel, count: Int): List<AppInfo> {
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val selfPackage = LocalContext.current.packageName
    return remember(apps) { apps.filter { it.packageName != selfPackage }.take(count) }
}

// region Glass & blur

@Composable
fun GlassSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    // Local values while dragging; persisted when the finger is lifted.
    var tint by remember(settings.glassTint) { mutableFloatStateOf(settings.glassTint) }
    var refraction by remember(settings.glassRefraction) { mutableFloatStateOf(settings.glassRefraction) }
    var radius by remember(settings.homeBlurRadius) { mutableFloatStateOf(settings.homeBlurRadius) }
    // Android 12 has no runtime shaders: the choice isn't offered and glass stays Frosted.
    val liquidSupported = isLiquidGlassSupported(Build.VERSION.SDK_INT)
    val liquid = liquidSupported && settings.glassMode == GlassMode.Liquid

    SettingsPage(
        title = stringResource(R.string.row_glass),
        onBack = onBack,
        action = {
            ResetAction {
                vm.settings.update {
                    it.copy(
                        blurEffect = Defaults.blurEffect,
                        glassTint = Defaults.glassTint,
                        glassEdge = Defaults.glassEdge,
                        glassMode = Defaults.glassMode,
                        glassRefraction = Defaults.glassRefraction,
                        glassTiltLight = Defaults.glassTiltLight,
                        homeBlur = Defaults.homeBlur,
                        homeBlurRadius = Defaults.homeBlurRadius,
                    )
                }
            }
        },
    ) {
        item {
            GlassPreview(sampleApps(vm, 4), settings.glassStyle(tint, refraction), settings.blurEffect, wallpaper)
        }
        item { SectionHeader(stringResource(R.string.glass_section_panels)) }
        item {
            SettingsCard {
                if (liquidSupported) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        RowTitle(stringResource(R.string.glass_style))
                        Spacer(Modifier.height(10.dp))
                        SegmentedControl(
                            options = GlassMode.entries.map { stringResource(it.labelRes) },
                            selectedIndex = GlassMode.entries.indexOf(settings.glassMode),
                            onSelect = { index -> vm.settings.update { it.copy(glassMode = GlassMode.entries[index]) } },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.general_blur), settings.blurEffect, vm.settings::setBlurEffect)
                SliderRow(
                    title = stringResource(R.string.glass_tint),
                    value = tint,
                    valueText = percent(tint),
                    onValueChange = { tint = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(glassTint = tint) } },
                    valueRange = 0f..SettingsState.MAX_GLASS_TINT,
                )
                SwitchRow(stringResource(R.string.glass_edge), settings.glassEdge, onCheckedChange = { value ->
                    vm.settings.update { it.copy(glassEdge = value) }
                })
                if (liquid) {
                    SliderRow(
                        title = stringResource(R.string.glass_refraction),
                        value = refraction,
                        valueText = percent(refraction),
                        onValueChange = { refraction = it },
                        onValueChangeFinished = { vm.settings.update { it.copy(glassRefraction = refraction) } },
                        valueRange = 0f..1f,
                        enabled = settings.blurEffect,
                    )
                    SwitchRow(stringResource(R.string.glass_tilt_light), settings.glassTiltLight, onCheckedChange = { value ->
                        vm.settings.update { it.copy(glassTiltLight = value) }
                    })
                }
            }
        }
        item { SectionHeader(stringResource(R.string.glass_section_background)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.glass_home_blur), settings.homeBlur, onCheckedChange = { value ->
                    vm.settings.update { it.copy(homeBlur = value) }
                })
                SliderRow(
                    title = stringResource(R.string.glass_home_blur_radius),
                    value = radius,
                    valueText = "${radius.roundToInt()} dp",
                    onValueChange = { radius = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(homeBlurRadius = radius) } },
                    valueRange = SettingsState.MIN_HOME_BLUR..SettingsState.MAX_HOME_BLUR,
                    enabled = settings.homeBlur,
                )
            }
        }
        if (settings.blurEffect && !actions.hasWallpaperAccess) {
            item { SectionFooter(stringResource(R.string.general_wallpaper_access_body)) }
            item {
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    NavRow(Icons.Rounded.Wallpaper, stringResource(R.string.general_wallpaper_access), onClick = actions::requestWallpaperAccess)
                }
            }
        }
    }
}

private val GlassMode.labelRes: Int
    get() = when (this) {
        GlassMode.Frosted -> R.string.glass_style_frosted
        GlassMode.Liquid -> R.string.glass_style_liquid
    }

/**
 * A search pill and a small dock drawn with the glass style being edited, over a window onto
 * the real wallpaper (sharp, as on the Home Screen), so both modes look as they will at home.
 */
@Composable
private fun GlassPreview(apps: List<AppInfo>, style: GlassStyle, blur: Boolean, wallpaper: ImageBitmap?) {
    val source = LocalGlassSource.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(
        LocalGlassStyle provides style,
        LocalGlassSource provides source.takeIf { blur },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(PreviewShape)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .drawBehind {
                    // The wallpaper as laid out on screen, shifted so this box shows the part under it.
                    if (wallpaper != null) {
                        val k = WallpaperBlur.PREVIEW_DOWNSCALE
                        drawImage(
                            wallpaper,
                            dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                            dstSize = IntSize(wallpaper.width * k, wallpaper.height * k),
                        )
                    }
                }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .height(Tokens.PillHeight)
                    .glass(ContinuousRoundedShape(Tokens.PillHeight / 2), profile = GlassProfile.Button)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = Tokens.TextPrimary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.search), color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .glass(ContinuousRoundedShape(28.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                apps.forEach { AppIconImage(it, size = 52.dp) }
            }
        }
    }
}

private val PreviewShape = ContinuousRoundedShape(26.dp)

// endregion

// region Home Screen & Dock

@Composable
fun HomeScreenSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val slots = DOCK_CAPACITY_OPTIONS

    SettingsPage(
        title = stringResource(R.string.row_home_screen),
        onBack = onBack,
        action = {
            ResetAction {
                vm.settings.update {
                    it.copy(
                        showDock = Defaults.showDock,
                        dockBackground = Defaults.dockBackground,
                        dockSlots = Defaults.dockSlots,
                        showPagePill = Defaults.showPagePill,
                        appLibrary = Defaults.appLibrary,
                    )
                }
            }
        },
    ) {
        item {
            val shape = ContinuousRoundedShape(30.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val layout by vm.layout.collectAsStateWithLifecycle()
                val preview = remember(apps, settings, layout) { vm.homePreview(apps, settings) }
                HomePreview(
                    state = preview,
                    wallpaper = wallpaper,
                    modifier = Modifier
                        .width(200.dp)
                        .clip(shape)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), shape),
                )
            }
        }
        item { SectionHeader(stringResource(R.string.home_section_dock)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.home_show_dock), settings.showDock, onCheckedChange = { value ->
                    vm.settings.update { it.copy(showDock = value) }
                })
                SwitchRow(stringResource(R.string.home_dock_background), settings.dockBackground, onCheckedChange = { value ->
                    vm.settings.update { it.copy(dockBackground = value) }
                })
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.home_dock_apps))
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        options = slots.map { it.toString() },
                        selectedIndex = slots.indexOf(settings.dockSlots),
                        onSelect = { index -> vm.settings.update { it.copy(dockSlots = slots[index]) } },
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.home_section_pages)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.home_page_pill), settings.showPagePill, onCheckedChange = { value ->
                    vm.settings.update { it.copy(showPagePill = value) }
                })
                SwitchRow(stringResource(R.string.home_app_library), settings.appLibrary, onCheckedChange = { value ->
                    vm.settings.update { it.copy(appLibrary = value) }
                })
            }
        }
        item { SectionHeader(stringResource(R.string.home_section_layout)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.layout_lock), settings.layoutLocked, vm.settings::setLayoutLocked)
            }
        }
    }
}

// endregion

// region Context menu

@Composable
fun MenuSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    var dim by remember(settings.menuDim) { mutableFloatStateOf(settings.menuDim) }

    SettingsPage(
        title = stringResource(R.string.row_context_menu),
        onBack = onBack,
        action = {
            ResetAction {
                vm.settings.update { it.copy(menuDim = Defaults.menuDim, hiddenMenuActions = Defaults.hiddenMenuActions) }
            }
        },
    ) {
        item {
            SettingsCard {
                SliderRow(
                    title = stringResource(R.string.menu_dim),
                    value = dim,
                    valueText = percent(dim),
                    onValueChange = { dim = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(menuDim = dim) } },
                    valueRange = 0f..SettingsState.MAX_MENU_DIM,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.menu_section_items)) }
        item {
            SettingsCard {
                MenuAction.entries.forEach { action ->
                    SwitchRow(stringResource(action.labelRes), settings.isMenuActionEnabled(action), onCheckedChange = { enabled ->
                        vm.settings.setMenuActionEnabled(action, enabled)
                    })
                }
            }
        }
    }
}

/** Name of a menu item in settings. */
private val MenuAction.labelRes: Int
    get() = when (this) {
        MenuAction.Shortcuts -> R.string.menu_shortcuts
        MenuAction.RenameFolder -> R.string.menu_rename_folder_setting
        MenuAction.AddToHome -> R.string.menu_add_to_home
        MenuAction.AppInfo -> R.string.menu_app_info
        MenuAction.Dock -> R.string.menu_dock_toggle
        MenuAction.HideApp -> R.string.menu_hide_app
        MenuAction.EditHome -> R.string.menu_edit_home
        MenuAction.RemoveApp -> R.string.menu_remove_app
        MenuAction.LibraryPin -> R.string.menu_library_pin
        MenuAction.LibraryCategory -> R.string.menu_library_category_setting
    }

// endregion
