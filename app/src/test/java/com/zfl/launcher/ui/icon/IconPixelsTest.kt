package com.zfl.launcher.ui.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPixelsTest {
    private val s = 96
    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1A73E8.toInt()
    private val red = 0xFFEA4335.toInt()
    private val yellow = 0xFFFBBC04.toInt()
    private val green = 0xFF34A853.toInt()
    private val black = 0xFF000000.toInt()
    private val tint = 0xFFFFC94D.toInt()

    /** [plate] fills the square with a centered [glyph] square of side s/3. */
    private fun tile(plate: Int, glyph: Int): IntArray = IntArray(s * s) { i ->
        val x = i % s
        val y = i / s
        if (x in s / 3 until 2 * s / 3 && y in s / 3 until 2 * s / 3) glyph else plate
    }

    /** A disk of [color] (transparent around it). */
    private fun disk(color: (x: Int, y: Int) -> Int): IntArray = IntArray(s * s) { i ->
        val x = i % s - s / 2 + 0.5f
        val y = i / s - s / 2 + 0.5f
        if (x * x + y * y < (s * 0.45f) * (s * 0.45f)) color(i % s, i / s) else 0
    }

    private fun isGlyphPixel(i: Int) = (i % s) in s / 3 until 2 * s / 3 && (i / s) in s / 3 until 2 * s / 3

    @Test
    fun whiteTileWithLogoIsAPlate() {
        val pixels = tile(white, blue)
        val plate = findPlate(pixels, s)
        assertNotNull(plate)
        assertTrue(plate!!.isLight)
        assertEquals(1f, plate.weight(white))
        assertEquals(0f, plate.weight(blue))
    }

    @Test
    fun gradientTileIsOnePlate() {
        // A vertical blue gradient like many OEM tiles, with a white symbol.
        val pixels = IntArray(s * s) { i ->
            val y = i / s
            if (isGlyphPixel(i)) white else argb(255, 20, 60 + y * 40 / s, 160 + y * 50 / s)
        }
        val plate = findPlate(pixels, s)!!
        assertFalse(plate.isLight)
        for (i in pixels.indices) if (!isGlyphPixel(i)) assertEquals(1f, plate.weight(pixels[i]))
    }

    @Test
    fun oneColorGlyphAndMulticolorArtHaveNoPlate() {
        assertNull(findPlate(disk { _, _ -> white }, s))
        val chrome = disk { x, y -> if (y < s / 2) red else if (x < s / 2) green else yellow }
        assertNull(findPlate(chrome, s))
        assertNull(findPlate(IntArray(s * s), s))
    }

    @Test
    fun lightGlyphWithDarkOutlineIsNotAPlate() {
        // Like a white pill drawn with a thin dark outline: Dark must keep the white.
        val pixels = disk { x, y ->
            val dx = x - s / 2 + 0.5f
            val dy = y - s / 2 + 0.5f
            if (dx * dx + dy * dy > (s * 0.42f) * (s * 0.42f)) black else white
        }
        assertNull(findPlate(pixels, s))
        val before = pixels.copyOf()
        darkenLayer(pixels, findPlate(pixels, s)?.takeIf { it.isLight })
        assertTrue(before.contentEquals(pixels))
    }

    @Test
    fun smallShapeWithASymbolIsArtNotABackdrop() {
        // Like VLC's white cone with orange stripes, or Authenticator's lock with a person:
        // the shape covers a third of the icon, so Dark and Tinted must keep it.
        val pixels = IntArray(s * s) { i ->
            val x = i % s
            val y = i / s
            when {
                x !in s / 5 until 4 * s / 5 || y !in s / 4 until 3 * s / 4 -> 0
                y in s * 2 / 5 until s / 2 && x in s * 2 / 5 until s * 3 / 5 -> red
                else -> white
            }
        }
        assertNull(findPlate(pixels, s))
    }

    @Test
    fun tintedGlyphLeavesNoGrayPlate() {
        val pixels = tile(white, blue)
        assertTrue(tintGlyph(pixels, findPlate(pixels, s), tint))
        for (i in pixels.indices) {
            if (isGlyphPixel(i)) {
                assertEquals(tint, pixels[i])
            } else {
                assertEquals("plate pixel $i", 0, alpha(pixels[i]))
            }
        }
    }

    @Test
    fun tintedMulticolorKeepsShapeAndRelativeBrightness() {
        val pixels = disk { x, _ -> if (x < s / 2) yellow else blue }
        assertTrue(tintGlyph(pixels, findPlate(pixels, s), tint))
        val left = pixels[(s / 2) * s + s / 4]
        val right = pixels[(s / 2) * s + 3 * s / 4]
        assertEquals(255, alpha(left))
        assertEquals(255, alpha(right))
        assertTrue(luma(left) > luma(right))
        assertEquals(tint, left)
        assertEquals(0, pixels[0])
    }

    @Test
    fun faintShadowsAndSheensAreNotPartOfTheGlyph() {
        // Like Google Translate's foreground: a gray shadow at 10% and a white sheen at 2%.
        val shadow = 0x1A585858
        val sheen = 0x05FFFFFF
        val pixels = disk { x, y -> if (x < s / 2) white else if (y < s / 2) shadow else sheen }
        assertTrue(tintGlyph(pixels, findPlate(pixels, s), tint))
        assertEquals(tint, pixels[(s / 2) * s + s / 4])
        assertEquals(0, alpha(pixels[(s / 4) * s + 3 * s / 4]))
        assertEquals(0, alpha(pixels[(3 * s / 4) * s + 3 * s / 4]))
    }

    @Test
    fun emptyGlyphIsReported() {
        assertFalse(tintGlyph(IntArray(s * s), null, tint))
    }

    @Test
    fun darkRemovesALightPlate() {
        val pixels = tile(white, blue)
        darkenLayer(pixels, findPlate(pixels, s))
        for (i in pixels.indices) {
            if (isGlyphPixel(i)) assertEquals(blue, pixels[i]) else assertEquals(0, alpha(pixels[i]))
        }
    }

    @Test
    fun darkDimsAColoredTileWithoutInverting() {
        val pixels = tile(blue, white)
        darkenLayer(pixels, findPlate(pixels, s), plateDim = 0.45f)
        val plate = pixels[2 * s + 2]
        assertEquals(255, alpha(plate))
        assertTrue(luma(plate) < luma(blue))
        // Same hue, just darker.
        assertEquals(rgbToHsv(blue)[0], rgbToHsv(plate)[0], 2f)
        assertEquals(white, pixels[(s / 2) * s + s / 2])
    }

    @Test
    fun darkLiftsANearBlackGlyph() {
        val pixels = disk { _, _ -> black }
        darkenLayer(pixels, findPlate(pixels, s))
        assertTrue(luma(pixels[(s / 2) * s + s / 2]) > 0.5f)
        assertEquals(0, pixels[0])
    }

    @Test
    fun darkKeepsBrightContent() {
        val pixels = disk { x, _ -> if (x < s / 2) yellow else red }
        val before = pixels.copyOf()
        darkenLayer(pixels, findPlate(pixels, s))
        assertTrue(before.contentEquals(pixels))
    }

    @Test
    fun filledTileDetection() {
        val square = tile(blue, white)
        val box = opaqueBounds(square, s)!!
        assertEquals(PixelBox(0, 0, s, s), box)
        assertTrue(fillsBounds(square, s, box))
        val round = disk { _, _ -> blue }
        assertFalse(fillsBounds(round, s, opaqueBounds(round, s)!!))
    }
}
