package com.zfl.launcher.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class GlassModeTest {

    @Test
    fun liquidNeedsAndroid13AndACompiledShader() {
        assertEquals(GlassMode.Liquid, resolveGlassMode(GlassMode.Liquid, 33, shaderReady = true))
        assertEquals(GlassMode.Liquid, resolveGlassMode(GlassMode.Liquid, 34, shaderReady = true))
        assertEquals(GlassMode.Frosted, resolveGlassMode(GlassMode.Liquid, 31, shaderReady = true))
        assertEquals(GlassMode.Frosted, resolveGlassMode(GlassMode.Liquid, 32, shaderReady = true))
        assertEquals(GlassMode.Frosted, resolveGlassMode(GlassMode.Liquid, 34, shaderReady = false))
        assertEquals(GlassMode.Frosted, resolveGlassMode(GlassMode.Frosted, 34, shaderReady = true))
    }

    @Test
    fun storedModeFallsBackToDefault() {
        assertEquals(GlassMode.Liquid, GlassMode.fromStored("Liquid", GlassMode.Frosted))
        assertEquals(GlassMode.Frosted, GlassMode.fromStored(null, GlassMode.Frosted))
        assertEquals(GlassMode.Liquid, GlassMode.fromStored("Glossy", GlassMode.Liquid))
    }

    @Test
    fun tiltSensorRunsOnlyWhenEverythingAsksForIt() {
        assertTrue(shouldTrackTilt(GlassMode.Liquid, tiltEnabled = true, visible = true, animationsEnabled = true, hasSensor = true))
        assertFalse(shouldTrackTilt(GlassMode.Frosted, tiltEnabled = true, visible = true, animationsEnabled = true, hasSensor = true))
        assertFalse(shouldTrackTilt(GlassMode.Liquid, tiltEnabled = false, visible = true, animationsEnabled = true, hasSensor = true))
        assertFalse(shouldTrackTilt(GlassMode.Liquid, tiltEnabled = true, visible = false, animationsEnabled = true, hasSensor = true))
        assertFalse(shouldTrackTilt(GlassMode.Liquid, tiltEnabled = true, visible = true, animationsEnabled = false, hasSensor = true))
        assertFalse(shouldTrackTilt(GlassMode.Liquid, tiltEnabled = true, visible = true, animationsEnabled = true, hasSensor = false))
    }

    @Test
    fun tiltAngleIsClampedAndSafe() {
        val max = (35 * PI / 180).toFloat()
        assertEquals(0f, tiltAngle(0f, max), 0f)
        assertEquals((PI / 6).toFloat(), tiltAngle(0.5f, max), 1e-5f)
        assertEquals(max, tiltAngle(1f, max), 1e-6f)
        assertEquals(-max, tiltAngle(-3f, max), 1e-6f)
        assertEquals(0f, tiltAngle(Float.NaN, max), 0f)
    }

    @Test
    fun filterGlidesTowardsTheTiltWithoutJumping() {
        val filter = TiltLightFilter(maxRadians = 1f, smoothing = 0.2f, deadband = 0.001f)
        val target = tiltAngle(0.5f, 1f)
        var previous = 0f
        repeat(60) {
            filter.update(0.5f)
            assertTrue("one sample moves at most smoothing x distance", filter.angle - previous <= 0.2f * target + 1e-6f)
            previous = filter.angle
        }
        assertEquals(target, filter.angle, 0.01f)
    }

    @Test
    fun filterIgnoresJitterBelowTheDeadband() {
        val filter = TiltLightFilter(maxRadians = 1f, smoothing = 0.5f, deadband = 0.02f)
        repeat(30) { filter.update(0.3f) }
        val settled = filter.angle
        var changes = 0
        repeat(200) { i -> if (filter.update(0.3f + if (i % 2 == 0) 0.004f else -0.004f)) changes++ }
        assertEquals(0, changes)
        assertTrue(abs(filter.angle - settled) < 0.02f)
    }

    @Test
    fun resetPutsTheLightBackOnTop() {
        val filter = TiltLightFilter(maxRadians = 1f, smoothing = 1f, deadband = 0f)
        filter.update(0.8f)
        assertTrue(filter.angle > 0f)
        filter.reset()
        assertEquals(0f, filter.angle, 0f)
    }
}
