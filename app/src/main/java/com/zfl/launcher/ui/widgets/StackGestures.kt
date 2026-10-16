package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** Who handles a touch that started on a Smart Stack. */
enum class StackGestureOwner {
    /** The finger hasn't moved far enough to tell. */
    Undecided,
    /** A vertical swipe: the stack pages, nothing else sees the gesture. */
    Stack,
    /** Left to the others: the home pager (horizontal), a list in the widget, taps and long presses. */
    Others,
}

/**
 * Gesture priorities on a stack, decided once per gesture when the finger passes [slop]:
 *
 * 1. Mostly horizontal travel → [StackGestureOwner.Others]: the home pager flips pages.
 * 2. Mostly vertical travel over a list of the shown widget that can still scroll that way
 *    ([contentCanScroll]) → [StackGestureOwner.Others]: the list scrolls, as on a single widget.
 * 3. Any other vertical travel → [StackGestureOwner.Stack]: the stack pages (rubber-banding at
 *    its ends) and the page's search swipe ([com.zfl.launcher.settings.SearchSwipe]) doesn't fire.
 *
 * Taps and long presses never move past the slop, so the widget itself still gets them.
 *
 * @param travel finger movement since touch down, in px (positive y = down).
 * @param contentCanScroll whether content under the finger scrolls for a finger moving by `dy` (sign matters).
 */
fun stackGestureOwner(travel: Offset, slop: Float, contentCanScroll: (dy: Float) -> Boolean): StackGestureOwner = when {
    travel.getDistance() <= slop -> StackGestureOwner.Undecided
    abs(travel.x) >= abs(travel.y) -> StackGestureOwner.Others
    contentCanScroll(travel.y) -> StackGestureOwner.Others
    else -> StackGestureOwner.Stack
}

/**
 * The stack's swipe handler. It watches the Initial pass, so it decides before the
 * widgets inside (Compose taps, hosted views) and the page around it (search swipe, home
 * pager) see the movement; once the stack owns a gesture it consumes every change, which
 * cancels the others (a hosted view gets ACTION_CANCEL).
 */
internal fun Modifier.stackSwipe(state: StackPagerState, enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var travel = Offset.Zero
            var owned = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (owned) {
                            change.consume()
                            state.release(tracker.calculateVelocity().y)
                            owned = false
                        }
                        break
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (owned) {
                        state.dragBy(change.positionChange().y)
                        change.consume()
                        continue
                    }
                    // Someone above took the gesture (e.g. a drag of the whole stack).
                    if (change.isConsumed) break
                    travel += change.positionChange()
                    val owner = stackGestureOwner(travel, viewConfiguration.touchSlop) { dy -> state.contentCanScroll(down.position, dy) }
                    when (owner) {
                        StackGestureOwner.Undecided -> Unit
                        StackGestureOwner.Others -> break
                        StackGestureOwner.Stack -> {
                            owned = true
                            state.dragStart()
                            change.consume()
                        }
                    }
                }
            } finally {
                // Cancelled mid-swipe (the stack left the screen): settle where it is.
                if (owned) state.release(0f)
            }
        }
    }
