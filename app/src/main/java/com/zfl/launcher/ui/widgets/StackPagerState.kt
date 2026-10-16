package com.zfl.launcher.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Scroll state of one Smart Stack. [position] is in widgets (1.0 = one widget further) and
 * is only read in layout/draw lambdas, so swiping never recomposes. The settled widget
 * ([settled]) and [scrolling] change once per swipe; they pick which widgets are composed.
 */
@Stable
class StackPagerState(
    private val scope: CoroutineScope,
    initial: Int,
    /** A release is heading for another widget (called before the snap animation, for a haptic tick). */
    private val onNewTarget: () -> Unit = {},
    private val onSettled: (Int) -> Unit,
) {
    /** Height of one widget, in px; set by the layout. */
    var pageHeight: Float = 1f

    var count: Int = 1
        internal set

    var position: Float by mutableFloatStateOf(initial.toFloat())
        private set

    var settled: Int by mutableIntStateOf(initial)
        private set

    /** True from the first move of a swipe until it has settled; neighbors are drawn meanwhile. */
    var scrolling: Boolean by mutableStateOf(false)
        private set

    /** Opacity of the side dots: shown while swiping, faded out after. */
    val indicator: Animatable<Float, *> = Animatable(0f)

    /** The shown hosted widget's view, for asking whether a list inside it can scroll. */
    var liveView: ZflWidgetHostView? = null

    private var settleJob: Job? = null
    private var fadeJob: Job? = null

    /** The saved current widget changed elsewhere (a reorder, a removal): follow it unless swiping. */
    fun sync(current: Int, count: Int) {
        this.count = count
        val target = current.coerceIn(0, (count - 1).coerceAtLeast(0))
        if (!scrolling && target != settled) {
            settled = target
            position = target.toFloat()
        }
    }

    fun contentCanScroll(at: Offset, dy: Float): Boolean =
        liveView?.canScrollContentVertically(at.x, at.y, if (dy < 0) 1 else -1) == true

    fun dragStart() {
        settleJob?.cancel()
        fadeJob?.cancel()
        scrolling = true
        liveView?.let { view -> if (AppWidgetSnapshots.get(view.appWidgetId) == null) AppWidgetSnapshots.capture(view.appWidgetId, view) }
        scope.launch { indicator.animateTo(1f, tween(INDICATOR_IN_MS)) }
    }

    /** The finger moved by [dy] px: up shows the next widget. Past either end it rubber-bands. */
    fun dragBy(dy: Float) {
        val next = position - dy / pageHeight
        val low = (settled - 1).coerceAtLeast(0).toFloat()
        val high = (settled + 1).coerceAtMost(count - 1).toFloat()
        position = when {
            next < low -> position - (dy / pageHeight) * RUBBER_BAND
            next > high -> position - (dy / pageHeight) * RUBBER_BAND
            else -> next
        }.coerceIn(low - MAX_OVERSCROLL, high + MAX_OVERSCROLL)
    }

    /** The finger lifted with [velocityY] px/s: snap to the nearest widget, or one further on a fling. */
    fun release(velocityY: Float) {
        val pagesPerSecond = -velocityY / pageHeight
        val delta = position - settled
        val target = when {
            pagesPerSecond > FLING_PAGES_PER_SECOND || (abs(pagesPerSecond) <= FLING_PAGES_PER_SECOND && delta > 0.5f) -> settled + 1
            pagesPerSecond < -FLING_PAGES_PER_SECOND || (abs(pagesPerSecond) <= FLING_PAGES_PER_SECOND && delta < -0.5f) -> settled - 1
            else -> settled
        }.coerceIn(0, count - 1)
        if (target != settled) onNewTarget()
        settleJob?.cancel()
        settleJob = scope.launch {
            // Compose animations follow the system animator duration scale (0 = instant).
            animate(position, target.toFloat(), pagesPerSecond, spring(dampingRatio = 0.9f, stiffness = SNAP_STIFFNESS)) { value, _ ->
                position = value
            }
            val changed = target != settled
            settled = target
            scrolling = false
            if (changed) onSettled(target)
            fadeJob = launch {
                delay(INDICATOR_HOLD_MS)
                indicator.animateTo(0f, tween(INDICATOR_OUT_MS))
            }
        }
    }

    private companion object {
        /** Movement past the first/last widget, as a fraction of the finger's. */
        const val RUBBER_BAND = 0.3f
        const val MAX_OVERSCROLL = 0.25f
        const val FLING_PAGES_PER_SECOND = 1.2f
        const val SNAP_STIFFNESS = 420f
        const val INDICATOR_IN_MS = 120
        const val INDICATOR_HOLD_MS = 700L
        const val INDICATOR_OUT_MS = 300
    }
}
