package com.zfl.launcher.island

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class IslandPlatformPolicyTest {
    @Test fun onlyDeclaredTransportCapabilitiesAreOffered() {
        assertTrue(islandCommands(0).isEmpty())
        assertEquals(setOf(IslandCommand.Play, IslandCommand.Pause), islandCommands(PlaybackState.ACTION_PLAY_PAUSE))
        assertEquals(setOf(IslandCommand.Play, IslandCommand.Next, IslandCommand.Seek),
            islandCommands(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO))
        assertEquals(setOf(IslandCommand.Previous), islandCommands(PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        assertEquals(setOf(IslandCommand.Pause), islandCommands(PlaybackState.ACTION_PAUSE))
        // Custom, stop, rating and route-like actions cannot become invented buttons.
        assertTrue(islandCommands(PlaybackState.ACTION_STOP or PlaybackState.ACTION_SET_RATING).isEmpty())
    }

    @Test fun missingOrStoppedPlaybackIsNotPretendedToBePlaying() {
        assertEquals(IslandPlayback.Inactive, islandPlayback(null))
        assertEquals(IslandPlayback.Inactive, islandPlayback(PlaybackState.STATE_STOPPED))
        assertEquals(IslandPlayback.Buffering, islandPlayback(PlaybackState.STATE_BUFFERING))
        assertEquals(IslandPlayback.Buffering, islandPlayback(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertEquals(IslandPlayback.Playing, islandPlayback(PlaybackState.STATE_FAST_FORWARDING))
    }

    @Test fun geometryWrapsTheCameraInsideTheStatusBar() {
        val geometry = islandGeometry(1080, 2400, 0, 0, 111, 94, 540, 47, 3f, 1f)
        assertEquals(0, geometry.centerOffsetPx)
        // 34dp capsule centered on the camera at 47px: 47 - 51 is clamped to the top edge.
        assertEquals(0, geometry.topPx)
        assertTrue(geometry.available)
        assertTrue(geometry.expansionAllowed)
        assertEquals(340f, geometry.widthDp, 0.01f)
        val offCenter = islandGeometry(1080, 2400, 0, 0, 0, 120, 600, 90, 2f, 1f)
        assertEquals(60, offCenter.centerOffsetPx)
        assertEquals(56, offCenter.topPx)
        val noCutout = islandGeometry(1080, 2400, 0, 0, 0, 100, null, null, 2f, 1f)
        assertEquals(16, noCutout.topPx)
    }

    @Test fun landscapeIsCompactAndCrampedOrLargeFontWindowsCannotExpand() {
        assertFalse(islandGeometry(2400, 1080, 120, 80, 10, 30, null, null, 3f, 1f).expansionAllowed)
        val cramped = islandGeometry(450, 400, 0, 0, 100, 100, null, null, 3f, 1f)
        assertFalse(cramped.available)
        val largeFont = islandGeometry(1000, 1000, 0, 0, 100, 100, null, null, 3f, 2f)
        assertTrue(largeFont.available)
        assertFalse(largeFont.expansionAllowed)
    }
}
