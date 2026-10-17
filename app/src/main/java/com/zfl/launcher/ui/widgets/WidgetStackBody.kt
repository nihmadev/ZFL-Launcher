package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.AppWidgetEntry
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.zfl.launcher.ui.theme.Haptics

/** Corner radius of the stack's clip while its widgets slide; matches hosted and built-in widgets. */
private val StackRadius = 22.dp

/** How long the shown hosted widget rests before its snapshot (used while swiping) is refreshed. */
private const val SNAPSHOT_DELAY_MS = 800L

/**
 * A Smart Stack on a page: the shown widget, live, and while swiping its neighbors, drawn
 * without a live host (a hosted widget's last snapshot, a built-in widget's plain body).
 * The swipe offset is applied in the layout phase only; see [stackSwipe] for the gesture
 * priorities.
 *
 * @param interactive swipes, taps and long presses (off in edit mode and in previews).
 * @param onTap tap on a built-in widget: that widget and the position in it (0..1).
 * @param onLongPress long press, with the finger in window coordinates.
 * @param onSettled the user swiped to another widget; called once per swipe.
 */
@Composable
fun WidgetStackBody(
    entry: WidgetStackEntry,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onTap: (PageWidgetEntry, Offset) -> Unit = { _, _ -> },
    onLongPress: (Offset) -> Unit = {},
    onSettled: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptic = launcherHaptics()
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val state = remember { StackPagerState(scope, entry.current, onNewTarget = { haptic.performHapticFeedback(Haptics.Tick) }) { currentOnSettled(it) } }
    SideEffect { state.sync(entry.current, entry.items.size) }

    // Host views of this stack's hosted widgets, reused across swipes; dropped with the stack.
    val hostViews = remember { mutableMapOf<Int, ZflWidgetHostView>() }
    val hostedIds = entry.items.mapNotNull { (it as? AppWidgetEntry)?.appWidgetId }.toSet()
    SideEffect { hostViews.keys.retainAll(hostedIds) }
    DisposableEffect(Unit) { onDispose { hostViews.clear() } }

    val shown = entry.items.getOrNull(state.settled) ?: entry.currentItem
    // Refresh the snapshot of the shown hosted widget once it rests, off the swipe's first frame.
    LaunchedEffect(shown.id, state.scrolling) {
        if (state.scrolling || shown !is AppWidgetEntry) return@LaunchedEffect
        delay(SNAPSHOT_DELAY_MS)
        state.liveView?.let { AppWidgetSnapshots.capture(it.appWidgetId, it) }
    }
    if (shown !is AppWidgetEntry) SideEffect { state.liveView = null }

    val origin = remember { floatArrayOf(0f, 0f) }
    Box(
        modifier
            .onSizeChanged { state.pageHeight = it.height.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned { val p = it.positionInWindow(); origin[0] = p.x; origin[1] = p.y }
            .stackIndicator(state)
            .clip(ContinuousRoundedShape(StackRadius))
            .stackSwipe(state, enabled = interactive && entry.items.size > 1),
    ) {
        val window = if (state.scrolling) (state.settled - 1)..(state.settled + 1) else state.settled..state.settled
        for (index in window) {
            val item = entry.items.getOrNull(index) ?: continue
            val live = index == state.settled
            key(item.id) {
                val slide = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, ((index - state.position) * state.pageHeight).roundToInt()) }
                when {
                    !live -> PageWidgetBody(item, slide, live = false)
                    item is AppWidgetEntry -> HostedWidget(
                        item,
                        slide,
                        onLongPress = { pointer -> if (interactive) currentOnLongPress(pointer) },
                        cache = hostViews,
                        onView = { state.liveView = it },
                    )
                    else -> Box(slide) {
                        PageWidgetBody(item, Modifier.fillMaxSize())
                        // Built-in widgets get taps and long presses here; hosted ones handle their own.
                        if (interactive && item is WidgetEntry) Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(item.id) {
                                    detectTapGestures(
                                        onTap = { p -> currentOnTap(item, Offset(p.x / size.width, p.y / size.height)) },
                                        onLongPress = { p ->
                                            haptic.performHapticFeedback(Haptics.Lift)
                                            currentOnLongPress(Offset(origin[0] + p.x, origin[1] + p.y))
                                        },
                                    )
                                },
                        )
                    }
                }
            }
        }
    }
}
