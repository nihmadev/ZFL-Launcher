package com.zfl.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.LibraryView
import com.zfl.launcher.data.NewAppPlacement
import com.zfl.launcher.data.movePinned
import com.zfl.launcher.data.pin
import com.zfl.launcher.data.unpin
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.library.labelRes
import com.zfl.launcher.ui.search.search

/** Settings › App Library: the view, smart groups, pins, categories and where new apps go. */
@Composable
fun LibrarySettingsScreen(vm: SettingsViewModel, navigate: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val config = settings.library

    SettingsPage(title = stringResource(R.string.library_title), onBack = onBack) {
        item { SectionHeader(stringResource(R.string.library_section_view)) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SegmentedControl(
                        options = LibraryView.entries.map { stringResource(it.labelRes) },
                        selectedIndex = config.view.ordinal,
                        onSelect = { index -> vm.editLibrary { it.copy(view = LibraryView.entries[index]) } },
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.library_section_smart)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.library_suggestions), config.showSuggestions, onCheckedChange = { value ->
                    vm.editLibrary { it.copy(showSuggestions = value) }
                })
                SwitchRow(stringResource(R.string.library_recently_added), config.showRecent, onCheckedChange = { value ->
                    vm.editLibrary { it.copy(showRecent = value) }
                })
            }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.PushPin,
                    stringResource(R.string.library_pinned),
                    onClick = { navigate(SettingsRoute.LibraryPinned) },
                    value = library?.pinned?.size?.takeIf { it > 0 }?.toString(),
                    tileColor = SettingsStyle.Tile.Orange,
                )
                NavRow(
                    Icons.Rounded.Folder,
                    stringResource(R.string.library_view_categories),
                    onClick = { navigate(SettingsRoute.LibraryCategories) },
                    tileColor = SettingsStyle.Tile.Blue,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.library_section_new_apps)) }
        item {
            SettingsCard {
                NewAppPlacement.entries.forEach { placement ->
                    CheckRow(stringResource(placement.labelRes), config.newApps == placement) {
                        vm.editLibrary { it.copy(newApps = placement) }
                    }
                }
            }
        }
        if (!settings.appLibrary) item { SectionFooter(stringResource(R.string.library_page_off)) }
    }
}

private val NewAppPlacement.labelRes: Int
    get() = when (this) {
        NewAppPlacement.HomeAndLibrary -> R.string.library_new_home
        NewAppPlacement.LibraryOnly -> R.string.library_new_library_only
    }

/**
 * Settings › App Library › Pinned: the pinned apps in their order (drag to reorder, minus to
 * unpin), then every other visible app to pin, with a search field.
 */
@Composable
fun LibraryPinnedScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val pinned = library?.pinned.orEmpty()
    val others = remember(apps, pinned, query) {
        val pinnedKeys = pinned.mapTo(HashSet()) { it.key }
        search(apps.filter { it.key !in pinnedKeys }, query)
    }
    val unpin = stringResource(R.string.menu_library_unpin)

    SettingsPage(title = stringResource(R.string.library_pinned), onBack = onBack) {
        if (pinned.isNotEmpty()) {
            item {
                SettingsCard {
                    ReorderRows(
                        items = pinned,
                        key = { it.key },
                        onMove = { from, to ->
                            val keys = pinned.map { it.key }
                            vm.editLibrary { it.movePinned(keys[from], to, keys) }
                        },
                    ) { app, handle ->
                        ReorderRow(handle) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RemoveButton(unpin) { vm.editLibrary { it.unpin(app.key) } }
                                Spacer(Modifier.width(6.dp))
                                AppIconImage(app, size = 34.dp)
                                Spacer(Modifier.width(14.dp))
                                RowTitle(app.label)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.library_add_apps)) }
        item {
            SettingsCard {
                SettingsTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_hint),
                    leading = Icons.Rounded.Search,
                    imeAction = ImeAction.Search,
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        items(others, key = { it.key }) { app ->
            AppCapsule(app, app.label, onClick = { vm.editLibrary { it.pin(app.key) } }) { PickMark(selected = false) }
        }
    }
}
