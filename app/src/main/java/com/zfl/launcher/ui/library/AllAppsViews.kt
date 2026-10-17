package com.zfl.launcher.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.ui.theme.Tokens

/*
 * The Grid and List views. An app can be in a section at the top (pinned, suggestions,
 * recently added) and in the full list: item keys carry the section ("pinned:", "all:"…), so
 * the two never collide.
 */

/** Every visible app in one grid by name, under the pinned apps and the smart groups. */
@Composable
internal fun AllAppsGrid(
    library: AppLibraryState,
    header: @Composable () -> Unit,
    callbacks: LibraryCallbacks,
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(Tokens.LibraryColumns),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 10.dp)) { header() }
        }
        section("pinned", R.string.library_pinned, library.pinned, callbacks)
        section("suggestions", R.string.library_suggestions, library.suggestions, callbacks)
        section("recent", R.string.library_recently_added, library.recent, callbacks)
        val titled = library.pinned.isNotEmpty() || library.suggestions.isNotEmpty() || library.recent.isNotEmpty()
        section("all", R.string.library_all_apps.takeIf { titled }, library.alphabetical, callbacks)
    }
}

private fun LazyGridScope.section(name: String, title: Int?, apps: List<AppInfo>, callbacks: LibraryCallbacks) {
    if (apps.isEmpty()) return
    if (title != null) {
        item(key = "title:$name", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            SectionTitle(stringResource(title), Modifier.padding(horizontal = 10.dp))
        }
    }
    items(apps, key = { "$name:${it.key}" }, contentType = { "app" }) { app -> LibraryCell(app, callbacks) }
}

/** Every visible app A–Z with letter headers and an index, under the pinned apps. */
@Composable
internal fun AllAppsList(
    library: AppLibraryState,
    header: @Composable () -> Unit,
    callbacks: LibraryCallbacks,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val pinned = library.pinned
    LetterIndexedList(
        sections = library.sections,
        callbacks = callbacks,
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 12.dp, bottom = 24.dp),
        leadingCount = 1 + if (pinned.isEmpty()) 0 else 1 + pinned.size,
        keyPrefix = "all",
    ) {
        item(key = "header") { header() }
        if (pinned.isNotEmpty()) {
            item(key = "title:pinned", contentType = "title") { SectionTitle(stringResource(R.string.library_pinned)) }
            items(pinned, key = { "pinned:${it.key}" }, contentType = { "app" }) { app -> AppListRow(app, callbacks) }
        }
    }
}
