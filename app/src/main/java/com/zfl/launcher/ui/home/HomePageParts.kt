package com.zfl.launcher.ui.home

import com.zfl.launcher.ui.widgets.PageWidgetBody
import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import com.zfl.launcher.data.Span
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.SearchSwipe
import com.zfl.launcher.ui.shape.IconShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.ui.widgets.WidgetBody
import androidx.compose.ui.unit.DpSize
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.itemAtCell
import com.zfl.launcher.data.packCells
import com.zfl.launcher.data.span
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalHapticFeedback
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.Motion

/** One home screen page; long-press on empty space enters edit mode, a tap leaves it. */
@Composable
internal fun PageContent(
    page: Int,
    entries: List<HomeEntry>,
    rows: Int,
    cells: Map<String, Int>,
    isCurrent: Boolean,
    editMode: Boolean,
    drag: DragController,
    liftedId: String?,
    callbacks: HomeIconCallbacks,
    widgetCallbacks: WidgetCallbacks,
    showGlassPrompt: Boolean,
    onGlassPrompt: () -> Unit,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    searchSwipe: SearchSwipe,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    val currentEntries by rememberUpdatedState(entries)
    val currentCells by rememberUpdatedState(cells)
    val currentOnSearch by rememberUpdatedState(onSearch)
    // Icons handle their own gestures; the page only reacts to touches on empty space.
    fun isOnIcon(local: Offset): Boolean {
        val grid = geometry ?: return false
        val p = origin + local
        val inside = p.x >= grid.origin.x && p.y >= grid.origin.y &&
            p.x < grid.origin.x + grid.cellWidth * grid.columns && p.y < grid.origin.y + grid.cellHeight * grid.rows
        if (!inside) return false
        val spans = currentEntries.map { it.span }
        return itemAtCell(spans, packCells(spans, grid.columns, grid.rows, currentEntries.map { currentCells[it.id] }), grid.cellAt(p), grid.columns) != null
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(editMode) {
                detectTapGestures(
                    onLongPress = {
                        if (!editMode && !isOnIcon(it)) {
                            haptic.performHapticFeedback(Haptics.Lift)
                            onEnterEdit()
                        }
                    },
                    onTap = { if (editMode && !isOnIcon(it)) onExitEdit() },
                )
            }
            .pointerInput(editMode, searchSwipe) {
                if (editMode || searchSwipe == SearchSwipe.Disabled) return@pointerInput
                val threshold = with(density) { Tokens.SearchSwipeThreshold.toPx() }
                var total = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; triggered = false },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        if (!triggered && searchSwipe.isTriggered(total, threshold)) {
                            triggered = true
                            change.consume()
                            haptic.performHapticFeedback(Haptics.Threshold)
                            currentOnSearch()
                        }
                    },
                )
            },
    ) {
        IconGrid(
            entries = entries,
            rows = rows,
            cells = cells,
            header = { if (showGlassPrompt) GlassPrompt(onGlassPrompt) },
            marker = drag.pageMarker(page),
            markerSquare = (drag.session?.entry as? PageWidgetEntry)?.isSquare == true,
            onGeometry = {
                geometry = it
                if (isCurrent) drag.pageGrid = it
            },
            dropOrigin = drag::takeDropOrigin,
        ) { entry ->
            if (entry is PageWidgetEntry) {
                HomeWidget(
                    entry = entry,
                    editMode = editMode,
                    jiggleSeed = page * 100 + entries.indexOf(entry),
                    callbacks = widgetCallbacks,
                    // No name under widgets: the widget itself says what it is.
                    withLabel = false,
                    draggable = true,
                    hidden = entry.id == liftedId,
                    mergeTarget = drag.mergeArmed && drag.mergeCandidate == entry.id,
                )
            } else {
                HomeIcon(
                    entry = entry,
                    showLabel = true,
                    editMode = editMode,
                    jiggleSeed = page * 100 + entries.indexOf(entry),
                    callbacks = callbacks,
                    hidden = entry.id == liftedId,
                    mergeTarget = drag.mergeArmed && drag.mergeCandidate == entry.id,
                )
            }
        }
    }
}

/**
 * Faint outline where the dragged item will land: an icon-sized tile, or a widget-sized
 * card for widgets. Fades in so it doesn't flicker while the target is still moving.
 */
@Composable
internal fun DropMarker(span: Span, modifier: Modifier = Modifier, square: Boolean = false) {
    val settings = LocalSettingsState.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(Motion.MARKER_FADE_MS)) }
    val fade = Modifier.graphicsLayer { this.alpha = alpha.value }
    if (span == Span.One) {
        val shape = IconShape(settings.iconCorner)
        Box(modifier, contentAlignment = Alignment.TopCenter) {
            Box(fade.size(settings.iconSize).background(DropMarkerFill, shape).border(1.dp, DropMarkerStroke, shape))
        }
    } else {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val body = widgetBodySize(DpSize(maxWidth, maxHeight), span, square, settings.iconSize, withLabel = false)
            val shape = ContinuousRoundedShape(22.dp)
            Box(fade.size(body).background(DropMarkerFill, shape).border(1.dp, DropMarkerStroke, shape))
        }
    }
}

private val DropMarkerFill = Color.White.copy(alpha = 0.10f)
private val DropMarkerStroke = Color.White.copy(alpha = 0.22f)

/** The icon under the finger while dragging: slightly bigger, floating above everything. */
@Composable
internal fun DraggedIcon(drag: DragController) {
    val session = drag.session?.takeIf { it.active } ?: return
    Box(
        Modifier
            .offset {
                val topLeft = session.pointer - session.touchOffset
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.95f
            },
    ) {
        when (val entry = session.entry) {
            is AppEntry -> AppIconImage(entry.app)
            is FolderEntry -> FolderIcon(entry)
            is PageWidgetEntry -> {
                val size = with(LocalDensity.current) { DpSize(session.itemSize.width.toDp(), session.itemSize.height.toDp()) }
                PageWidgetBody(entry, Modifier.size(size), live = false)
            }
            is PlaceholderEntry -> Unit
        }
    }
}

/** Card asking to enable the glass effect. Shown on page 1 until wallpaper access is granted. */
@Composable
internal fun GlassPrompt(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .glass(ContinuousRoundedShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(stringResource(R.string.glass_prompt_title), color = Tokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.glass_prompt_body),
                color = Tokens.TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

/** Pill above the dock: "Search" on the first page, page dots while swiping or on other pages. */
@Composable
internal fun PagePill(
    pager: PagerState,
    firstPage: Int,
    pageCount: Int,
    editMode: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = pager.currentPage - firstPage
    // Dots in edit mode, while swiping and away from the first page; "Search" otherwise.
    val showDots = pageCount > 1 && (editMode || pager.isScrollInProgress || current != 0)
    AnimatedContent(
        targetState = showDots,
        transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) },
        modifier = modifier
            .height(Tokens.PillHeight)
            .glass(ContinuousRoundedShape(Tokens.PillHeight / 2), profile = GlassProfile.Button)
            .clickable(interactionSource = null, indication = null, enabled = !showDots, onClick = onSearch),
        label = "pill",
    ) { dots ->
        Row(
            Modifier
                .height(Tokens.PillHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dots) {
                repeat(pageCount) { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (i == current) Color.White else Color.White.copy(alpha = 0.35f),
                                CircleShape,
                            ),
                    )
                }
            } else {
                Icon(Icons.Filled.Search, null, tint = Tokens.TextPrimary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.search), color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
