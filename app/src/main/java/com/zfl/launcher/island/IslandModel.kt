package com.zfl.launcher.island

/** Monotonic deadlines; Android owns one scheduled wake-up for the earliest live deadline. */
object IslandTiming {
    const val PAUSED_MS: Long = 30_000
    const val CHARGING_MS: Long = 4_000
    const val COLLAPSE_MS: Long = 12_000
    const val PROGRESS_MS: Long = 1_000
}

data class IslandEnvironment(
    val enabled: Boolean = false,
    val windowGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val connected: Boolean = false,
    val chargingEnabled: Boolean = false,
    val timersEnabled: Boolean = true,
    /** Keep an empty capsule around the camera when nothing is live. */
    val alwaysOn: Boolean = false,
    val collapseMs: Long = IslandTiming.COLLAPSE_MS,
) {
    val ready: Boolean get() = enabled && windowGranted && notificationGranted && connected
}

enum class IslandCard {
    Hidden, Idle, MusicCompact, MusicExpanded, Charging, TimerCompact, TimerExpanded;

    val expanded: Boolean get() = this == MusicExpanded || this == TimerExpanded
}

data class IslandModel(
    val environment: IslandEnvironment = IslandEnvironment(),
    val interactive: Boolean = false,
    val unlocked: Boolean = false,
    val spaceAvailable: Boolean = true,
    val expansionAllowed: Boolean = true,
    val sessions: List<IslandSession> = emptyList(),
    val selectedId: Long? = null,
    val pausedSince: Map<Long, Long> = emptyMap(),
    val collapseAt: Long? = null,
    /** What [collapseAt] expanded: the clock activity or the music. */
    val expandedTimer: Boolean = false,
    val timers: List<IslandTimer> = emptyList(),
    val plugged: Boolean? = null,
    val batteryPercent: Int? = null,
    val chargingUntil: Long? = null,
) {
    val selected: IslandSession? get() = sessions.firstOrNull { it.id == selectedId }
    val timer: IslandTimer? get() = timers.firstOrNull()
    val visible: Boolean get() = environment.ready && interactive && unlocked && spaceAvailable
    val card: IslandCard get() = when {
        !visible -> IslandCard.Hidden
        collapseAt != null && expandedTimer && timer != null -> IslandCard.TimerExpanded
        collapseAt != null && !expandedTimer && selected != null -> IslandCard.MusicExpanded
        chargingUntil != null -> IslandCard.Charging
        timer != null -> IslandCard.TimerCompact
        selected != null -> IslandCard.MusicCompact
        environment.alwaysOn -> IslandCard.Idle
        else -> IslandCard.Hidden
    }

    fun nextDeadline(): Long? {
        if (!visible) return null
        val pausedDeadline = selectedId?.let { pausedSince[it] }?.plus(IslandTiming.PAUSED_MS)
        return listOfNotNull(collapseAt, chargingUntil, pausedDeadline).minOrNull()
    }
}

sealed interface IslandEvent {
    data class Environment(val value: IslandEnvironment) : IslandEvent
    data class Visibility(val interactive: Boolean, val unlocked: Boolean) : IslandEvent
    data class Space(val available: Boolean, val expansionAllowed: Boolean) : IslandEvent
    data class Sessions(val value: List<IslandSession>) : IslandEvent
    data class Timers(val value: List<IslandTimer>) : IslandEvent
    data class Battery(val plugged: Boolean, val percent: Int?, val baseline: Boolean = false) : IslandEvent
    /** Expands the compact card that is shown. */
    data object Expand : IslandEvent
    data object Collapse : IslandEvent
    data object Interaction : IslandEvent
    data object Time : IslandEvent
}

/** Single authority for selection, battery deduplication, visibility and presentation. No Android or clock reads. */
fun reduceIsland(previous: IslandModel, event: IslandEvent, nowMs: Long): IslandModel {
    var state = when (event) {
        is IslandEvent.Environment -> previous.copy(environment = event.value)
        is IslandEvent.Visibility -> previous.copy(interactive = event.interactive, unlocked = event.unlocked)
        is IslandEvent.Space -> previous.copy(spaceAvailable = event.available, expansionAllowed = event.expansionAllowed)
        else -> previous
    }
    if (!state.environment.ready) return IslandModel(environment = state.environment)
    state = when (event) {
        is IslandEvent.Sessions -> {
            val paused = event.value.filter { it.playback == IslandPlayback.Paused }.associate {
                it.id to (state.pausedSince[it.id] ?: nowMs)
            }
            state.copy(sessions = event.value, pausedSince = paused)
        }
        is IslandEvent.Timers -> {
            val timers = orderIslandTimers(event.value)
            val known = state.timers.map { it.key }.toSet()
            // A newly ringing alarm opens itself, like an incoming iOS alert.
            val ringing = timers.firstOrNull()?.takeIf { it.kind == IslandTimerKind.Alarm && it.key !in known }
            if (ringing != null && state.visible && state.expansionAllowed) {
                state.copy(timers = timers, collapseAt = nowMs + state.environment.collapseMs, expandedTimer = true, chargingUntil = null)
            } else state.copy(timers = timers)
        }
        is IslandEvent.Battery -> {
            val newConnection = !event.baseline && state.plugged == false && event.plugged
            state.copy(
                plugged = event.plugged,
                batteryPercent = event.percent?.coerceIn(0, 100),
                chargingUntil = when {
                    !event.plugged -> null
                    newConnection && state.visible && state.environment.chargingEnabled && event.percent != null &&
                        state.collapseAt == null -> nowMs + IslandTiming.CHARGING_MS
                    else -> state.chargingUntil
                },
            )
        }
        IslandEvent.Expand -> {
            val card = state.card
            if ((card == IslandCard.MusicCompact || card == IslandCard.TimerCompact) && state.expansionAllowed) {
                state.copy(collapseAt = nowMs + state.environment.collapseMs, expandedTimer = card == IslandCard.TimerCompact, chargingUntil = null)
            } else state
        }
        IslandEvent.Collapse -> state.copy(collapseAt = null)
        IslandEvent.Interaction -> if (state.collapseAt != null) state.copy(collapseAt = nowMs + state.environment.collapseMs) else state
        else -> state
    }
    val eligible = state.sessions.filter {
        it.playback != IslandPlayback.Paused || nowMs < (state.pausedSince[it.id] ?: nowMs) + IslandTiming.PAUSED_MS
    }
    val selected = selectIslandSession(eligible, state.selectedId)
    if (!state.environment.timersEnabled && state.timers.isNotEmpty()) state = state.copy(timers = emptyList())
    val expandedContent = if (state.expandedTimer) state.timer != null else selected != null
    // Keep expired paused timestamps to prevent metadata/battery callbacks resurrecting a paused session.
    state = state.copy(
        selectedId = selected?.id,
        collapseAt = state.collapseAt?.takeIf { expandedContent && state.visible && state.expansionAllowed && it > nowMs },
        chargingUntil = state.chargingUntil?.takeIf { state.visible && state.environment.chargingEnabled && it > nowMs },
    )
    if (!state.visible) state = state.copy(collapseAt = null, chargingUntil = null)
    return state
}
