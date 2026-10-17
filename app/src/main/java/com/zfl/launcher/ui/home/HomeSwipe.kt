package com.zfl.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.zfl.launcher.settings.HomeBinding
import kotlin.math.abs

/** One pointer sequence. A locked horizontal axis never becomes a global vertical swipe. */
class HomeSwipe(private val slop: Float, private val threshold: Float) {
    private var vertical: Boolean? = null
    private var cancelled = false
    private var finished = false
    private var reached: HomeBinding? = null

    val binding: HomeBinding? get() = reached.takeIf { !cancelled && !finished }
    val claimed: Boolean get() = binding != null

    fun move(x: Float, y: Float, consumed: Boolean, pointers: Int, enabled: Boolean) {
        if (finished || cancelled) return
        if (consumed || pointers != 1 || !enabled) { cancel(); return }
        if (vertical == null && x * x + y * y > slop * slop) vertical = abs(y) > abs(x)
        if (vertical == false) { cancel(); return }
        if (vertical == true && reached == null && abs(y) > threshold) {
            reached = if (y < 0) HomeBinding.SwipeUp else HomeBinding.SwipeDown
        }
    }

    fun release(): HomeBinding? {
        if (cancelled || finished) return null
        finished = true
        return reached
    }

    fun cancel() { cancelled = true; reached = null }
}

/** Final pass observes consumption by stacks, hosted content and the home pager first. */
internal fun Modifier.homeSwipe(
    enabled: Boolean,
    threshold: Float,
    canHandle: (HomeBinding) -> Boolean,
    stillEnabled: () -> Boolean,
    onAction: (HomeBinding) -> Unit,
): Modifier = if (!enabled) this else pointerInput(threshold, enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val sequence = HomeSwipe(viewConfiguration.touchSlop, threshold)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // A second finger, child consumption or focus loss invalidates even an armed action.
                if (event.changes.size != 1 || !stillEnabled() || change.isConsumed) {
                    sequence.cancel()
                    break
                }
                if (!change.pressed) {
                    sequence.release()?.let { if (canHandle(it)) onAction(it) }
                    break
                }
                val travel = change.position - down.position
                sequence.move(travel.x, travel.y, change.isConsumed, event.changes.count { it.pressed }, stillEnabled())
                if (sequence.claimed) {
                    if (!canHandle(checkNotNull(sequence.binding))) {
                        sequence.cancel()
                        break
                    }
                    change.consume()
                }
            }
        } finally {
            sequence.cancel()
        }
    }
}
