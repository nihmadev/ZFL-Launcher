package com.zfl.launcher.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.PageTransition
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.home.HomePreview
import com.zfl.launcher.ui.home.pageTransition
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// region General

/** Navigation bar, glass effect and icon label size. */
@Composable
fun GeneralSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val selfPackage = LocalContext.current.packageName
    val sample = apps.firstOrNull { it.packageName != selfPackage }
    // Local value while dragging; persisted when the finger is lifted.
    var scale by remember(settings.textScale) { mutableFloatStateOf(settings.textScale) }

    SettingsPage(title = stringResource(R.string.row_general), onBack = onBack) {
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.general_hide_navigation), settings.hideNavigation, vm.settings::setHideNavigation)
                SwitchRow(stringResource(R.string.general_blur), settings.blurEffect, vm.settings::setBlurEffect)
                SwitchRow(stringResource(R.string.general_custom_text_size), settings.customTextSize, vm.settings::setCustomTextSize)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (sample != null) {
                        val previewScale = if (settings.customTextSize) scale else 1f
                        CompositionLocalProvider(
                            LocalSettingsState provides settings.copy(customTextSize = true, textScale = previewScale),
                        ) {
                            AppIconImage(sample, size = 64.dp)
                            Spacer(Modifier.height(6.dp))
                            IconLabel(sample.label)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    IosSlider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = SettingsState.MIN_TEXT_SCALE..SettingsState.MAX_TEXT_SCALE,
                        enabled = settings.customTextSize,
                        onValueChangeFinished = { vm.settings.setTextScale(scale) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(scale * 100).roundToInt()}%", color = SettingsStyle.SecondaryText, fontSize = 14.sp)
                }
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

// endregion

// region Screen grid

/** Number of icon rows, with a live preview of the user's own home screen. Saved with the ✓ button, like iOS27L. */
@Composable
fun GridSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    var rows by remember { mutableIntStateOf(settings.gridRows) }
    val options = SettingsState.GRID_ROW_OPTIONS

    SettingsPage(
        title = stringResource(R.string.row_grid),
        onBack = onBack,
        action = {
            GlassCircleButton(Icons.Rounded.Check, stringResource(R.string.action_done), onClick = {
                vm.settings.setGridRows(rows)
                onBack()
            })
        },
    ) {
        item {
            val shape = ContinuousRoundedShape(34.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = rows, label = "gridPreview") { previewRows ->
                    val preview = remember(apps, settings, previewRows) { vm.homePreview(apps, settings, previewRows) }
                    HomePreview(
                        state = preview,
                        wallpaper = wallpaper,
                        modifier = Modifier
                            .width(232.dp)
                            .clip(shape)
                            .border(2.dp, Color.White.copy(alpha = 0.3f), shape),
                    )
                }
            }
        }
        item {
            SegmentedControl(
                options = options.map { "4×$it" },
                selectedIndex = options.indexOf(rows),
                onSelect = { rows = options[it] },
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

// endregion

// region Transition effect

/** Page swipe effect, with an animated preview that replays on every change. */
@Composable
fun TransitionSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()

    SettingsPage(title = stringResource(R.string.row_transition), onBack = onBack) {
        item { TransitionPreview(settings.pageTransition, apps) }
        item { SectionHeader(stringResource(R.string.transition_section)) }
        item {
            SettingsCard {
                PageTransition.entries.forEach { effect ->
                    CheckRow(stringResource(effect.labelRes), effect == settings.pageTransition) {
                        vm.settings.setPageTransition(effect)
                    }
                }
            }
        }
    }
}

/** Three mini home screen pages that swipe to the next page and back using [effect]. */
@Composable
private fun TransitionPreview(effect: PageTransition, apps: List<AppInfo>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(effect) {
        progress.snapTo(0f)
        delay(250)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(700)
        progress.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
    }
    val pageWidth = 128.dp
    val pageWidthPx = with(LocalDensity.current) { pageWidth.toPx() }

    SettingsCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            for (page in -1..2) {
                MiniPage(
                    apps = apps.drop((page + 1) * MINI_PAGE_ICONS).take(MINI_PAGE_ICONS),
                    modifier = Modifier
                        .offset { IntOffset(((page - progress.value) * pageWidthPx).roundToInt(), 0) }
                        .pageTransition(effect) { page - progress.value }
                        .size(pageWidth - 12.dp, 210.dp),
                )
            }
        }
    }
}

private const val MINI_PAGE_ICONS = 16

@Composable
private fun MiniPage(apps: List<AppInfo>, modifier: Modifier = Modifier) {
    val shape = ContinuousRoundedShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { AppIconImage(it, size = 20.dp) }
            }
        }
    }
}

// endregion

// region App animations

@Composable
fun AnimationSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.row_animations), onBack = onBack) {
        item {
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.animations_resume),
                    description = stringResource(R.string.animations_resume_description),
                    checked = settings.resumeAnimation,
                    onCheckedChange = vm.settings::setResumeAnimation,
                )
                SwitchRow(
                    title = stringResource(R.string.animations_open_close),
                    description = stringResource(R.string.animations_open_close_description),
                    checked = settings.openCloseAnimation,
                    onCheckedChange = vm.settings::setOpenCloseAnimation,
                )
            }
        }
    }
}

// endregion
