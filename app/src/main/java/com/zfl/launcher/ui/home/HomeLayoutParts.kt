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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.GridMargins
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.DpSize
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.packCells
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.span
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.FolderGlass
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconBadge
import com.zfl.launcher.ui.icon.groupBadgeModifier
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.shape.IconShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt
import com.zfl.launcher.ui.theme.Motion

/** Spring used when icons slide to their new slots. */
internal val SlotSpring = Motion.settle<IntOffset>()

/**
 * Lays out [entries] on [grid], filled top-left first (widgets span several cells, see
 * [packCells]), inside the area this composable gets, with [gridFrame] deciding margins,
 * cell size and vertical position. Each item sits in its slot through an animated offset,
 * so when the order changes (drag and drop) icons slide to their new place instead of
 * jumping. Shared by home pages, open folders and the settings preview.
 *
 * @param header optional content above the grid (e.g. the glass access prompt); its height
 *   is taken out of the grid's area.
 * @param onGeometry receives the window-space grid layout (used for drag and drop hit tests).
 * @param dropOrigin returns where a just-dropped item was released, to animate it from there.
 */
@Composable
fun IconGrid(
    entries: List<HomeEntry>,
    grid: GridSpec,
    metrics: AreaMetrics,
    modifier: Modifier = Modifier,
    margins: GridMargins = GridMargins.Default,
    alignment: GridAlignment = GridAlignment.Top,
    header: (@Composable () -> Unit)? = null,
    onGeometry: ((GridGeometry) -> Unit)? = null,
    dropOrigin: (String) -> Offset? = { null },
    cells: Map<String, Int> = emptyMap(),
    item: @Composable (HomeEntry, IconSlot) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxSize()) {
        if (header != null) {
            Box(Modifier.padding(start = margins.side, end = margins.side, top = margins.top)) { header() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val area = DpSize(maxWidth, maxHeight)
            // Below a header the top margin is already above it.
            val frame = remember(area, grid, margins, alignment, metrics, header != null) {
                gridFrame(area, grid, if (header != null) margins.copy(top = 0.dp) else margins, alignment, metrics)
            }
            val slot = frame.iconSlot(metrics)
            val cellWidthPx = with(density) { frame.cellWidth.toPx() }
            val cellHeightPx = with(density) { frame.cellHeight.toPx() }
            val iconPx = with(density) { frame.iconSize.toPx() }
            val widthPx = cellWidthPx * grid.columns
            var originInWindow by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .padding(start = frame.side, top = frame.top)
                    .size(frame.width, frame.height)
                    .onGloballyPositioned { coordinates ->
                        originInWindow = coordinates.positionInWindow()
                        onGeometry?.invoke(GridGeometry(originInWindow, cellWidthPx, cellHeightPx, grid, iconPx, rtl))
                    },
            ) {
                fun slotOffset(cell: Int) = IntOffset(
                    (grid.columnOf(cell) * cellWidthPx).roundToInt(),
                    (grid.rowOf(cell) * cellHeightPx).roundToInt(),
                )
                // Widgets take several cells; apps flow around them (see packCells).
                val spans = entries.map { it.span }
                val preferred = entries.map { cells[it.id] }
                val placed = remember(spans, grid, preferred) { packCells(spans, grid, preferred) }
                entries.forEachIndexed { index, entry ->
                    val cell = placed[index] ?: return@forEachIndexed
                    val span = spans[index]
                    key(entry.id) {
                        AnimatedSlot(
                            target = slotOffset(cell),
                            initial = dropOrigin(entry.id)?.let { origin ->
                                // The released icon's top-left → this slot's start (offsets mirror in RTL).
                                val left = origin.x - originInWindow.x - (cellWidthPx - iconPx) / 2
                                val x = if (rtl) widthPx - left - cellWidthPx * span.columns else left
                                IntOffset(x.roundToInt(), (origin.y - originInWindow.y).roundToInt())
                            },
                            modifier = Modifier.size(frame.cellWidth * span.columns, frame.cellHeight * span.rows),
                            // An icon shrinks back around its own center; a widget around its card.
                            pivot = if (span == Span.One) TransformOrigin(0.5f, iconPx / 2 / cellHeightPx) else TransformOrigin.Center,
                        ) { item(entry, slot) }
                    }
                }
            }
        }
    }
}

/**
 * A child that springs to [target] whenever it changes. A just-dropped item starts at
 * [initial], at its lifted size, and settles into the slot around [pivot] instead of popping.
 */
@Composable
internal fun AnimatedSlot(
    target: IntOffset,
    initial: IntOffset?,
    modifier: Modifier,
    pivot: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(initial ?: target, IntOffset.VectorConverter) }
    val landing = remember { if (initial != null) Animatable(Motion.LIFT_SCALE) else null }
    LaunchedEffect(target) { offset.animateTo(target, SlotSpring) }
    if (landing != null) LaunchedEffect(Unit) { landing.animateTo(1f, Motion.settle()) }
    Box(
        modifier
            .offset { offset.value }
            .then(
                if (landing == null) Modifier else Modifier.graphicsLayer {
                    scaleX = landing.value
                    scaleY = landing.value
                    transformOrigin = pivot
                },
            ),
        contentAlignment = Alignment.TopCenter,
    ) { content() }
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
 * Its [badge] is the sum of its apps' notification counts.
 */
@Composable
fun FolderIcon(
    folder: FolderEntry,
    modifier: Modifier = Modifier,
    size: Dp,
    highlighted: Boolean = false,
    badge: IconBadge = IconBadge.None,
) {
    val settings = LocalSettingsState.current
    val shape = IconShape(settings.iconCorner)
    val scale by animateFloatAsState(if (highlighted) 1.15f else 1f, Motion.bouncy(), label = "folderHover")
    val badged = groupBadgeModifier(folder.apps, folder.name, badge)
    FolderGlass(settings.folders) {
        Box(
            modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(badged)
                .glass(shape, tint = if (highlighted) Color.White.copy(alpha = 0.32f) else Color.Unspecified, profile = GlassProfile.Tile),
        ) {
            folder.apps.take(GridSpec.Folder.cellCount).forEachIndexed { index, app ->
                val cell = folderPreviewCell(index)
                AppIconImage(
                    app = app,
                    size = size * cell.size,
                    modifier = Modifier.offset(
                        x = size * cell.left,
                        y = size * cell.top,
                    ),
                )
            }
        }
    }
}

/** Glass squircle that appears behind an app icon when another app hovers over it (future folder). */
@Composable
fun MergeHalo(visible: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (visible) 1.22f else 0.9f, Motion.bouncy(), label = "halo")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, Motion.quickFade(), label = "haloAlpha")
    if (alpha == 0f) return
    val settings = LocalSettingsState.current
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .glass(IconShape(settings.iconCorner), tint = Color.White.copy(alpha = 0.3f), profile = GlassProfile.Tile),
    )
}

/** Vertical gap between an icon and its label. */
val LabelGap: Dp = 6.dp

/** Spacer with [LabelGap] height, for consistency between grid, folder and preview. */
@Composable
fun LabelSpacer() = Spacer(Modifier.height(LabelGap))
