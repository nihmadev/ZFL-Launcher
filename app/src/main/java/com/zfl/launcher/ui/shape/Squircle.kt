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

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radiusDp?.let { with(density) { it.toPx() } } ?: (size.minDimension * radiusRatio!!)
        return Outline.Generic(continuousRect(size.width, size.height, r))
    }

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
private const val CONTINUOUS_LIMIT_RATIO = 0.5f / 1.528665f

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
    // Coefficients reverse-engineered from UIBezierPath(roundedRect:cornerRadius:).
    val limit = minOf(w, h) / 2f / 1.528665f
    val r = minOf(radius, limit)
    fun x(v: Float) = v * r
    return Path().apply {
        moveTo(x(1.528665f), 0f)
        lineTo(w - x(1.528665f), 0f)
        cubicTo(w - x(1.08849f), 0f, w - x(0.86840f), 0f, w - x(0.63149f), x(0.07491f))
        cubicTo(w - x(0.37282f), x(0.16906f), w - x(0.16906f), x(0.37282f), w - x(0.07491f), x(0.63149f))
        cubicTo(w, x(0.86840f), w, x(1.08849f), w, x(1.528665f))
        lineTo(w, h - x(1.528665f))
        cubicTo(w, h - x(1.08849f), w, h - x(0.86840f), w - x(0.07491f), h - x(0.63149f))
        cubicTo(w - x(0.16906f), h - x(0.37282f), w - x(0.37282f), h - x(0.16906f), w - x(0.63149f), h - x(0.07491f))
        cubicTo(w - x(0.86840f), h, w - x(1.08849f), h, w - x(1.528665f), h)
        lineTo(x(1.528665f), h)
        cubicTo(x(1.08849f), h, x(0.86840f), h, x(0.63149f), h - x(0.07491f))
        cubicTo(x(0.37282f), h - x(0.16906f), x(0.16906f), h - x(0.37282f), x(0.07491f), h - x(0.63149f))
        cubicTo(0f, h - x(0.86840f), 0f, h - x(1.08849f), 0f, h - x(1.528665f))
        lineTo(0f, x(1.528665f))
        cubicTo(0f, x(1.08849f), 0f, x(0.86840f), x(0.07491f), x(0.63149f))
        cubicTo(x(0.16906f), x(0.37282f), x(0.37282f), x(0.16906f), x(0.63149f), x(0.07491f))
        cubicTo(x(0.86840f), 0f, x(1.08849f), 0f, x(1.528665f), 0f)
        close()
    }
}
