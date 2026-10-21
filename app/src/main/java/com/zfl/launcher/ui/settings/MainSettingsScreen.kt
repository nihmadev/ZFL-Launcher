package com.zfl.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.settings.PageTransition
import com.zfl.launcher.ui.onboarding.AppLogo
import com.zfl.launcher.ui.onboarding.finishSetupItems
import com.zfl.launcher.ui.onboarding.setupState
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Tokens

/** First settings page: a header with the launcher itself, then the groups of settings, like iOS Settings. */
@Composable
fun MainSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, navigate: (SettingsRoute) -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    val unfinished = finishSetupItems(actions.setupState(hasImportSources = null)).size

    SettingsPage(title = stringResource(R.string.settings_title), onBack = null) {
        item { LauncherHeader(actions.isDefaultHome, onClick = actions::requestDefaultHome) }
        if (unfinished > 0) {
            item { SectionGap() }
            item { FinishSetupRow(unfinished, onClick = { navigate(SettingsRoute.FinishSetup) }) }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Settings, stringResource(R.string.row_general), onClick = { navigate(SettingsRoute.General) })
                NavRow(Icons.Rounded.ViewCarousel, stringResource(R.string.island_title), onClick = { navigate(SettingsRoute.Island) }, tileColor = SettingsStyle.Tile.Purple)
                NavRow(
                    Icons.Rounded.Translate,
                    stringResource(R.string.row_language),
                    onClick = { navigate(SettingsRoute.Language) },
                    value = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
                    tileColor = SettingsStyle.Tile.Blue,
                )
                NavRow(
                    Icons.Rounded.GridView,
                    stringResource(R.string.row_grid),
                    onClick = { navigate(SettingsRoute.Grid) },
                    value = (layout?.grid ?: vm.savedGrid()).toString(),
                    tileColor = SettingsStyle.Tile.Indigo,
                )
                NavRow(
                    Icons.Rounded.WbSunny,
                    stringResource(R.string.widget_weather),
                    onClick = { navigate(SettingsRoute.Weather) },
                    tileColor = SettingsStyle.Tile.Cyan,
                )
                NavRow(
                    Icons.Rounded.SystemUpdateAlt,
                    stringResource(R.string.row_import),
                    onClick = { navigate(SettingsRoute.Import) },
                    tileColor = SettingsStyle.Tile.Green,
                )
            }
        }

        item { SectionGap() }
        item {
            SettingsCard {
                IconSwitchRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.row_dynamic_color),
                    checked = settings.dynamicColor,
                    onCheckedChange = vm.settings::setDynamicColor,
                    tileColor = SettingsStyle.Tile.Pink,
                )
                NavRow(Icons.Rounded.BlurOn, stringResource(R.string.row_glass), onClick = { navigate(SettingsRoute.Glass) }, tileColor = SettingsStyle.Tile.Teal)
                NavRow(Icons.Rounded.Apps, stringResource(R.string.row_icons), onClick = { navigate(SettingsRoute.Icons) }, tileColor = SettingsStyle.Tile.Orange)
                NavRow(Icons.Rounded.Folder, stringResource(R.string.area_folders), onClick = { navigate(SettingsRoute.Folders) }, tileColor = SettingsStyle.Tile.Indigo)
                NavRow(
                    Icons.Rounded.Dock,
                    stringResource(R.string.row_home_screen),
                    onClick = { navigate(SettingsRoute.HomeScreen) },
                    tileColor = SettingsStyle.Tile.Purple,
                )
                NavRow(
                    Icons.Rounded.ViewModule,
                    stringResource(R.string.library_title),
                    onClick = { navigate(SettingsRoute.AppLibrary) },
                    tileColor = SettingsStyle.Tile.Indigo,
                )
                NavRow(Icons.Rounded.Apps, stringResource(R.string.gestures_home), onClick = { navigate(SettingsRoute.HomeBehavior) })
                NavRow(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.row_context_menu), onClick = { navigate(SettingsRoute.ContextMenu) })
            }
        }

        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.ViewCarousel,
                    stringResource(R.string.row_transition),
                    onClick = { navigate(SettingsRoute.Transition) },
                    value = stringResource(settings.pageTransition.labelRes),
                    tileColor = SettingsStyle.Tile.Red,
                )
                NavRow(
                    Icons.Rounded.Animation,
                    stringResource(R.string.row_animations),
                    onClick = { navigate(SettingsRoute.Animations) },
                    tileColor = SettingsStyle.Tile.Yellow,
                )
            }
        }

        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.VisibilityOff,
                    stringResource(R.string.row_hidden_apps),
                    onClick = { navigate(SettingsRoute.HiddenApps) },
                    value = settings.hiddenApps.size.takeIf { it > 0 }?.toString(),
                )
                NavRow(
                    Icons.Rounded.Category,
                    stringResource(R.string.row_change_icon),
                    onClick = { navigate(SettingsRoute.AppIcons) },
                    tileColor = SettingsStyle.Tile.Blue,
                )
                NavRow(
                    Icons.Rounded.DriveFileRenameOutline,
                    stringResource(R.string.row_change_name),
                    onClick = { navigate(SettingsRoute.AppNames) },
                    tileColor = SettingsStyle.Tile.Green,
                )
            }
        }

        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Info, stringResource(R.string.row_version), onClick = null, value = actions.versionName)
                NavRow(Icons.Rounded.Gavel, stringResource(R.string.row_license), onClick = { navigate(SettingsRoute.License) }, value = "GPL-3.0+")
            }
        }
    }
}

/**
 * The launcher itself on top of the list, like the account card of iOS Settings: its icon, its
 * name and whether it is the home app. Tapping it opens the default home app choice.
 */
@Composable
private fun LauncherHeader(isDefaultHome: Boolean, onClick: () -> Unit) {
    SettingsCard {
        SettingsRow(onClick = onClick, modifier = Modifier.padding(vertical = 6.dp)) {
            AppLogo(size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (isDefaultHome) R.string.default_home_active else R.string.default_home_select),
                    color = if (isDefaultHome) SettingsStyle.SecondaryText else LocalAccent.current.primary,
                    fontSize = 15.sp,
                )
            }
            Chevron()
        }
    }
}

/** iOS "Finish Setting Up" row with a red badge: the onboarding steps the user skipped. */
@Composable
private fun FinishSetupRow(count: Int, onClick: () -> Unit) {
    SettingsCard {
        SettingsRow(onClick = onClick) {
            RowTitle(stringResource(R.string.finish_setup_title), modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .background(Tokens.Destructive, CircleShape)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(6.dp))
            Chevron()
        }
    }
}

/** Display name of a page transition. */
val PageTransition.labelRes: Int
    get() = when (this) {
        PageTransition.Default -> R.string.effect_default
        PageTransition.Rotation -> R.string.effect_rotation
        PageTransition.Flip -> R.string.effect_flip
        PageTransition.ZoomIn -> R.string.effect_zoom_in
        PageTransition.Alpha -> R.string.effect_alpha
    }
