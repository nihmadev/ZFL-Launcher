package com.zfl.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.settings.PageTransition

/** First settings page: the list of sections, like the iOS27L "Settings" screen. */
@Composable
fun MainSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, navigate: (SettingsRoute) -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    SettingsPage(title = stringResource(R.string.settings_title), onBack = null) {
        item { SectionHeader(stringResource(R.string.section_general)) }
        item {
            SettingsCard {
                if (actions.isDefaultHome) {
                    NavRow(Icons.Rounded.Home, stringResource(R.string.default_home_active), onClick = actions::requestDefaultHome, showChevron = false)
                } else {
                    NavRow(Icons.Rounded.Home, stringResource(R.string.default_home_select), onClick = actions::requestDefaultHome, titleColor = SettingsStyle.Link)
                }
                NavRow(Icons.Rounded.Settings, stringResource(R.string.row_general), onClick = { navigate(SettingsRoute.General) })
                NavRow(
                    Icons.Rounded.Translate,
                    stringResource(R.string.row_language),
                    onClick = actions::openLanguageSettings,
                    value = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
                )
                NavRow(
                    Icons.Rounded.GridView,
                    stringResource(R.string.row_grid),
                    onClick = { navigate(SettingsRoute.Grid) },
                    value = "4×${settings.gridRows}",
                )
                NavRow(Icons.Rounded.WbSunny, stringResource(R.string.widget_weather), onClick = { navigate(SettingsRoute.Weather) })
                NavRow(Icons.Rounded.SystemUpdateAlt, stringResource(R.string.row_import), onClick = { navigate(SettingsRoute.Import) })
            }
        }

        item { SectionHeader(stringResource(R.string.section_interface)) }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.BlurOn, stringResource(R.string.row_glass), onClick = { navigate(SettingsRoute.Glass) })
                NavRow(Icons.Rounded.Apps, stringResource(R.string.row_icons), onClick = { navigate(SettingsRoute.Icons) })
                NavRow(
                    Icons.Rounded.Dock,
                    stringResource(R.string.row_home_screen),
                    onClick = { navigate(SettingsRoute.HomeScreen) },
                )
                NavRow(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.row_context_menu), onClick = { navigate(SettingsRoute.ContextMenu) })
            }
        }

        item { SectionHeader(stringResource(R.string.section_transition)) }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.ViewCarousel,
                    stringResource(R.string.row_transition),
                    onClick = { navigate(SettingsRoute.Transition) },
                    value = stringResource(settings.pageTransition.labelRes),
                )
                NavRow(Icons.Rounded.Animation, stringResource(R.string.row_animations), onClick = { navigate(SettingsRoute.Animations) })
            }
        }

        item { SectionHeader(stringResource(R.string.section_icons)) }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.VisibilityOff,
                    stringResource(R.string.row_hidden_apps),
                    onClick = { navigate(SettingsRoute.HiddenApps) },
                    value = settings.hiddenApps.size.takeIf { it > 0 }?.toString(),
                )
                NavRow(Icons.Rounded.Category, stringResource(R.string.row_change_icon), onClick = { navigate(SettingsRoute.AppIcons) })
                NavRow(Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.row_change_name), onClick = { navigate(SettingsRoute.AppNames) })
            }
        }

        item { SectionHeader(stringResource(R.string.section_about)) }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Info, stringResource(R.string.row_version), onClick = {}, value = actions.versionName, showChevron = false)
                NavRow(Icons.Rounded.Gavel, stringResource(R.string.row_license), onClick = { navigate(SettingsRoute.License) }, value = "GPL-3.0+")
            }
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
