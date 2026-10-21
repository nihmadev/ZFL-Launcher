package com.zfl.launcher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Motion tokens: every spring and duration of the launcher comes from here, so surfaces that
 * open, settle or react to a touch move with the same character, like iOS.
 *
 * Springs are interruptible: a new target keeps the current velocity instead of restarting.
 * Compose scales them with the system animator duration scale.
 */
object Motion {
    // Damping/stiffness pairs; see the spec functions below for where each is used.
    private const val EXPAND_DAMPING = 0.86f
    private const val EXPAND_STIFFNESS = 380f
    private const val POP_DAMPING = 0.8f
    private const val POP_STIFFNESS = 500f
    private const val SHEET_DAMPING = 0.9f
    private const val SHEET_STIFFNESS = 400f
    private const val SETTLE_DAMPING = 0.82f
    private const val SETTLE_STIFFNESS = 420f
    private const val CONTROL_DAMPING = 0.75f
    private const val CONTROL_STIFFNESS = 500f
    private const val BOUNCY_DAMPING = 0.6f
    private const val BOUNCY_STIFFNESS = 400f
    private const val PRESS_STIFFNESS = 600f
    private const val FADE_STIFFNESS = 400f
    private const val GEL_DAMPING = 0.5f
    private const val GEL_STIFFNESS = 300f

    /** Short fade of small helpers (jiggle amount, merge halo). */
    const val QUICK_FADE_MS: Int = 150
    /** Fade-in of the drop marker under a dragged item. */
    const val MARKER_FADE_MS: Int = 180
    /** How much an item grows when a drag lifts it off the grid, like iOS. */
    const val LIFT_SCALE: Float = 1.15f
    /** Push/pop between settings screens. */
    const val PUSH_MS: Int = 420
    /** Island shape: a soft spring with a little overshoot, like the Dynamic Island. */
    const val ISLAND_DAMPING: Float = 0.72f
    const val ISLAND_STIFFNESS: Float = 260f
    /** Island content: the old one fades out first, the new one unblurs while the shape grows. */
    const val ISLAND_OUT_MS: Int = 120
    const val ISLAND_IN_MS: Int = 280
    const val ISLAND_IN_DELAY_MS: Int = 80

    /** UIKit's default navigation curve: a fast start and a long, soft landing. */
    val IosEasing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

    /** A surface growing out of its source and back: folders, App Library categories, returning home. */
    fun <T> expand(): SpringSpec<T> = spring(EXPAND_DAMPING, EXPAND_STIFFNESS)

    /** The long-press menu and a dragged item lifting off: quicker than [expand], with a hint of overshoot. */
    fun <T> pop(): SpringSpec<T> = spring(POP_DAMPING, POP_STIFFNESS)

    /** Bottom sheets sliding in and out: the widget gallery, the stack editor. */
    fun <T> sheet(): SpringSpec<T> = spring(SHEET_DAMPING, SHEET_STIFFNESS)

    /** Content moving to a new place: grid slots, the drop marker, the edit-mode inset. */
    fun <T> settle(): SpringSpec<T> = spring(SETTLE_DAMPING, SETTLE_STIFFNESS)

    /** Toggles and segment highlights. */
    fun <T> control(): SpringSpec<T> = spring(CONTROL_DAMPING, CONTROL_STIFFNESS)

    /** Playful feedback that overshoots: a folder or stack inviting a drop. */
    fun <T> bouncy(): SpringSpec<T> = spring(BOUNCY_DAMPING, BOUNCY_STIFFNESS)

    /** Press-down scale of buttons: no overshoot. */
    fun <T> press(): SpringSpec<T> = spring(stiffness = PRESS_STIFFNESS)

    /** Liquid glass under a finger: springy, it overshoots and wobbles back like a gel. */
    fun <T> gel(): SpringSpec<T> = spring(GEL_DAMPING, GEL_STIFFNESS)

    /** Scrims, the home blur and fades of overlays, so dimming keeps pace with blurring. */
    fun <T> fade(): SpringSpec<T> = spring(stiffness = FADE_STIFFNESS)

    fun <T> quickFade(): TweenSpec<T> = tween(QUICK_FADE_MS)

    fun <T> push(): TweenSpec<T> = tween(PUSH_MS, easing = IosEasing)
}

/**
 * What each kind of touch feels like. The system maps these to the device's vibrator and
 * honors the user's touch feedback setting; constants newer than the running Android
 * fall back to the closest older one.
 */
object Haptics {
    /** An item lifts off the screen: long press, drag start. */
    val Lift: HapticFeedbackType = HapticFeedbackType.LongPress
    /** A drop target becomes active, a stack snaps to another widget, a segment changes. */
    val Tick: HapticFeedbackType = HapticFeedbackType.SegmentTick
    /** A swipe passed the point where it will open something (search). */
    val Threshold: HapticFeedbackType = HapticFeedbackType.GestureThresholdActivate
    /** A mode was entered: jiggle mode. */
    val Confirm: HapticFeedbackType = HapticFeedbackType.Confirm
    val ToggleOn: HapticFeedbackType = HapticFeedbackType.ToggleOn
    val ToggleOff: HapticFeedbackType = HapticFeedbackType.ToggleOff
}
