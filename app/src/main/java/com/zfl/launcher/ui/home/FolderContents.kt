package com.zfl.launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.GridMargins
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.abs

/** The same grid used on Home, with each icon unfolding from its miniature in the tile. */
@Composable
internal fun FolderContents(
    entry: FolderEntry,
    isOpen: Boolean,
    editMode: Boolean,
    panelBounds: Rect,
    progress: State<Float>,
    drag: DragController,
    callbacks: HomeIconCallbacks,
) {
    val apps = remember(entry, drag.target, drag.draggedId) { drag.previewFolder(entry) }
    val pages = remember(apps) { apps.chunked(FOLDER_PAGE_SIZE).ifEmpty { listOf(emptyList()) } }
    val pager = rememberPagerState { pages.size }
    val currentPage = pager.currentPage
    val settings = LocalSettingsState.current
    val metrics = rememberAreaMetrics(settings, IconArea.Folder)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val margins = GridMargins(side = Tokens.GridSidePadding, top = Tokens.FolderGridTop, bottom = 0.dp)
    val openNow by rememberUpdatedState(isOpen)
    val currentCallbacks by rememberUpdatedState(callbacks)
    // Closing icons remain visible but cannot launch an app or start a drag.
    val guarded = remember {
        object : HomeIconCallbacks {
            private fun ready(): Boolean = openNow && progress.value >= 1f
            override fun onTap(entry: HomeEntry, iconBounds: Rect) {
                if (ready()) currentCallbacks.onTap(entry, iconBounds)
            }
            override fun onLongPress(entry: HomeEntry, iconBounds: Rect, pointer: Offset) {
                if (ready()) currentCallbacks.onLongPress(entry, iconBounds, pointer)
            }
            override fun onDragStart(entry: HomeEntry, iconBounds: Rect, pointer: Offset) {
                if (ready()) currentCallbacks.onDragStart(entry, iconBounds, pointer)
            }
            override fun onRemove(entry: AppEntry) {
                if (ready()) currentCallbacks.onRemove(entry)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val frame = remember(maxWidth, maxHeight, metrics, margins) {
                gridFrame(DpSize(maxWidth, maxHeight), GridSpec.Folder, margins, GridAlignment.Center, metrics)
            }
            // Use the final, untransformed frame for hit tests. The outer panel and the icon
            // layers animate independently; sampling their moving bounds would drift the grid.
            val geometry = with(density) {
                GridGeometry(
                    origin = panelBounds.topLeft + Offset(frame.side.toPx(), frame.top.toPx()),
                    cellWidth = frame.cellWidth.toPx(),
                    cellHeight = frame.cellHeight.toPx(),
                    grid = GridSpec.Folder,
                    iconSize = frame.iconSize.toPx(),
                    rtl = rtl,
                )
            }
            SideEffect {
                if (isOpen) {
                    drag.folderPage = currentPage
                    drag.folderGrid = geometry
                }
            }
            HorizontalPager(
                state = pager,
                userScrollEnabled = isOpen && !drag.isDragging,
                key = { it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val entries = pages.getOrElse(page) { emptyList() }
                IconGrid(
                    entries = entries,
                    grid = GridSpec.Folder,
                    metrics = metrics,
                    margins = margins,
                    alignment = GridAlignment.Center,
                ) { app, slot ->
                    val index = entries.indexOf(app)
                    val miniature = remember(index, rtl) { folderPreviewCell(index, rtl) }
                    val columns = GridSpec.Folder.columns
                    val column = if (rtl) columns - 1 - index % columns else index % columns
                    val centerX = geometry.cellWidth * (column + 0.5f) + with(density) { frame.side.toPx() }
                    val top = geometry.cellHeight * (index / columns) + with(density) { frame.top.toPx() }
                    val startX = panelBounds.width * (miniature.left + miniature.size / 2) - centerX
                    val startY = panelBounds.height * miniature.top - top
                    val startScaleX = panelBounds.width * miniature.size / geometry.iconSize.coerceAtLeast(1f)
                    val startScaleY = panelBounds.height * miniature.size / geometry.iconSize.coerceAtLeast(1f)
                    HomeIcon(
                        entry = app,
                        slot = slot,
                        editMode = editMode && isOpen,
                        jiggleSeed = page * FOLDER_PAGE_SIZE + index,
                        callbacks = guarded,
                        modifier = Modifier.graphicsLayer {
                            val p = progress.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationX = lerp(startX, 0f, p)
                            translationY = lerp(startY, 0f, p)
                            scaleX = lerp(startScaleX, 1f, p)
                            scaleY = lerp(startScaleY, 1f, p)
                            // Only the first page is represented by the closed folder's tile.
                            alpha = if (page == 0) 1f else folderDetailAlpha(p)
                        },
                        labelModifier = Modifier.graphicsLayer { alpha = folderDetailAlpha(progress.value) },
                    )
                }
            }
        }
        if (pages.size > 1 && settings.folders.showPages) {
            FolderPageIndicator(
                count = pages.size,
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                alpha = { folderDetailAlpha(progress.value) },
            )
        }
    }
}

/** Shared with the read-only settings preview; scrolling is read only while drawing. */
@Composable
internal fun FolderPageIndicator(count: Int, position: () -> Float, alpha: () -> Float = { 1f }): Unit {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(Tokens.FolderPageHeight)) {
        val dot = Tokens.FolderPageDotSize.toPx()
        val step = dot + Tokens.FolderPageDotGap.toPx()
        val current = position()
        repeat(count) { page ->
            val selected = (1f - abs(current - page)).coerceIn(0f, 1f)
            val visual = if (rtl) count - 1 - page else page
            drawCircle(
                color = Tokens.TextPrimary,
                radius = dot / 2,
                center = Offset(center.x + (visual - (count - 1) / 2f) * step, center.y),
                alpha = lerp(Tokens.FolderPageInactiveAlpha, 1f, selected) * alpha(),
            )
        }
    }
}
