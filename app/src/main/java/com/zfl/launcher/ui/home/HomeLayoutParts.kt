package com.zfl.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.packCells
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.span
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.shape.IconShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt
import com.zfl.launcher.ui.theme.Motion

/** Tallest a grid cell may get, so icons don't drift apart on a sparse 4×5 grid. */
private val MaxCellHeight = 112.dp

/** Smallest space between two dock icons. */
private val DockIconGap = 8.dp

/** Spring used when icons slide to their new slots. */
private val SlotSpring = Motion.settle<IntOffset>()

/**
 * Lays out [entries] as a [columns] × [rows] grid, filled top-left first (widgets span
 * several cells, see [packCells]). Each item sits in
 * its slot through an animated offset, so when the order changes (drag and drop) icons
 * slide to their new place instead of jumping. Shared by home pages, open folders and the
 * settings preview.
 *
 * @param header optional content above the grid (e.g. the glass access prompt).
 * @param onGeometry receives the window-space grid layout (used for drag and drop hit tests).
 * @param dropOrigin returns where a just-dropped item was released, to animate it from there.
 */
@Composable
fun IconGrid(
    entries: List<HomeEntry>,
    rows: Int,
    modifier: Modifier = Modifier,
    columns: Int = Tokens.GridColumns,
    topPadding: Dp = 12.dp,
    header: @Composable () -> Unit = {},
    onGeometry: ((GridGeometry) -> Unit)? = null,
    dropOrigin: (String) -> Offset? = { null },
    marker: Pair<Int, Span>? = null,
    markerSquare: Boolean = false,
    cells: Map<String, Int> = emptyMap(),
    item: @Composable (HomeEntry) -> Unit,
) {
    val density = LocalDensity.current
    val iconSize = LocalSettingsState.current.iconSize
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(horizontal = Tokens.GridSidePadding)
            .padding(top = topPadding),
    ) {
        val cellWidth = maxWidth / columns
        // Bigger icons need taller cells; the cap moves with them.
        val cellHeight = min(maxHeight / rows, MaxCellHeight + (iconSize - Tokens.IconSize))
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }
        Column {
            header()
            var originInWindow by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cellHeight * rows)
                    .onGloballyPositioned { coordinates ->
                        originInWindow = coordinates.positionInWindow()
                        onGeometry?.invoke(
                            GridGeometry(
                                origin = originInWindow,
                                cellWidth = cellWidthPx,
                                cellHeight = cellHeightPx,
                                columns = columns,
                                rows = rows,
                                iconSize = with(density) { iconSize.toPx() },
                            ),
                        )
                    },
            ) {
                // Widgets take several cells; apps flow around them (see packCells).
                marker?.let { (cell, span) ->
                    key("dropMarker") {
                        MarkerSlot(
                            target = IntOffset(((cell % columns) * cellWidthPx).roundToInt(), ((cell / columns) * cellHeightPx).roundToInt()),
                            modifier = Modifier.size(cellWidth * span.columns, cellHeight * span.rows),
                        ) { DropMarker(span, square = markerSquare) }
                    }
                }
                val spans = entries.map { it.span }
                val preferred = entries.map { cells[it.id] }
                val placed = remember(spans, columns, rows, preferred) { packCells(spans, columns, rows, preferred) }
                entries.forEachIndexed { index, entry ->
                    val cell = placed[index] ?: return@forEachIndexed
                    key(entry.id) {
                        val slot = IntOffset(
                            ((cell % columns) * cellWidthPx).roundToInt(),
                            ((cell / columns) * cellHeightPx).roundToInt(),
                        )
                        AnimatedSlot(
                            target = slot,
                            initial = dropOrigin(entry.id)?.let { origin ->
                                // The released icon's top-left → this cell's top-left.
                                val iconInset = (cellWidthPx - with(density) { iconSize.toPx() }) / 2
                                IntOffset((origin.x - originInWindow.x - iconInset).roundToInt(), (origin.y - originInWindow.y).roundToInt())
                            },
                            modifier = Modifier.size(cellWidth * spans[index].columns, cellHeight * spans[index].rows),
                        ) { item(entry) }
                    }
                }
            }
        }
    }
}

/** A child that springs to [target] whenever it changes (starting from [initial] if given). */
@Composable
private fun AnimatedSlot(target: IntOffset, initial: IntOffset?, modifier: Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(initial ?: target, IntOffset.VectorConverter) }
    LaunchedEffect(target) { offset.animateTo(target, SlotSpring) }
    Box(modifier.offset { offset.value }, contentAlignment = Alignment.TopCenter) { content() }
}

/** The drop marker glides from slot to slot instead of jumping; it appears right where it is first shown. */
@Composable
private fun MarkerSlot(target: IntOffset, modifier: Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(target, IntOffset.VectorConverter) }
    LaunchedEffect(target) { offset.animateTo(target, SlotSpring) }
    Box(modifier.offset { offset.value }, contentAlignment = Alignment.TopCenter) { content() }
}

/**
 * The glass dock. Icons are spread evenly and slide when the dock content changes.
 * Its height follows the icon size; [background] = false leaves just the icons.
 *
 * @param onBounds receives the dock's window bounds (drag and drop target area).
 */
@Composable
fun DockBar(
    entries: List<HomeEntry>,
    modifier: Modifier = Modifier,
    background: Boolean = true,
    onBounds: ((Rect) -> Unit)? = null,
    dropOrigin: (String) -> Offset? = { null },
    marker: Pair<Int, Int>? = null,
    item: @Composable (HomeEntry) -> Unit,
) {
    val density = LocalDensity.current
    val settings = LocalSettingsState.current
    val dockHeight = settings.dockHeight
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = Tokens.DockSideMargin, end = Tokens.DockSideMargin, bottom = Tokens.DockBottomMargin)
            .height(dockHeight)
            .then(if (background) Modifier.glass(ContinuousRoundedShape(Tokens.DockRadius)) else Modifier)
            .onGloballyPositioned { onBounds?.invoke(it.boundsInWindow()) },
    ) {
        // Big icons in a 5-slot dock would touch: shrink them to fit, keeping a small gap.
        val iconSize = min(settings.iconSize, maxWidth / settings.dockSlots - DockIconGap)
        val widthPx = with(density) { maxWidth.toPx() }
        val iconPx = with(density) { iconSize.toPx() }
        val topPx = with(density) { ((dockHeight - iconSize) / 2).toPx() }
        var originInWindow by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { originInWindow = it.positionInWindow() }) {
            val slotWidth = widthPx / entries.size.coerceAtLeast(1)
            marker?.let { (slot, count) ->
                val markerSlotWidth = widthPx / count.coerceAtLeast(1)
                MarkerSlot(
                    target = IntOffset((markerSlotWidth * slot + (markerSlotWidth - iconPx) / 2).roundToInt(), topPx.roundToInt()),
                    modifier = Modifier.size(iconSize),
                ) {
                    CompositionLocalProvider(LocalSettingsState provides settings.copy(iconScale = iconSize / Tokens.IconSize)) {
                        DropMarker(Span.One)
                    }
                }
            }
            entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    AnimatedSlot(
                        target = IntOffset((slotWidth * index + (slotWidth - iconPx) / 2).roundToInt(), topPx.roundToInt()),
                        initial = dropOrigin(entry.id)?.let { IntOffset((it.x - originInWindow.x).roundToInt(), (it.y - originInWindow.y).roundToInt()) },
                        modifier = Modifier.size(iconSize),
                    ) {
                        CompositionLocalProvider(LocalSettingsState provides settings.copy(iconScale = iconSize / Tokens.IconSize)) {
                            item(entry)
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS "jiggle": a quick, small rotation back and forth. Neighbors swing in opposite
 * directions with slightly different speeds so the screen looks alive, not mechanical.
 */
fun Modifier.jiggle(active: Boolean, seed: Int): Modifier = composed {
    val amount by animateFloatAsState(if (active) 1f else 0f, Motion.quickFade(), label = "jiggleAmount")
    if (amount == 0f) return@composed this
    val period = remember(seed) { 230 + (seed * 37) % 60 }
    val transition = rememberInfiniteTransition(label = "jiggle")
    val angle by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset((seed * 53) % period),
        ),
        label = "jiggleAngle",
    )
    val direction = if (seed % 2 == 0) 1f else -1f
    graphicsLayer {
        // A fixed angle moves the corners of a big widget card several times farther than
        // an icon's; scale it down so every item's edges travel about the same distance.
        val sizeFactor = (JIGGLE_REFERENCE_SIZE.toPx() / size.maxDimension).coerceAtMost(1f)
        rotationZ = angle * JIGGLE_DEGREES * direction * amount * sizeFactor
    }
}

private const val JIGGLE_DEGREES = 1.8f
/** Items up to this size jiggle at the full angle (an icon with its label). */
private val JIGGLE_REFERENCE_SIZE = 80.dp

/** The round "−" button shown on app icons in edit mode. */
@Composable
fun RemoveBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0xCC3A3A3C))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(2.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * Folder icon: a glass tile showing the first nine apps as tiny icons, like iOS.
 * [highlighted] grows it a little (an app is hovering over it, ready to be added).
 */
@Composable
fun FolderIcon(
    folder: FolderEntry,
    modifier: Modifier = Modifier,
    size: Dp = LocalSettingsState.current.iconSize,
    highlighted: Boolean = false,
) {
    val shape = IconShape(LocalSettingsState.current.iconCorner)
    val scale by animateFloatAsState(if (highlighted) 1.15f else 1f, Motion.bouncy(), label = "folderHover")
    val padding = size * 0.13f
    val gap = size * 0.05f
    val mini = (size - padding * 2 - gap * 2) / 3
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(shape, tint = Color.White.copy(alpha = if (highlighted) 0.32f else 0.2f), profile = GlassProfile.Tile),
    ) {
        folder.apps.take(9).forEachIndexed { index, app ->
            AppIconImage(
                app = app,
                size = mini,
                modifier = Modifier.offset(
                    x = padding + (mini + gap) * (index % 3),
                    y = padding + (mini + gap) * (index / 3),
                ),
            )
        }
    }
}

/** Glass squircle that appears behind an app icon when another app hovers over it (future folder). */
@Composable
fun MergeHalo(visible: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (visible) 1.22f else 0.9f, Motion.bouncy(), label = "halo")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, Motion.quickFade(), label = "haloAlpha")
    if (alpha == 0f) return
    val settings = LocalSettingsState.current
    Box(
        modifier
            .size(settings.iconSize)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .glass(IconShape(settings.iconCorner), tint = Color.White.copy(alpha = 0.3f), profile = GlassProfile.Tile),
    )
}

/** Vertical gap between an icon and its label. */
val LabelGap: Dp = 6.dp

/** Spacer with [LabelGap] height, for consistency between grid, folder and preview. */
@Composable
fun LabelSpacer() = Spacer(Modifier.height(LabelGap))
