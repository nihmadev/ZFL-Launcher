package com.zfl.launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragPointersTest {
    private val pointers = DragPointers(10f, 48f)

    private fun finger(id: Long, x: Float, y: Float = 100f, pressed: Boolean = true, wasPressed: Boolean = true) =
        PointerInputChange(
            id = PointerId(id), uptimeMillis = 16L, position = Offset(x, y), pressed = pressed,
            previousUptimeMillis = 0L, previousPosition = Offset(x, y),
            previousPressed = wasPressed, isInitiallyConsumed = false,
        )

    private fun event(vararg changes: PointerInputChange) { pointers.observe(PointerEvent(changes.toList())) }

    private fun pickUp() {
        event(finger(7, 100f))
        pointers.bind(Offset(100f, 100f))
        event(finger(7, 100f), finger(2, 500f, wasPressed = false))
        assertEquals(0, pointers.swipe(true))
    }

    @Test
    fun secondarySwipeDoesNotMoveTheItemEvenIfPointerOrderChanges() {
        pickUp()
        val second = finger(2, 400f)
        event(second, finger(7, 110f))
        assertEquals(1, pointers.swipe(true))
        assertEquals(PointerId(7), pointers.primary()?.id)
        assertEquals(Offset(110f, 100f), pointers.primary()?.position)
        assertTrue(second.isConsumed)
        assertFalse(pointers.primary()!!.isConsumed)
        event(finger(7, 110f), finger(2, 300f))
        assertEquals(0, pointers.swipe(true))
    }

    @Test
    fun liftingSecondaryAllowsAnotherSwipeInEitherDirection() {
        pickUp()
        event(finger(7, 100f), finger(2, 400f))
        assertEquals(1, pointers.swipe(true))
        event(finger(7, 100f), finger(2, 400f, pressed = false))
        assertEquals(0, pointers.swipe(true))
        assertFalse(pointers.hasSecondary)
        event(finger(7, 100f), finger(9, 300f, wasPressed = false))
        assertEquals(0, pointers.swipe(true))
        event(finger(7, 100f), finger(9, 360f))
        assertEquals(-1, pointers.swipe(true))
    }

    @Test
    fun primaryReleaseNeverHandsTheItemToTheRemainingFinger() {
        pickUp()
        event(finger(2, 400f), finger(7, 100f, pressed = false))
        assertFalse(pointers.primary()!!.pressed)
        event(finger(2, 400f))
        assertEquals(null, pointers.primary())
    }

    @Test
    fun verticalGestureCannotTurnIntoPageSwipeAndSmallMovementDoesNothing() {
        pickUp()
        event(finger(7, 100f), finger(2, 495f, 105f))
        assertEquals(0, pointers.swipe(true))
        event(finger(7, 100f), finger(2, 495f, 140f))
        assertEquals(0, pointers.swipe(true))
        event(finger(7, 100f), finger(2, 300f, 140f))
        assertEquals(0, pointers.swipe(true))
    }

    @Test
    fun preparedLongPressAndOpenFolderDoNotFlipPages() {
        pickUp()
        event(finger(7, 100f), finger(2, 400f))
        assertEquals(0, pointers.swipe(false))
    }

    @Test
    fun thirdFingerDoesNotReplaceTheSwipeFinger() {
        pickUp()
        val third = finger(1, 10f, wasPressed = false)
        event(third, finger(7, 100f), finger(2, 490f))
        assertEquals(0, pointers.swipe(true))
        assertTrue(third.isConsumed)
        event(finger(1, 200f), finger(7, 100f), finger(2, 430f))
        assertEquals(1, pointers.swipe(true))
    }
}
