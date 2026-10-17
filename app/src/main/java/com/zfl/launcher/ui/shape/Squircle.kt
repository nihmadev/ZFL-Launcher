package com.zfl.launcher.ui.shape

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Rounded rectangle with iOS-style "continuous" corners.
 *
 * Each corner is built from cubic Bézier curves that span ~1.53x the radius, so the
 * curvature ramps up smoothly from the straight edge instead of the visible kink
 * you get with [androidx.compose.foundation.shape.RoundedCornerShape].
 *
 * Create it either with a fixed radius (`ContinuousRoundedShape(16.dp)`) or with a
 * radius relative to the shorter side (`ContinuousRoundedShape.ratio(0.225f)`).
 */
class ContinuousRoundedShape private constructor(
    private val radiusDp: Dp?,
    private val radiusRatio: Float?,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(continuousRect(size.width, size.height, cornerRadius(size, density)))

    /** Corner radius in px for [size], before [continuousRect] limits it to what fits. */
    fun cornerRadius(size: Size, density: Density): Float =
        radiusDp?.let { with(density) { it.toPx() } } ?: (size.minDimension * radiusRatio!!)

    companion object {
        /** Corner radius as a fraction of the shorter side, e.g. `0.225f` for app icons. */
        fun ratio(ratio: Float): ContinuousRoundedShape = ContinuousRoundedShape(null, ratio)
        /** Fixed corner radius. */
        operator fun invoke(radius: Dp): ContinuousRoundedShape = ContinuousRoundedShape(radius, null)
    }
}

/** Corner radius of an app icon relative to its size (iOS uses ~22.5%). */
const val SQUIRCLE_RADIUS_RATIO: Float = 0.225f

/** Shape of an app icon. */
val SquircleShape: Shape = ContinuousRoundedShape.ratio(SQUIRCLE_RADIUS_RATIO)

/** Largest corner ratio that [continuousRect] can draw; beyond it the curves would overlap. */
const val CONTINUOUS_LIMIT_RATIO: Float = 0.5f / ContinuousCorner.EXTENT

/**
 * Outline of an app icon of size [w] x [h] with corners of [cornerRatio] × the shorter side:
 * continuous (iOS) corners while they fit, a plain rounded rectangle up to a circle at 0.5.
 */
fun iconOutline(w: Float, h: Float, cornerRatio: Float): Path {
    val side = minOf(w, h)
    if (cornerRatio <= CONTINUOUS_LIMIT_RATIO) return continuousRect(w, h, side * cornerRatio)
    val r = side * cornerRatio.coerceAtMost(0.5f)
    return Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
}

/** Icon shape with the user's corner ratio, see [iconOutline]. */
data class IconShape(val cornerRatio: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(iconOutline(size.width, size.height, cornerRatio))
}

/** Builds a continuous-corner rectangle path of size [w] x [h] with corner [radius] in px. */
fun continuousRect(w: Float, h: Float, radius: Float): Path {
    val r = minOf(radius, continuousRadiusLimit(w, h))
    fun x(v: Float) = v * r
    // Control points of the corner curves, in units of the radius, see [ContinuousCorner].
    val e = ContinuousCorner.EXTENT
    val a = ContinuousCorner.A
    val b = ContinuousCorner.B
    val c = ContinuousCorner.C
    val d = ContinuousCorner.D
    val f = ContinuousCorner.F
    val g = ContinuousCorner.G
    return Path().apply {
        moveTo(x(e), 0f)
        lineTo(w - x(e), 0f)
        cubicTo(w - x(a), 0f, w - x(b), 0f, w - x(c), x(d))
        cubicTo(w - x(f), x(g), w - x(g), x(f), w - x(d), x(c))
        cubicTo(w, x(b), w, x(a), w, x(e))
        lineTo(w, h - x(e))
        cubicTo(w, h - x(a), w, h - x(b), w - x(d), h - x(c))
        cubicTo(w - x(g), h - x(f), w - x(f), h - x(g), w - x(c), h - x(d))
        cubicTo(w - x(b), h, w - x(a), h, w - x(e), h)
        lineTo(x(e), h)
        cubicTo(x(a), h, x(b), h, x(c), h - x(d))
        cubicTo(x(f), h - x(g), x(g), h - x(f), x(d), h - x(c))
        cubicTo(0f, h - x(b), 0f, h - x(a), 0f, h - x(e))
        lineTo(0f, x(e))
        cubicTo(0f, x(a), 0f, x(b), x(d), x(c))
        cubicTo(x(g), x(f), x(f), x(g), x(c), x(d))
        cubicTo(x(b), 0f, x(a), 0f, x(e), 0f)
        close()
    }
}

/** Largest corner radius [continuousRect] can draw in a [w] x [h] box; beyond it the curves would overlap. */
fun continuousRadiusLimit(w: Float, h: Float): Float = minOf(w, h) / 2f / ContinuousCorner.EXTENT

/**
 * Geometry of one continuous corner, reverse-engineered from UIBezierPath(roundedRect:cornerRadius:).
 * All values are in units of the corner radius, measured from the corner of the bounding box:
 * the curve leaves the straight edge [EXTENT] radii away from the corner and is made of
 * three cubics (the middle one close to a circular arc).
 */
object ContinuousCorner {
    const val EXTENT: Float = 1.528665f
    const val A: Float = 1.08849f
    const val B: Float = 0.86840f
    const val C: Float = 0.63149f
    const val D: Float = 0.07491f
    const val F: Float = 0.37282f
    const val G: Float = 0.16906f
}
