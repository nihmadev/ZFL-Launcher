package com.zfl.launcher.ui.glass

import com.zfl.launcher.ui.shape.ContinuousCorner
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Pure geometry of Liquid Glass, mirrored line by line in the AGSL shader
 * (LiquidGlassShader.kt). Kept free of Android types so JVM tests can check it.
 */

/** Exponent of circular corners: a superellipse with n = 2 is a circle. */
const val CIRCULAR_CORNER_EXPONENT: Float = 2f

/**
 * A continuous (iOS) corner of radius r is drawn by the shader as a superellipse quadrant
 * |x/a|^n + |y/a|^n = 1 with a = [SQUIRCLE_SDF_EXTENT] × r and n = [SQUIRCLE_SDF_EXPONENT].
 * Fitted numerically to the Bézier corner of `continuousRect`: the contours differ by at most
 * 0.31 % of r, under half a pixel even for the dock (r ≈ 90 px).
 */
const val SQUIRCLE_SDF_EXPONENT: Float = 2.611f
const val SQUIRCLE_SDF_EXTENT: Float = 1.241f

/** Corners smaller than this (px) are treated as sharp; keeps the shader away from 0/0. */
private const val MIN_CORNER = 0.5f

/**
 * Outline of a glass surface as the shader sees it: a [width] × [height] box whose corners
 * are superellipse quadrants of the given extents (px) and a shared [exponent].
 */
class GlassOutline(
    val width: Float,
    val height: Float,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val exponent: Float,
) {
    /**
     * Signed distance (px) from the point ([x], [y]), relative to the top-left corner, to the
     * contour: negative inside. Exact on straight edges and on the contour itself, a first
     * order estimate (value / gradient length) near superellipse corners. Mirrors `sdf()` of the shader.
     */
    fun signedDistance(x: Float, y: Float): Float {
        val px = x - width / 2f
        val py = y - height / 2f
        val a = cornerAt(px, py)
        val qx = abs(px) - width / 2f + a
        val qy = abs(py) - height / 2f + a
        if (qx > 0f && qy > 0f) {
            val n = exponent
            val ux = qx / a
            val uy = qy / a
            val norm = (ux.pow(n) + uy.pow(n)).pow(1f / n)
            val gx = (ux / norm).pow(n - 1f)
            val gy = (uy / norm).pow(n - 1f)
            return (norm - 1f) * a / hypot(gx, gy)
        }
        return max(qx, qy) - a
    }

    /** Corner extent of the quadrant the point (relative to the center, y down) is in. */
    private fun cornerAt(px: Float, py: Float): Float = if (px > 0f) {
        if (py > 0f) bottomRight else topRight
    } else {
        if (py > 0f) bottomLeft else topLeft
    }

    companion object {
        /** `ContinuousRoundedShape` / continuous `IconShape`: the radius is limited like `continuousRect` does. */
        fun continuous(width: Float, height: Float, radius: Float): GlassOutline {
            val limit = min(width, height) / 2f / ContinuousCorner.EXTENT
            val a = max(min(radius, limit) * SQUIRCLE_SDF_EXTENT, MIN_CORNER)
            return GlassOutline(width, height, a, a, a, a, SQUIRCLE_SDF_EXPONENT)
        }

        /**
         * Circular corners (`RoundedCornerShape`, `CircleShape`, round `IconShape`). Corners that
         * don't fit their side are scaled down in pairs, like Compose does for the path.
         */
        fun rounded(width: Float, height: Float, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): GlassOutline {
            var tl = max(topLeft, 0f)
            var tr = max(topRight, 0f)
            var br = max(bottomRight, 0f)
            var bl = max(bottomLeft, 0f)
            // The smallest factor over the four sides keeps every pair within its side.
            val scale = minOf(
                1f,
                fit(width, tl + tr), fit(width, bl + br),
                fit(height, tl + bl), fit(height, tr + br),
            )
            tl *= scale; tr *= scale; br *= scale; bl *= scale
            return GlassOutline(
                width, height,
                max(tl, MIN_CORNER), max(tr, MIN_CORNER), max(br, MIN_CORNER), max(bl, MIN_CORNER),
                CIRCULAR_CORNER_EXPONENT,
            )
        }

        private fun fit(side: Float, sum: Float): Float = if (sum > side && sum > 0f) side / sum else 1f
    }
}

/**
 * Lens profile: how far a sample is pulled in from the edge, as a fraction of the full
 * refraction, at [depth] px inside the contour of a bevel [rim] px wide. A quarter-circle
 * bevel: 1 at the edge (the background is magnified and bent there, like through a convex
 * lens), falling to 0 with zero slope at the inner end of the bevel, so the flat middle stays
 * undistorted.
 */
fun lensBend(depth: Float, rim: Float): Float {
    if (rim <= 0f) return 0f
    val inv = 1f - (depth / rim).coerceIn(0f, 1f)
    return 1f - sqrt(max(1f - inv * inv, 0f))
}

/** The rim can't be wider than half the shorter side: a thin capsule is all lens. */
fun effectiveRim(profileRim: Float, width: Float, height: Float): Float =
    max(min(profileRim, min(width, height) / 2f), 0f)
