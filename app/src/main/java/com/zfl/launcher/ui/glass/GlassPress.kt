package com.zfl.launcher.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import com.zfl.launcher.ui.theme.LiquidGlassTokens
import com.zfl.launcher.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The gel-like reaction of a Liquid glass button: it lights up from under the finger, grows a
 * little, and leans and stretches after a finger that slides, springing back on release.
 * Values are animated and read in the draw phase only.
 */
internal class GlassPress(private val scope: () -> CoroutineScope) {
    private var progress = Animatable(0f, PROGRESS_THRESHOLD)
    private var position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var start = Offset.Zero

    /** 0 at rest, 1 fully pressed (overshoots a little on the way). */
    val amount: Float get() = progress.value

    /** Where the light comes from: the finger, following it while it slides. */
    val touch: Offset get() = position.value

    fun down(at: Offset) {
        start = at
        scope().launch {
            launch { position.snapTo(at) }
            launch { progress.animateTo(1f, Motion.gel()) }
        }
    }

    fun move(to: Offset) {
        scope().launch { position.snapTo(to) }
    }

    fun up() {
        scope().launch {
            launch { progress.animateTo(0f, Motion.gel()) }
            launch { position.animateTo(start, Motion.gel()) }
        }
    }

    /** Back to rest at once: the node was detached and its animations were cancelled mid-way. */
    fun reset() {
        progress = Animatable(0f, PROGRESS_THRESHOLD)
        position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    }

    // Transform of the pressed element, see [measure].
    var translationX: Float = 0f
        private set
    var translationY: Float = 0f
        private set
    var scaleX: Float = 1f
        private set
    var scaleY: Float = 1f
        private set

    /**
     * Computes the transform for an element of [width] × [height] px: grown by [grow] px and
     * pulled after the finger. The pull is a tanh, so it never exceeds the element's shorter
     * side, and the element stretches by up to [stretch] px along the drag. Returns false at rest.
     */
    fun measure(width: Float, height: Float, grow: Float, stretch: Float): Boolean {
        val p = amount
        if (p == 0f || width <= 0f || height <= 0f) return false
        val minSide = min(width, height)
        val maxSide = max(width, height)
        val offset = touch - start
        translationX = minSide * tanh(LiquidGlassTokens.DragFollow * offset.x / minSide)
        translationY = minSide * tanh(LiquidGlassTokens.DragFollow * offset.y / minSide)
        val angle = atan2(offset.y, offset.x)
        val base = 1f + grow / height * p
        scaleX = base + stretch / height * abs(cos(angle) * offset.x / maxSide) * min(width / height, 1f)
        scaleY = base + stretch / height * abs(sin(angle) * offset.y / maxSide) * min(height / width, 1f)
        return true
    }

    private companion object {
        const val PROGRESS_THRESHOLD = 0.001f
    }
}

/** Observes touches on a glass button without consuming them; the content keeps handling clicks. */
internal class GlassTouchNode(private val press: GlassPress, private val enabled: () -> Boolean) :
    Modifier.Node(), PointerInputModifierNode {
    private var tracking = false

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        val changes = pointerEvent.changes
        when (pointerEvent.type) {
            PointerEventType.Press -> if (!tracking && changes.count { it.pressed } == 1 && enabled()) {
                tracking = true
                press.down(changes.first { it.pressed }.position)
            }
            PointerEventType.Move -> if (tracking) changes.firstOrNull { it.pressed }?.let { press.move(it.position) }
            PointerEventType.Release -> if (tracking && changes.none { it.pressed }) release()
        }
    }

    override fun onCancelPointerInput() {
        if (tracking) release()
    }

    private fun release() {
        tracking = false
        press.up()
    }
}
