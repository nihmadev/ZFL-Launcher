package com.zfl.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.CategoryEdit
import com.zfl.launcher.data.GroupKind
import com.zfl.launcher.data.MAX_CATEGORY_NAME
import com.zfl.launcher.data.NameProblem
import com.zfl.launcher.data.assign
import com.zfl.launcher.data.deleteCategory
import com.zfl.launcher.data.moveGroup
import com.zfl.launcher.data.resetGroupOrder
import com.zfl.launcher.ui.search.search

/**
 * Settings › App Library › Categories: every category in the order of the App Library (drag
 * to reorder), user categories open their editor, and "New Category". The order follows the
 * number of apps until the user arranges it; Reset goes back to that.
 */
@Composable
fun LibraryCategoriesScreen(vm: SettingsViewModel, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val groups = library?.allGroups.orEmpty()

    SettingsPage(
        title = stringResource(R.string.library_view_categories),
        onBack = onBack,
        action = { if (settings.library.groupOrder.isNotEmpty()) ResetAction { vm.editLibrary { it.resetGroupOrder() } } },
    ) {
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Add, stringResource(R.string.library_new_category), onClick = { onEdit(null) }, tileColor = SettingsStyle.Tile.Green)
            }
        }
        item { SectionHeader(stringResource(R.string.library_section_order)) }
        item {
            SettingsCard {
                ReorderRows(
                    items = groups,
                    key = { it.id },
                    onMove = { from, to -> vm.editLibrary { it.moveGroup(groups.map { group -> group.id }, from, to) } },
                ) { group, handle ->
                    val user = group.kind == GroupKind.User
                    SettingsRow(onClick = if (user) ({ onEdit(group.id) }) else null) {
                        Column(Modifier.weight(1f)) {
                            RowTitle(group.title)
                            Text(
                                pluralStringResource(R.plurals.library_app_count, group.size, group.size),
                                color = SettingsStyle.SecondaryText,
                                fontSize = SettingsStyle.SubtitleSize,
                            )
                        }
                        if (user) Chevron()
                        handle()
                    }
                }
            }
        }
    }
}

/**
 * The editor of one user category: its name, the apps in it and every other app to add, with
 * a search field. [id] null is a new category, created when a valid name is entered. Taking
 * an app out returns it to its automatic category; deleting the category does that for all.
 */
@Composable
fun CategoryEditorScreen(vm: SettingsViewModel, id: String?, onCreated: (String) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val category = id?.let(settings.library::category)
    // A category deleted elsewhere (or a stale saved id) leaves the editor.
    if (id != null && category == null) {
        LaunchedEffect(id) { onBack() }
        return
    }
    var name by rememberSaveable(id) { mutableStateOf(category?.name.orEmpty()) }
    var problem by remember(id) { mutableStateOf<NameProblem?>(null) }
    var query by rememberSaveable(id) { mutableStateOf("") }
    var confirmDelete by remember(id) { mutableStateOf(false) }

    fun saveName() {
        val edit = if (id == null) vm.createCategory(name) else vm.renameCategory(id, name)
        problem = (edit as? CategoryEdit.Refused)?.problem
        if (edit is CategoryEdit.Done && id == null) onCreated(edit.id)
    }

    val groupOf = library?.groupOf.orEmpty()
    val matches = remember(apps, query) { search(apps, query) }
    val (inside, outside) = matches.partition { id != null && groupOf[it.key] == id }

    SettingsPage(title = category?.name ?: stringResource(R.string.library_new_category), onBack = onBack) {
        item {
            SettingsCard {
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_CATEGORY_NAME); problem = null },
                    placeholder = stringResource(R.string.library_category_name),
                    onDone = ::saveName,
                    focusOnStart = id == null,
                )
            }
        }
        problem?.let { item { SectionFooter(stringResource(it.messageRes)) } }
        if (id == null) return@SettingsPage
        item { Spacer(Modifier.height(16.dp)) }
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
        if (inside.isNotEmpty()) item { SectionHeader(stringResource(R.string.library_in_category)) }
        items(inside, key = { "in:${it.key}" }) { app ->
            AppCapsule(app, app.label, onClick = { vm.editLibrary { it.assign(app.key, null) } }) { PickMark(selected = true) }
        }
        item { SectionHeader(stringResource(R.string.library_other_apps)) }
        items(outside, key = { "out:${it.key}" }) { app ->
            val current = groupOf[app.key]?.let { library?.summary(it)?.title }
            AppCapsule(app, app.label, onClick = { vm.editLibrary { it.assign(app.key, id) } }, subtitle = current) { PickMark(selected = false) }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                SettingsRow(onClick = {
                    // The editor leaves by itself once the category is gone (see above).
                    if (confirmDelete) vm.editLibrary { it.deleteCategory(id) } else {
                        confirmDelete = true
                    }
                }) {
                    RowTitle(
                        stringResource(if (confirmDelete) R.string.library_delete_confirm else R.string.library_delete_category),
                        color = SettingsStyle.Destructive,
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        if (confirmDelete) item { SectionFooter(stringResource(R.string.library_delete_footer)) }
    }

    // Saves a changed name when leaving the editor, so Back never loses it.
    val latestName by rememberUpdatedState(name)
    DisposableEffect(id) {
        onDispose { if (id != null) vm.renameCategory(id, latestName) }
    }
}

private val NameProblem.messageRes: Int
    get() = when (this) {
        NameProblem.Empty -> R.string.library_name_empty
        NameProblem.Duplicate -> R.string.library_name_duplicate
        NameProblem.TooMany -> R.string.library_name_limit
    }
