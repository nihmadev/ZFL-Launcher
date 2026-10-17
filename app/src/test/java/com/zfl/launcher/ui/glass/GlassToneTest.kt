package com.zfl.launcher.ui.glass

import com.zfl.launcher.ui.theme.LiquidGlassTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassToneTest {

    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    @Test
    fun luminanceMapAveragesAnyRectangle() {
        // Left half black, right half white, 4 x 2 pixels.
        val pixels = IntArray(8) { i -> if (i % 4 < 2) gray(0) else gray(255) }
        val map = LuminanceMap.fromArgb(pixels, 4, 2)
        assertEquals(0f, map.mean(0f, 0f, 2f, 2f), 1e-5f)
        assertEquals(1f, map.mean(2f, 0f, 4f, 2f), 1e-5f)
        assertEquals(0.5f, map.mean(0f, 0f, 4f, 2f), 1e-5f)
        assertEquals(0.5f, map.mean(1f, 1f, 3f, 2f), 1e-5f)
    }

    @Test
    fun luminanceMapClipsToTheImage() {
        val map = LuminanceMap.fromArgb(IntArray(4) { gray(255) }, 2, 2)
        assertEquals(1f, map.mean(-10f, -10f, 50f, 50f), 1e-5f)
        // Entirely off the image (an element scrolled away): neutral, not NaN.
        assertEquals(0.5f, map.mean(10f, 10f, 20f, 20f), 1e-5f)
    }

    @Test
    fun luminanceUsesRec709Weights() {
        assertEquals(0.7152f, LuminanceMap.luminance(0xFF00FF00.toInt()), 1e-3f)
        assertEquals(0.0722f, LuminanceMap.luminance(0xFF0000FF.toInt()), 1e-3f)
    }

    @Test
    fun darkBackgroundGetsBrighterWhiteGlass() {
        val tone = GlassTone()
        glassTone(0.05f, 0.12f, tone)
        assertEquals(1f, tone.red, 1e-6f)
        assertEquals(0.12f * (1f + LiquidGlassTokens.DarkTintBoost), tone.alpha, 1e-5f)
        assertEquals(1f, tone.highlight, 1e-6f)
    }

    @Test
    fun lightBackgroundGetsGreyGlassAndAStrongerHighlight() {
        val tone = GlassTone()
        glassTone(0.95f, 0.12f, tone)
        assertEquals(LiquidGlassTokens.LightTintLevel, tone.red, 1e-6f)
        assertEquals(0.12f, tone.alpha, 1e-6f)
        assertEquals(1f + LiquidGlassTokens.LightHighlightBoost, tone.highlight, 1e-6f)
        assertTrue("a light background gets a lighter shadow", tone.shadow < 1f)
    }

    @Test
    fun toneChangesSmoothlyWithLuminance() {
        val tone = GlassTone()
        var previousLevel = 2f
        var previousHighlight = 0f
        for (i in 0..100) {
            glassTone(i / 100f, 0.12f, tone)
            assertTrue(tone.red <= previousLevel)
            assertTrue(tone.highlight >= previousHighlight)
            assertTrue(previousLevel - tone.red < 0.05f || previousLevel == 2f)
            previousLevel = tone.red
            previousHighlight = tone.highlight
        }
    }

    @Test
    fun callerTintIsKept() {
        val tone = GlassTone()
        fixedTone(0f, 0f, 0f, 0.35f, 0.9f, tone)
        assertEquals(0f, tone.red, 0f)
        assertEquals(0.35f, tone.alpha, 0f)
        assertTrue(tone.highlight > 1f)
    }
}
