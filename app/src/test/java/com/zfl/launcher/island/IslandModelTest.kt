package com.zfl.launcher.island

import org.junit.Assert.*
import org.junit.Test

class IslandModelTest {
    private val environment = IslandEnvironment(true, true, true, true, true)
    private fun ready(): IslandModel = reduceIsland(
        reduceIsland(IslandModel(), IslandEvent.Environment(environment), 0),
        IslandEvent.Visibility(true, true), 0,
    )
    private fun session(id: Long, playback: IslandPlayback = IslandPlayback.Playing): IslandSession = IslandSession(id, playback)
    private fun IslandModel.event(event: IslandEvent, at: Long = 0): IslandModel = reduceIsland(this, event, at)

    @Test fun selectionRetainsTokenWithinRankAndFallsBackInSystemOrder() {
        var state = ready().event(IslandEvent.Sessions(listOf(session(1), session(2))))
        assertEquals(1L, state.selectedId)
        state = state.event(IslandEvent.Sessions(listOf(session(2), session(1))))
        assertEquals(1L, state.selectedId)
        state = state.event(IslandEvent.Sessions(listOf(session(2))))
        assertEquals(2L, state.selectedId)
        state = state.event(IslandEvent.Sessions(listOf(session(3, IslandPlayback.Paused), session(2))))
        assertEquals(2L, state.selectedId)
        state = state.event(IslandEvent.Sessions(listOf(session(2, IslandPlayback.Paused), session(3))))
        assertEquals(3L, state.selectedId)
    }

    @Test fun pausedExpiresWithoutBeingResurrectedByMetadataOrReordering() {
        val paused = session(1, IslandPlayback.Paused)
        var state = ready().event(IslandEvent.Sessions(listOf(paused)), 10)
        assertEquals(30_010L, state.nextDeadline())
        state = state.event(IslandEvent.Time, 30_010)
        assertEquals(IslandCard.Hidden, state.card)
        assertNull(state.nextDeadline())
        state = state.event(IslandEvent.Sessions(listOf(paused.copy(title = "New title"))), 31_000)
        assertNull(state.selectedId)
        state = state.event(IslandEvent.Sessions(listOf(session(1))), 31_001)
        assertEquals(IslandCard.MusicCompact, state.card)
    }

    @Test fun stickyBaselineAndDuplicateBatteryDoNotRestartCharging() {
        var state = ready().event(IslandEvent.Battery(true, 48, baseline = true))
        assertEquals(IslandCard.Hidden, state.card)
        state = state.event(IslandEvent.Battery(false, 48), 1).event(IslandEvent.Battery(true, 48), 2)
        assertEquals(IslandCard.Charging, state.card)
        assertEquals(4_002L, state.nextDeadline())
        state = state.event(IslandEvent.Battery(true, 49), 100)
        assertEquals(4_002L, state.nextDeadline())
        assertEquals(IslandCard.Hidden, state.event(IslandEvent.Time, 4_002).card)
        assertEquals(IslandCard.Hidden, state.event(IslandEvent.Battery(false, 49), 101).card)
    }

    @Test fun chargingReturnsToCurrentMusicAndCannotDismissExpandedControls() {
        var state = ready().event(IslandEvent.Sessions(listOf(session(1))))
            .event(IslandEvent.Battery(false, 50, true)).event(IslandEvent.Battery(true, 50), 1)
        assertEquals(IslandCard.Charging, state.card)
        state = state.event(IslandEvent.Sessions(listOf(session(2))), 2).event(IslandEvent.Time, 4_001)
        assertEquals(2L, state.selectedId)
        assertEquals(IslandCard.MusicCompact, state.card)
        state = state.event(IslandEvent.Expand, 4_002).event(IslandEvent.Battery(false, 50), 4_003)
            .event(IslandEvent.Battery(true, 50), 4_004)
        assertEquals(IslandCard.MusicExpanded, state.card)
        assertNull(state.chargingUntil)
        assertEquals(IslandCard.MusicCompact, state.event(IslandEvent.Time, 16_002).card)
    }

    @Test fun interactionsExtendCollapseButUnplugDisableAndScreenOffCancelEvents() {
        var state = ready().event(IslandEvent.Sessions(listOf(session(1)))).event(IslandEvent.Expand)
        state = state.event(IslandEvent.Interaction, 10_000)
        assertEquals(22_000L, state.collapseAt)
        state = state.event(IslandEvent.Visibility(false, false), 10_001)
        assertEquals(IslandCard.Hidden, state.card)
        assertNull(state.nextDeadline())
        state = state.event(IslandEvent.Visibility(true, false), 10_002)
        assertEquals(IslandCard.Hidden, state.card)
        state = state.event(IslandEvent.Visibility(true, true), 10_003)
        assertEquals(IslandCard.MusicCompact, state.card)
        state = state.event(IslandEvent.Battery(false, 20)).event(IslandEvent.Battery(true, 20), 10_004)
        state = state.event(IslandEvent.Environment(environment.copy(chargingEnabled = false)), 10_005)
        assertNull(state.chargingUntil)
    }

    @Test fun revokeDisconnectDisableAndReconnectRequireFreshRuntimeData() {
        for (env in listOf(environment.copy(windowGranted = false), environment.copy(notificationGranted = false),
            environment.copy(connected = false), environment.copy(enabled = false))) {
            val old = ready().event(IslandEvent.Sessions(listOf(session(1)))).event(IslandEvent.Expand)
            val stopped = old.event(IslandEvent.Environment(env), 1)
            assertEquals(IslandCard.Hidden, stopped.card)
            assertTrue(stopped.sessions.isEmpty())
            assertNull(stopped.nextDeadline())
            val reconnected = stopped.event(IslandEvent.Environment(environment), 2)
                .event(IslandEvent.Visibility(true, true), 2)
            assertEquals(IslandCard.Hidden, reconnected.card)
            assertEquals(IslandCard.MusicCompact, reconnected.event(IslandEvent.Sessions(listOf(session(2))), 3).card)
            assertEquals(stopped, stopped.event(IslandEvent.Environment(env), 1))
        }
    }

    @Test fun missingMetadataAndDurationRemainUsableWithoutInventedProgress() {
        val media = session(1)
        assertNotNull(selectIslandSession(listOf(media), null))
        assertFalse(media.hasProgress)
        assertFalse(media.canSeek)
        assertNull(media.progressPosition(500))
        assertTrue(media.commands.isEmpty())
        assertFalse(media.canOpen)
    }

    @Test fun progressUsesPlaybackSpeedAndMonotonicTimestampAndClampsToDuration() {
        val playing = session(1).copy(durationMs = 10_000, positionMs = 2_000, positionAtMs = 1_000, speed = 2f,
            commands = setOf(IslandCommand.Seek))
        assertEquals(4_000L, playing.progressPosition(2_000))
        assertEquals(10_000L, playing.progressPosition(20_000))
        assertEquals(2_000L, playing.progressPosition(500))
        assertEquals(2_000L, playing.copy(playback = IslandPlayback.Paused).progressPosition(9_000))
        assertEquals(2_000L, playing.copy(playback = IslandPlayback.Buffering).progressPosition(9_000))
        assertEquals(2_000L, playing.copy(speed = Float.NaN).progressPosition(9_000))
        assertTrue(playing.canSeek)
        assertFalse(playing.copy(commands = emptySet()).canSeek)
        assertFalse(playing.copy(durationMs = 0).canSeek)
    }

    @Test fun repeatedEnableDisableHasNoPersistedExpansionAndRestorationStartsCompact() {
        var state = ready()
        repeat(3) {
            state = state.event(IslandEvent.Sessions(listOf(session(it.toLong())))).event(IslandEvent.Expand)
            assertEquals(IslandCard.MusicExpanded, state.card)
            state = state.event(IslandEvent.Environment(environment.copy(enabled = false)))
                .event(IslandEvent.Environment(environment)).event(IslandEvent.Visibility(true, true))
            assertEquals(IslandCard.Hidden, state.card)
        }
        val restored = ready().event(IslandEvent.Sessions(listOf(session(20))))
        assertEquals(IslandCard.MusicCompact, restored.card)
        assertNull(restored.collapseAt)
    }

    @Test fun spaceChangeCollapsesOrHidesAndUnlockDoesNotReplayCharging() {
        var state = ready().event(IslandEvent.Sessions(listOf(session(1)))).event(IslandEvent.Expand)
        state = state.event(IslandEvent.Space(true, false))
        assertEquals(IslandCard.MusicCompact, state.card)
        assertEquals(IslandCard.MusicCompact, state.event(IslandEvent.Expand).card)
        assertEquals(IslandCard.Hidden, state.event(IslandEvent.Space(false, false)).card)
        state = state.event(IslandEvent.Battery(false, 10, true)).event(IslandEvent.Visibility(false, false))
            .event(IslandEvent.Battery(true, 10)).event(IslandEvent.Visibility(true, true))
        assertEquals(IslandCard.MusicCompact, state.card)
        assertNull(state.chargingUntil)
    }

    private fun timer(key: String, kind: IslandTimerKind = IslandTimerKind.Timer): IslandTimer = IslandTimer(key, kind)

    @Test fun clockActivityTakesTheCompactSlotAndMusicReturnsAfterIt() {
        var state = ready().event(IslandEvent.Sessions(listOf(session(1))))
        state = state.event(IslandEvent.Timers(listOf(timer("t"))))
        assertEquals(IslandCard.TimerCompact, state.card)
        state = state.event(IslandEvent.Expand, 5)
        assertEquals(IslandCard.TimerExpanded, state.card)
        state = state.event(IslandEvent.Timers(emptyList()), 6)
        assertNull(state.collapseAt)
        assertEquals(IslandCard.MusicCompact, state.card)
    }

    @Test fun newRingingAlarmComesFirstAndOpensItselfOnce() {
        var state = ready().event(IslandEvent.Timers(listOf(timer("t"))))
        assertEquals(IslandCard.TimerCompact, state.card)
        state = state.event(IslandEvent.Timers(listOf(timer("t"), timer("a", IslandTimerKind.Alarm))), 10)
        assertEquals("a", state.timer?.key)
        assertEquals(IslandCard.TimerExpanded, state.card)
        state = state.event(IslandEvent.Collapse, 11).event(IslandEvent.Timers(listOf(timer("a", IslandTimerKind.Alarm))), 12)
        assertEquals(IslandCard.TimerCompact, state.card)
    }

    @Test fun onlyClockNotificationsBecomeActivities() {
        assertEquals(IslandTimerKind.Alarm, islandTimerKind("alarm", false, false, false, true))
        assertNull(islandTimerKind("alarm", true, false, false, false))
        assertEquals(IslandTimerKind.Timer, islandTimerKind(null, true, true, true, false))
        assertNull(islandTimerKind(null, false, true, true, false))
        assertNull(islandTimerKind("call", true, true, false, false))
        assertEquals(IslandTimerKind.Stopwatch, islandTimerKind("stopwatch", true, true, false, false))
    }

    @Test fun countersRoundLikeTheClockApp() {
        val down = IslandTimer("t", IslandTimerKind.Timer, baseMs = 100_000, countDown = true)
        assertEquals("5:00", down.copy(baseMs = 300_000).counter(200))
        assertEquals("0:01", down.counter(99_001))
        assertEquals("0:00", down.counter(120_000))
        val up = IslandTimer("s", IslandTimerKind.Stopwatch, baseMs = 0)
        assertEquals("1:01:01", up.counter(3_661_999))
        assertNull(IslandTimer("x", IslandTimerKind.Alarm).counter(5))
    }

    @Test fun aBareDuplicateSessionLosesToTheOneWithControls() {
        val bare = session(1).copy(commands = setOf(IslandCommand.Play, IslandCommand.Pause))
        val full = session(2).copy(commands = setOf(IslandCommand.Play, IslandCommand.Pause, IslandCommand.Seek))
        assertEquals(2L, selectIslandSession(listOf(bare, full), 1)?.id)
        assertEquals(1L, selectIslandSession(listOf(bare, full.copy(playback = IslandPlayback.Paused)), 2)?.id)
    }

    @Test fun alwaysOnKeepsAnIdleCapsuleAndTimersCanBeTurnedOff() {
        var state = ready().event(IslandEvent.Environment(environment.copy(alwaysOn = true)))
        assertEquals(IslandCard.Idle, state.card)
        state = state.event(IslandEvent.Sessions(listOf(session(1))))
        assertEquals(IslandCard.MusicCompact, state.card)
        state = state.event(IslandEvent.Environment(environment.copy(timersEnabled = false)))
            .event(IslandEvent.Timers(listOf(timer("t"))))
        assertEquals(IslandCard.MusicCompact, state.card)
        assertTrue(state.timers.isEmpty())
    }

    @Test fun collapseDelayFollowsTheSetting() {
        val state = ready().event(IslandEvent.Environment(environment.copy(collapseMs = 5_000)))
            .event(IslandEvent.Sessions(listOf(session(1)))).event(IslandEvent.Expand, 100)
        assertEquals(5_100L, state.collapseAt)
    }
}
