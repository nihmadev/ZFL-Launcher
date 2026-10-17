package com.zfl.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.profileId
import com.zfl.launcher.settings.AppTarget
import com.zfl.launcher.settings.HomeAction
import com.zfl.launcher.settings.HomeBehavior
import com.zfl.launcher.settings.HomeBinding
import com.zfl.launcher.settings.HomeReturn
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.search.search

/** Settings for real commands; the app chooser uses the current local repository. */
@Composable
fun HomeBehaviorScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val snapshot by vm.repository.snapshot.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val behavior = settings.homeBehavior
    var binding by rememberSaveable { mutableStateOf<HomeBinding?>(null) }
    var choosingApp by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        when {
            choosingApp -> choosingApp = false
            binding != null -> binding = null
            else -> onBack()
        }
    }
    BackHandler(binding != null, onBack = back)
    fun assign(action: HomeAction) {
        val selected = binding ?: return
        vm.settings.update { it.copy(homeBehavior = it.homeBehavior.assign(selected, action)) }
        choosingApp = false
        binding = null
    }
    if (choosingApp) {
        HomeAppChooser(snapshot.apps.filter { profileId(it.user) !in snapshot.unavailableUsers }, back) { app ->
            assign(HomeAction.OpenApp(AppTarget(app.component.flattenToString(), profileId(app.user))))
        }
        return
    }
    val selected = binding
    if (selected != null) {
        val options = listOf(HomeAction.None, HomeAction.Search, HomeAction.AppLibrary, HomeAction.Settings) +
            if (selected == HomeBinding.RepeatHome) listOf(HomeAction.MainPage) else emptyList()
        SettingsPage(title = stringResource(selected.title), onBack = back) {
            item {
                SettingsCard {
                    options.forEach { action ->
                        if (action != HomeAction.AppLibrary || settings.appLibrary) {
                            CheckRow(actionTitle(action, snapshot.apps, snapshot.unavailableUsers, settings.appLibrary), behavior[selected] == action) { assign(action) }
                        }
                    }
                    NavRow(Icons.Rounded.Apps, stringResource(R.string.gesture_open_app), onClick = { choosingApp = true })
                }
            }
        }
        return
    }
    SettingsPage(
        title = stringResource(R.string.gestures_home),
        onBack = back,
        action = { ResetAction { vm.settings.update { it.copy(homeBehavior = HomeBehavior()) } } },
    ) {
        item { SectionHeader(stringResource(R.string.gesture_assignments)) }
        item {
            SettingsCard {
                HomeBinding.entries.forEach { item ->
                    NavRow(
                        Icons.Rounded.Apps,
                        stringResource(item.title),
                        onClick = { binding = item },
                        value = actionTitle(behavior[item], snapshot.apps, snapshot.unavailableUsers, settings.appLibrary),
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.gesture_home_behavior)) }
        item {
            SettingsCard {
                HomeReturn.entries.forEach { policy ->
                    CheckRow(
                        stringResource(if (policy == HomeReturn.Stay) R.string.home_return_stay else R.string.home_return_main),
                        behavior.returnPolicy == policy,
                    ) { vm.settings.update { it.copy(homeBehavior = it.homeBehavior.copy(returnPolicy = policy)) } }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.home_main_page)) }
        item {
            SettingsCard {
                val count = layout?.pages?.size?.coerceAtLeast(1) ?: vm.homePreview(snapshot.apps, settings).pages.size.coerceAtLeast(1)
                repeat(count) { page ->
                    CheckRow(stringResource(R.string.home_page_number, page + 1), behavior.mainPage.coerceIn(0, count - 1) == page) {
                        vm.settings.update { it.copy(homeBehavior = it.homeBehavior.copy(mainPage = page)) }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.home_section_pages)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.home_show_today), behavior.showToday, onCheckedChange = { value ->
                    vm.settings.update { it.copy(homeBehavior = it.homeBehavior.copy(showToday = value)) }
                })
                SwitchRow(stringResource(R.string.home_haptics), behavior.haptics, onCheckedChange = { value ->
                    vm.settings.update { it.copy(homeBehavior = it.homeBehavior.copy(haptics = value)) }
                })
            }
        }
    }
}

@Composable
private fun HomeAppChooser(apps: List<AppInfo>, onBack: () -> Unit, onSelect: (AppInfo) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    SettingsPage(title = stringResource(R.string.gesture_open_app), onBack = onBack) {
        item { SettingsCard { SettingsTextField(query, { query = it }, stringResource(R.string.search), leading = Icons.Rounded.Search) } }
        items(search(apps, query), key = { it.key }) { app ->
            SettingsCard {
                SettingsRow(onClick = { onSelect(app) }) {
                    AppIconImage(app, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        RowTitle(app.label)
                        Text(stringResource(R.string.gesture_profile, profileId(app.user)), color = SettingsStyle.SecondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun actionTitle(action: HomeAction, apps: List<AppInfo>, unavailable: Set<String>, library: Boolean): String = when (action) {
    HomeAction.None -> stringResource(R.string.gesture_none)
    HomeAction.Search -> stringResource(R.string.search)
    HomeAction.AppLibrary -> if (library) stringResource(R.string.library_title) else stringResource(R.string.gesture_library_unavailable)
    HomeAction.Settings -> stringResource(R.string.settings_title)
    HomeAction.MainPage -> stringResource(R.string.home_main_page)
    is HomeAction.OpenApp -> {
        val app = apps.firstOrNull { it.key == action.target.key }
        if (app == null || action.target.profile in unavailable) {
            stringResource(R.string.gesture_app_unavailable, action.target.component.substringBefore('/'), action.target.profile)
        } else stringResource(R.string.gesture_app_name, app.label, action.target.profile)
    }
}

private val HomeBinding.title: Int get() = when (this) {
    HomeBinding.SwipeUp -> R.string.gesture_swipe_up
    HomeBinding.SwipeDown -> R.string.gesture_swipe_down
    HomeBinding.EmptyDoubleTap -> R.string.gesture_double_tap
    HomeBinding.RepeatHome -> R.string.gesture_repeat_home
}
