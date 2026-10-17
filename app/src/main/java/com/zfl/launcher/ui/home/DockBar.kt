package com.zfl.launcher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.min
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.Span
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The glass dock. Icons are spread evenly and slide when the dock content changes; with
 * names shown they sit under the icons. [height] comes from SettingsState.dockHeight, the
 * same value the pages leave free for the dock. [background] = false leaves just the icons.
 *
 * @param slots dock slots chosen in settings: icons shrink when that many wouldn't fit.
 * @param onBounds receives the dock's window bounds (drag and drop target area).
 */
@Composable
fun DockBar(
    entries: List<HomeEntry>,
    metrics: AreaMetrics,
    slots: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    background: Boolean = true,
    onBounds: ((Rect) -> Unit)? = null,
    dropOrigin: (String) -> Offset? = { null },
    marker: Pair<Int, Int>? = null,
    item: @Composable (HomeEntry, IconSlot) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = Tokens.DockSideMargin, end = Tokens.DockSideMargin, bottom = Tokens.DockBottomMargin)
            .height(height)
            .then(if (background) Modifier.glass(ContinuousRoundedShape(Tokens.DockRadius)) else Modifier)
            .onGloballyPositioned { onBounds?.invoke(it.boundsInWindow()) },
    ) {
        val iconSize = dockIconSize(maxWidth, slots, metrics)
        val contentHeight = iconSize + metrics.labelBlock
        val widthPx = with(density) { maxWidth.toPx() }
        val iconPx = with(density) { iconSize.toPx() }
        val topPx = with(density) { ((height - contentHeight) / 2).toPx() }
        var originInWindow by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { originInWindow = it.positionInWindow() }) {
            val slotWidthPx = widthPx / entries.size.coerceAtLeast(1)
            val slotWidth = with(density) { slotWidthPx.toDp() }
            val slot = IconSlot(iconSize, min(iconSize + DockItemMargin * 2, slotWidth), metrics.showLabels, metrics.labelScale)
            marker?.let { (index, count) ->
                val markerSlotWidth = widthPx / count.coerceAtLeast(1)
                MarkerSlot(
                    target = IntOffset((markerSlotWidth * index + (markerSlotWidth - iconPx) / 2).roundToInt(), topPx.roundToInt()),
                    modifier = Modifier.size(iconSize),
                ) { DropMarker(Span.One, iconSize) }
            }
            entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    AnimatedSlot(
                        target = IntOffset((slotWidthPx * index).roundToInt(), topPx.roundToInt()),
                        initial = dropOrigin(entry.id)?.let {
                            // The released icon's top-left → this slot's start (offsets mirror in RTL).
                            val left = it.x - originInWindow.x - (slotWidthPx - iconPx) / 2
                            val x = if (rtl) widthPx - left - slotWidthPx else left
                            IntOffset(x.roundToInt(), (it.y - originInWindow.y).roundToInt())
                        },
                        modifier = Modifier.width(slotWidth).height(contentHeight),
                    ) { item(entry, slot) }
                }
            }
        }
    }
}

/** Extra touch width on each side of a dock icon, within its slot. */
private val DockItemMargin = Tokens.MinIconGap
