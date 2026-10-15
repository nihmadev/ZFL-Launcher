package com.zfl.launcher.ui.home

import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.GRID_COLUMNS
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.packCells
import com.zfl.launcher.data.span

/** Side margin and the gap between widgets on the Today page, measured from iOS27L. */
private val TodayMargin = 33.dp
private val TodayGap = 36.dp

/**
 * The Today page, left of the first home page: a scrolling stack of widgets, small ones
 * side by side, without labels (as in iOS27L). Long-press on empty space enters edit mode.
 */
@Composable
internal fun TodayPage(
    widgets: List<PageWidgetEntry>,
    editMode: Boolean,
    liftedId: String?,
    callbacks: WidgetCallbacks,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(editMode) {
                detectTapGestures(
                    onLongPress = { if (!editMode) onEnterEdit() },
                    onTap = { if (editMode) onExitEdit() },
                )
            },
    ) {
        val width = maxWidth - TodayMargin * 2
        // Two small widgets and the gap between them fill the width; a cell is half a small widget.
        val cellWidth = (width + TodayGap) / GRID_COLUMNS
        val small = cellWidth * 2 - TodayGap
        val rowHeight = (small + TodayGap) / 2
        val spans = widgets.map { it.span }
        val totalRows = spans.sumOf { it.rows }
        val cells = remember(spans) { packCells(spans, GRID_COLUMNS, totalRows.coerceAtLeast(1)) }
        val usedRows = widgets.indices.maxOfOrNull { i -> (cells[i] ?: 0) / GRID_COLUMNS + spans[i].rows } ?: 0

        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Each slot carries half a gap on every side, so the outer margin loses half a gap.
                .padding(horizontal = TodayMargin - TodayGap / 2, vertical = 16.dp)
                .height(rowHeight * usedRows),
        ) {
            widgets.forEachIndexed { index, widget ->
                val cell = cells[index] ?: return@forEachIndexed
                key(widget.id) {
                    HomeWidget(
                        entry = widget,
                        editMode = editMode,
                        jiggleSeed = 500 + index,
                        callbacks = callbacks,
                        withLabel = false,
                        draggable = false,
                        slotInset = TodayGap / 2,
                        hidden = widget.id == liftedId,
                        modifier = Modifier
                            .offset(x = cellWidth * (cell % GRID_COLUMNS), y = rowHeight * (cell / GRID_COLUMNS))
                            .size(cellWidth * spans[index].columns, rowHeight * spans[index].rows),
                    )
                }
            }
        }
    }
}
