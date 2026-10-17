package com.zfl.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.AppWidgetEntry
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.R
import com.zfl.launcher.ui.widgets.WidgetStackBody
import com.zfl.launcher.data.Span
import com.zfl.launcher.ui.widgets.AppWidgets
import com.zfl.launcher.ui.widgets.PageWidgetBody
import androidx.compose.ui.platform.LocalContext
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.widgets.WidgetBody
import com.zfl.launcher.ui.widgets.titleRes
import com.zfl.launcher.ui.theme.Haptics

/** What a [HomeWidget] reports back. Bounds and pointers are in window coordinates. */
interface WidgetCallbacks {
    /** A tap in normal mode; [position] is relative to the widget card (0..1 on both axes). */
    fun onTap(entry: PageWidgetEntry, position: Offset, bounds: Rect)
    fun onLongPress(entry: PageWidgetEntry, bounds: Rect, pointer: Offset)
    /** Edit mode: the finger moved past the touch slop on this widget. */
    fun onDragStart(entry: PageWidgetEntry, bounds: Rect, pointer: Offset)
    fun onRemove(entry: PageWidgetEntry)
    /** The user swiped stack [stackId] to the widget at [index]. */
    fun onStackSettled(stackId: String, index: Int)
}

/**
 * Size of a widget card inside its grid slot: the slot minus the same side insets and
 * label space an app icon has, so widgets line up with the icons around them.
 * Built-in small widgets stay square. [slotInset] overrides the margin on every side (Today page).
 */
fun widgetBodySize(slot: DpSize, span: Span, square: Boolean, iconSize: Dp, withLabel: Boolean, slotInset: Dp? = null): DpSize {
    val cellWidth = slot.width / span.columns
    val cellHeight = slot.height / span.rows
    val sideInset = slotInset ?: ((cellWidth - iconSize) / 2).coerceAtLeast(0.dp)
    val labelSpace = when {
        slotInset != null -> slotInset * 2
        withLabel -> (cellHeight - iconSize).coerceAtLeast(0.dp)
        else -> sideInset * 2
    }
    val width = slot.width - sideInset * 2
    val height = slot.height - labelSpace
    return if (square) min(width, height).let { DpSize(it, it) } else DpSize(width, height)
}

/** Built-in small widgets are square, like iOS; everything else fills its cells. */
val PageWidgetEntry.isSquare: Boolean get() = this is WidgetEntry && size == WidgetSize.Small

/**
 * A widget on a home screen page or the Today page. Normal mode: tap acts, long-press
 * shows the widget menu (another app's widget handles its own taps; the long press comes
 * from its host view). Edit mode: it jiggles, gets a "−" badge and drags on touch.
 *
 * @param slot icon size and names of the area: the card lines up with the icons around it.
 * @param withLabel show the widget's name under it, like home screen widgets in iOS27L.
 * @param draggable edit-mode dragging (home pages only; Today is arranged from the menu).
 * @param mergeTarget a widget of the same size hovers over this one and will make (or join) a stack.
 */
@Composable
fun HomeWidget(
    entry: PageWidgetEntry,
    slot: IconSlot,
    editMode: Boolean,
    jiggleSeed: Int,
    callbacks: WidgetCallbacks,
    withLabel: Boolean,
    draggable: Boolean,
    modifier: Modifier = Modifier,
    slotInset: Dp? = null,
    hidden: Boolean = false,
    mergeTarget: Boolean = false,
) {
    val haptic = launcherHaptics()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentEntry by rememberUpdatedState(entry)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val showLabel = withLabel && slot.showLabel

    val gestures = if (editMode) {
        Modifier.pointerInput(true, draggable) {
            if (!draggable) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        change.consume()
                        haptic.performHapticFeedback(Haptics.Lift)
                        currentCallbacks.onDragStart(currentEntry, bounds, bounds.topLeft + change.position)
                        break
                    }
                }
            }
        }
    } else {
        Modifier.pointerInput(false) {
            detectTapGestures(
                onTap = { p ->
                    currentCallbacks.onTap(currentEntry, Offset(p.x / size.width, p.y / size.height), bounds)
                },
                onLongPress = { p ->
                    haptic.performHapticFeedback(Haptics.Lift)
                    currentCallbacks.onLongPress(currentEntry, bounds, bounds.topLeft + p)
                },
            )
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val body = widgetBodySize(DpSize(maxWidth, maxHeight), entry.span, entry.isSquare, slot.iconSize, showLabel, slotInset)
        Column(
            Modifier
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .jiggle(editMode, jiggleSeed),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.stackMergeHighlight(mergeTarget)) {
                val bodyModifier = Modifier
                    .size(body)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                if (entry is WidgetStackEntry) {
                    WidgetStackBody(
                        entry,
                        bodyModifier,
                        interactive = !editMode,
                        onTap = { item, position -> currentCallbacks.onTap(item, position, bounds) },
                        onLongPress = { pointer -> currentCallbacks.onLongPress(currentEntry, bounds, pointer) },
                        onSettled = { index -> currentCallbacks.onStackSettled(currentEntry.id, index) },
                    )
                } else {
                    PageWidgetBody(
                        entry,
                        bodyModifier,
                        onLongPress = { pointer -> currentCallbacks.onLongPress(currentEntry, bounds, pointer) },
                    )
                }
                // Our own gesture layer; over another app's widget (and a stack, which has its own) only in edit mode.
                if (entry is WidgetEntry || editMode) Box(Modifier.matchParentSize().then(gestures))
                if (editMode) {
                    RemoveBadge(
                        onClick = { callbacks.onRemove(entry) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-8).dp, y = (-8).dp),
                    )
                }
            }
            if (showLabel) {
                LabelSpacer()
                IconLabel(widgetLabel(entry), scale = slot.labelScale)
            }
        }
    }
}

/** Name under a widget: the built-in widget's name, or the widget's own label from its app. */
@Composable
internal fun widgetLabel(entry: PageWidgetEntry): String = when (entry) {
    is WidgetStackEntry -> stringResource(R.string.stack_smart)
    is WidgetEntry -> stringResource(entry.kind.titleRes)
    is AppWidgetEntry -> {
        val context = LocalContext.current
        remember(entry.provider) { AppWidgets.get(context).providerLabel(entry.provider) }.orEmpty()
    }
}
