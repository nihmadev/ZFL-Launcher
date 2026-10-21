package com.zfl.launcher.island

/** IDs identify session tokens for this runtime, never packages or persisted identities. */
enum class IslandPlayback { Playing, Buffering, Paused, Inactive }

enum class IslandCommand { Play, Pause, Previous, Next, Seek }

data class IslandSession(
    val id: Long,
    val playback: IslandPlayback,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionAtMs: Long = 0,
    val speed: Float = 0f,
    val commands: Set<IslandCommand> = emptySet(),
    val canOpen: Boolean = false,
    val artworkVersion: Long = 0,
) {
    val hasProgress: Boolean get() = durationMs != null && durationMs > 0 && positionMs != null && positionMs >= 0
    val canSeek: Boolean get() = hasProgress && IslandCommand.Seek in commands
}

/** Extrapolate only actual playback, in the same monotonic time base as PlaybackState. */
fun IslandSession.progressPosition(nowMs: Long): Long? {
    if (!hasProgress) return null
    val elapsed = if (playback == IslandPlayback.Playing && speed.isFinite() && positionAtMs > 0) {
        (nowMs - positionAtMs).coerceAtLeast(0) * speed.toDouble()
    } else 0.0
    return (positionMs!!.toDouble() + elapsed).coerceIn(0.0, durationMs!!.toDouble()).toLong()
}

/**
 * Playing wins. Within a rank the session offering the most controls wins: some apps (Telegram)
 * keep a bare duplicate session that ignores commands. Ties keep the current token, then system order.
 */
fun selectIslandSession(sessions: List<IslandSession>, currentId: Long?): IslandSession? {
    fun rank(session: IslandSession): Int = when (session.playback) {
        IslandPlayback.Playing -> 0
        IslandPlayback.Buffering -> 1
        IslandPlayback.Paused -> 2
        IslandPlayback.Inactive -> 3
    }
    val candidates = sessions.filter { it.playback != IslandPlayback.Inactive }
    val best = candidates.minOfOrNull(::rank) ?: return null
    val ranked = candidates.filter { rank(it) == best }
    val richest = ranked.maxOf { it.commands.size }
    val rich = ranked.filter { it.commands.size == richest }
    return rich.firstOrNull { it.id == currentId } ?: rich.first()
}
