package com.zfl.launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs

/** Keeps the lifted item attached to its original finger for the entire gesture. */
internal class DragPointers(private val slop: Float, private val swipeThreshold: Float) {
    private var latest: List<PointerInputChange> = emptyList()
    private var primaryId: PointerId? = null
    private var secondaryId: PointerId? = null
    private var secondaryStart = Offset.Zero
    private var horizontal: Boolean? = null
    private var swiped = false

    val hasSecondary: Boolean get() = secondaryId != null

    fun observe(event: PointerEvent) { latest = event.changes }

    fun bind(position: Offset) {
        reset()
        primaryId = latest.filter { it.pressed }.minByOrNull { (it.position - position).getDistance() }?.id
    }

    fun primary(): PointerInputChange? = latest.firstOrNull { it.id == primaryId }

    /** Claims other contacts before child icons see them; one horizontal swipe flips one page. */
    fun swipe(enabled: Boolean): Int {
        val other = latest.firstOrNull { it.id == secondaryId }
        if (other?.pressed != true) {
            secondaryId = null
            horizontal = null
            swiped = false
        }
        if (secondaryId == null) {
            latest.firstOrNull { it.id != primaryId && it.pressed && !it.previousPressed }?.let {
                secondaryId = it.id
                secondaryStart = it.position
            }
        }
        latest.filter { it.id != primaryId }.forEach { it.consume() }
        val change = latest.firstOrNull { it.id == secondaryId && it.pressed } ?: return 0
        if (!enabled || swiped) return 0
        val travel = change.position - secondaryStart
        if (horizontal == null && travel.getDistance() > slop) horizontal = abs(travel.x) > abs(travel.y)
        if (horizontal != true || abs(travel.x) < swipeThreshold) return 0
        swiped = true
        return if (travel.x < 0) 1 else -1
    }

    fun reset() {
        primaryId = null
        secondaryId = null
        horizontal = null
        swiped = false
    }
}
