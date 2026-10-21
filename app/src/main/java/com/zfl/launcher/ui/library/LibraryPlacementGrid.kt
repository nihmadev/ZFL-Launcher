package com.zfl.launcher.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.zfl.launcher.ui.icon.badgeOverhang
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.ui.home.DragController
import com.zfl.launcher.ui.home.DropMarker
import com.zfl.launcher.ui.theme.Tokens

/** Full library grid: a real gap follows the selected slot and commits its next neighbor. */
@Composable
internal fun LibraryPlacementGrid(
    apps: List<AppInfo>,
    header: (@Composable () -> Unit)?,
    callbacks: LibraryCallbacks,
    state: LazyGridState,
    drag: DragController?,
    modifier: Modifier = Modifier,
    groupId: String? = null,
) {
    val picked = (drag?.session?.takeIf { it.active }?.entry as? AppEntry)?.app
    val rest = remember(apps, picked?.key) { apps.filter { it.key != picked?.key } }
    val position = (drag?.target as? DropPosition.Library)?.takeIf { it.groupId == groupId }
    val gap = position?.beforeKey?.let { key -> rest.indexOfFirst { it.key == key }.takeIf { it >= 0 } } ?: rest.size
    val slots = remember(rest, picked, gap) {
        rest.map<AppInfo, AppInfo?> { it }.toMutableList().apply { if (picked != null) add(gap, null) }
    }
    val currentRest by rememberUpdatedState(rest)
    val firstCell = if (header == null) 0 else 1
    val bounds = remember { WindowBounds() }
    val owner = remember { Any() }

    DisposableEffect(drag, state, firstCell, groupId) {
        val hitTest: (Offset) -> DropPosition.Library? = { point ->
            val rect = bounds.current()
            val local = point - rect.topLeft
            val info = state.layoutInfo
            val headerBottom = if (firstCell == 0) null else info.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?.let { it.offset.y + it.size.height }
            if (!rect.contains(point) || (headerBottom != null && local.y < headerBottom)) null
            else {
                val closest = info.visibleItemsInfo.filter { it.index >= firstCell }.minByOrNull {
                    val dx = local.x - it.offset.x - it.size.width / 2f
                    val dy = local.y - it.offset.y - it.size.height / 2f
                    dx * dx + dy * dy
                }
                closest?.let {
                    val index = (it.index - firstCell).coerceIn(0, currentRest.size)
                    DropPosition.Library(currentRest.getOrNull(index)?.key, groupId)
                }
            }
        }
        if (drag != null) drag.libraryTargets[owner] = hitTest
        onDispose { drag?.libraryTargets?.remove(owner) }
    }
    LaunchedEffect(drag, state) {
        snapshotFlow { state.layoutInfo }.collect { drag?.refreshTarget() }
    }
    LibraryDragScroll(drag, state, bounds, owner)
    LazyVerticalGrid(
        columns = GridCells.Fixed(Tokens.LibraryColumns),
        state = state,
        modifier = modifier.fillMaxSize().then(bounds.modifier).onGloballyPositioned { drag?.refreshTarget() },
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
            top = if (header == null) badgeOverhang(Tokens.IconSize) else 12.dp, bottom = if (groupId == null) 24.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = picked == null,
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 10.dp)) { header() }
        }
        items(slots.size, key = { index -> slots[index]?.key ?: "library-drop-slot" }) { index ->
            val app = slots[index]
            if (app != null) LibraryCell(app, callbacks)
            else if (picked != null) Box(contentAlignment = Alignment.TopCenter) {
                Box(Modifier.graphicsLayer { alpha = 0f }) { LibraryCell(picked, callbacks) }
                if (position != null) DropMarker(Tokens.IconSize, Modifier.size(Tokens.IconSize))
            }
        }
    }
}
