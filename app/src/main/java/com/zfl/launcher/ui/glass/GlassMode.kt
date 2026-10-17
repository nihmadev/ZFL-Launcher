package com.zfl.launcher.ui.glass

import com.zfl.launcher.ui.theme.LiquidGlassTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin

/** How glass surfaces are rendered. */
enum class GlassMode {
    /** A slice of the blurred wallpaper with a tint and a gradient edge: works on every Android version. */
    Frosted,
    /** A lens computed by an AGSL shader from the wallpaper under the element (Android 13+). */
    Liquid;

    companion object {
        fun fromStored(name: String?, default: GlassMode): GlassMode = entries.firstOrNull { it.name == name } ?: default
    }
}

/** RuntimeShader (and so AGSL) exists since Android 13. */
const val LIQUID_GLASS_MIN_SDK: Int = 33

fun isLiquidGlassSupported(sdkInt: Int): Boolean = sdkInt >= LIQUID_GLASS_MIN_SDK

/**
 * The mode actually drawn: Liquid only when asked for, supported by the platform and the
 * shader compiled; Frosted in every other case.
 */
fun resolveGlassMode(requested: GlassMode, sdkInt: Int, shaderReady: Boolean): GlassMode =
    if (requested == GlassMode.Liquid && isLiquidGlassSupported(sdkInt) && shaderReady) GlassMode.Liquid else GlassMode.Frosted

/**
 * Whether the tilt sensor should run: only for a visible home screen with Liquid glass and
 * the highlight following the tilt, while animations are on and a sensor exists.
 */
fun shouldTrackTilt(
    mode: GlassMode,
    tiltEnabled: Boolean,
    visible: Boolean,
    animationsEnabled: Boolean,
    hasSensor: Boolean,
): Boolean = mode == GlassMode.Liquid && tiltEnabled && visible && animationsEnabled && hasSensor

/**
 * Angle (radians, positive = light moves right) of the light for a phone whose "up" vector
 * has [upX] as its component along the screen's x axis: the sideways tilt, clamped to
 * ±[maxRadians]. Stable when the phone lies flat (upX ≈ 0 keeps the light on top).
 */
fun tiltAngle(upX: Float, maxRadians: Float): Float {
    if (upX.isNaN()) return 0f
    return asin(upX.coerceIn(-1f, 1f)).coerceIn(-maxRadians, maxRadians)
}

/**
 * Smooths the raw tilt angle and publishes it only when it moved by more than a dead band,
 * so the highlight floats instead of jittering and a phone at rest causes no redraws.
 */
class TiltLightFilter(
    private val maxRadians: Float = LiquidGlassTokens.TiltMaxDegrees.toRadians(),
    private val smoothing: Float = LiquidGlassTokens.TiltSmoothing,
    private val deadband: Float = LiquidGlassTokens.TiltDeadbandDegrees.toRadians(),
) {
    // Starts from the light on top and glides to the phone's tilt.
    private var smoothed = 0f

    /** The last published angle, radians. */
    var angle: Float = 0f
        private set

    /** Feeds one sensor sample; returns true when [angle] changed. */
    fun update(upX: Float): Boolean {
        val target = tiltAngle(upX, maxRadians)
        smoothed += (target - smoothed) * smoothing
        if (abs(smoothed - angle) < deadband) return false
        angle = smoothed
        return true
    }

    /** Back to the light on top (sensor stopped). */
    fun reset() {
        smoothed = 0f
        angle = 0f
    }
}

private fun Float.toRadians(): Float = (this * PI / 180.0).toFloat()
