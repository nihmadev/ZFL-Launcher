package com.zfl.launcher.ui.glass

import com.zfl.launcher.ui.shape.ContinuousCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GlassGeometryTest {

    /** Points of the top-left continuous corner of radius [r], exactly as `continuousRect` draws it. */
    private fun continuousCornerPoints(r: Float): List<Pair<Float, Float>> {
        val c = ContinuousCorner
        val curves = listOf(
            floatArrayOf(0f, c.EXTENT, 0f, c.A, 0f, c.B, c.D, c.C),
            floatArrayOf(c.D, c.C, c.G, c.F, c.F, c.G, c.C, c.D),
            floatArrayOf(c.C, c.D, c.B, 0f, c.A, 0f, c.EXTENT, 0f),
        )
        return curves.flatMap { k ->
            (0..200).map { i ->
                val t = i / 200f
                val u = 1 - t
                val x = u * u * u * k[0] + 3 * u * u * t * k[2] + 3 * u * t * t * k[4] + t * t * t * k[6]
                val y = u * u * u * k[1] + 3 * u * u * t * k[3] + 3 * u * t * t * k[5] + t * t * t * k[7]
                x * r to y * r
            }
        }
    }

    @Test
    fun squircleContourStaysWithinHalfAPixelOfTheBezierCorner() {
        // From a small folder icon up to corners larger than the dock's (~90 px on the test device).
        for (r in listOf(8f, 16f, 30f, 65f, 90f, 120f, 150f)) {
            val outline = GlassOutline.continuous(1000f, 800f, r)
            val worst = continuousCornerPoints(r).maxOf { (x, y) -> abs(outline.signedDistance(x, y)) }
            assertTrue("r=$r deviates by $worst px", worst <= 0.5f)
        }
    }

    @Test
    fun squircleRadiusIsLimitedLikeThePath() {
        // A 60 px tall capsule with a huge radius: the path clamps r to 60 / 2 / EXTENT.
        val clamped = GlassOutline.continuous(300f, 60f, 500f)
        val reference = GlassOutline.continuous(300f, 60f, 60f / 2f / ContinuousCorner.EXTENT)
        assertEquals(reference.topLeft, clamped.topLeft, 1e-4f)
        assertTrue(clamped.topLeft <= 30f)
        // The middle of the short side lies on the contour.
        assertEquals(0f, clamped.signedDistance(0f, 30f), 0.01f)
    }

    @Test
    fun circleIsExact() {
        val circle = GlassOutline.rounded(100f, 100f, 50f, 50f, 50f, 50f)
        for (i in 0 until 32) {
            val a = i * Math.PI.toFloat() / 16f
            val x = 50f + 50f * cos(a)
            val y = 50f + 50f * sin(a)
            assertEquals(0f, circle.signedDistance(x, y), 1e-3f)
        }
        assertEquals(-50f, circle.signedDistance(50f, 50f), 1e-3f)
        assertEquals(10f, circle.signedDistance(110f, 50f), 1e-3f)
    }

    @Test
    fun distanceIsExactAlongStraightEdges() {
        val dock = GlassOutline.continuous(1000f, 250f, 90f)
        assertEquals(-12f, dock.signedDistance(500f, 12f), 1e-3f)
        assertEquals(-20f, dock.signedDistance(980f, 125f), 1e-3f)
        assertEquals(5f, dock.signedDistance(500f, 255f), 1e-3f)
    }

    @Test
    fun cornerDistanceIsCloseToEuclidean() {
        // Near the contour the first-order estimate must not distort the rim width.
        val outline = GlassOutline.continuous(400f, 400f, 60f)
        val boundary = continuousCornerPoints(60f)
        for (depth in listOf(2f, 6f, 12f)) {
            // A point [depth] px inside along the diagonal of the corner.
            val (bx, by) = boundary.minBy { (x, y) -> abs(x - y) }
            val step = depth / hypot(1f, 1f)
            // The contour itself may differ from the Bézier by up to ~0.2 px at this radius.
            assertEquals(-depth, outline.signedDistance(bx + step, by + step), 0.25f + depth * 0.03f)
        }
    }

    @Test
    fun roundedCornersFollowTheirQuadrants() {
        // A sheet: round top corners, square bottom ones.
        val sheet = GlassOutline.rounded(400f, 300f, 40f, 40f, 0f, 0f)
        assertTrue(sheet.signedDistance(2f, 2f) > 0f)      // cut off at the top-left
        assertTrue(sheet.signedDistance(2f, 298f) < 0f)    // kept at the bottom-left
        assertTrue(sheet.signedDistance(398f, 298f) < 0f)  // kept at the bottom-right
    }

    @Test
    fun oversizedRoundedCornersAreScaledToFit() {
        val pill = GlassOutline.rounded(200f, 40f, 100f, 100f, 100f, 100f)
        assertEquals(20f, pill.topLeft, 1e-3f)
        assertEquals(0f, pill.signedDistance(0f, 20f), 1e-3f)
        assertEquals(0f, pill.signedDistance(100f, 0f), 1e-3f)
    }

    @Test
    fun lensBendsMostAtTheEdgeAndNotAtAllInTheBody() {
        assertEquals(1f, lensBend(0f, 20f), 1e-6f)
        assertEquals(0f, lensBend(20f, 20f), 1e-6f)
        assertEquals(0f, lensBend(80f, 20f), 1e-6f)
        var previous = 1f
        for (i in 1..20) {
            val bend = lensBend(i.toFloat(), 20f)
            assertTrue("bend must fall towards the body", bend < previous)
            previous = bend
        }
        // Zero slope at the inner end of the rim: the body joins without a visible seam.
        assertTrue(lensBend(19f, 20f) - lensBend(20f, 20f) < 0.005f)
        // Steep at the edge: the background is squeezed there.
        assertTrue(lensBend(0f, 20f) - lensBend(1f, 20f) > 0.2f)
        assertEquals(0f, lensBend(5f, 0f), 0f)
    }

    @Test
    fun rimNeverExceedsHalfTheShorterSide() {
        assertEquals(13f, effectiveRim(30f, 300f, 26f), 1e-6f)
        assertEquals(30f, effectiveRim(30f, 1000f, 250f), 1e-6f)
        assertEquals(0f, effectiveRim(30f, 0f, 0f), 1e-6f)
    }
}
