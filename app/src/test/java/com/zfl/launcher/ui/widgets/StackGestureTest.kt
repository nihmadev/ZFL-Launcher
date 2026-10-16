package com.zfl.launcher.ui.widgets

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class StackGestureTest {
    private val slop = 10f

    @Test
    fun smallMovesStayUndecidedSoTapsAndLongPressesReachTheWidget() {
        assertEquals(StackGestureOwner.Undecided, stackGestureOwner(Offset(3f, 6f), slop) { false })
    }

    @Test
    fun horizontalTravelGoesToTheHomePager() {
        assertEquals(StackGestureOwner.Others, stackGestureOwner(Offset(30f, 12f), slop) { false })
    }

    @Test
    fun verticalTravelPagesTheStackInsteadOfOpeningSearch() {
        assertEquals(StackGestureOwner.Stack, stackGestureOwner(Offset(2f, 30f), slop) { false })
        assertEquals(StackGestureOwner.Stack, stackGestureOwner(Offset(2f, -30f), slop) { false })
    }

    @Test
    fun aListThatCanScrollThatWayKeepsTheGesture() {
        // Finger up (dy < 0) scrolls a list towards its end; at its end the stack takes over.
        assertEquals(StackGestureOwner.Others, stackGestureOwner(Offset(0f, -30f), slop) { dy -> dy < 0 })
        assertEquals(StackGestureOwner.Stack, stackGestureOwner(Offset(0f, 30f), slop) { dy -> dy < 0 })
    }
}
