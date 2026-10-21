package com.zfl.launcher.island

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import java.util.Locale

enum class IslandTimerKind { Timer, Stopwatch, Alarm }

/**
 * A clock live activity: a running timer or stopwatch, or a ringing alarm. Only these fields are
 * taken from the clock's notification; [actions] are its button labels in order.
 */
data class IslandTimer(
    val key: String,
    val kind: IslandTimerKind,
    val title: String? = null,
    val text: String? = null,
    /** Wall-clock chronometer base (`Notification.when`), or null when the clock shows no counter. */
    val baseMs: Long? = null,
    val countDown: Boolean = false,
    val actions: List<String> = emptyList(),
    val canOpen: Boolean = false,
)

/** Pending intents stay with the runtime; the model only knows labels and indices. */
internal class IslandTimerEntry(
    val timer: IslandTimer,
    val content: PendingIntent?,
    val actions: List<PendingIntent>,
)

/**
 * Which notifications are clock activities: a ringing alarm (alarm category with a full-screen
 * intent), an ongoing count-down chronometer (timer) or an ongoing stopwatch. Nothing else.
 */
internal fun islandTimerKind(
    category: String?, ongoing: Boolean, chronometer: Boolean, countDown: Boolean, fullScreen: Boolean,
): IslandTimerKind? = when {
    category == Notification.CATEGORY_ALARM && fullScreen -> IslandTimerKind.Alarm
    ongoing && chronometer && countDown -> IslandTimerKind.Timer
    ongoing && category == Notification.CATEGORY_STOPWATCH -> IslandTimerKind.Stopwatch
    else -> null
}

internal fun islandTimerEntry(sbn: StatusBarNotification, ownPackage: String): IslandTimerEntry? {
    if (sbn.packageName == ownPackage) return null
    val notification = sbn.notification ?: return null
    val extras = notification.extras
    val chronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
    val countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
    val kind = islandTimerKind(notification.category, sbn.isOngoing, chronometer, countDown,
        notification.fullScreenIntent != null) ?: return null
    val actions = notification.actions.orEmpty().filter { it.actionIntent != null && !it.title.isNullOrBlank() }.take(MAX_ACTIONS)
    return IslandTimerEntry(
        IslandTimer(
            key = sbn.key,
            kind = kind,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(MAX_TEXT),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(MAX_TEXT),
            baseMs = notification.`when`.takeIf { chronometer && it > 0 },
            countDown = countDown,
            actions = actions.map { it.title.toString().take(MAX_TEXT) },
            canOpen = notification.contentIntent != null,
        ),
        notification.contentIntent,
        actions.map { it.actionIntent },
    )
}

/** Alarms first (they need an answer), then system order. */
internal fun orderIslandTimers(timers: List<IslandTimer>): List<IslandTimer> =
    timers.sortedBy { if (it.kind == IslandTimerKind.Alarm) 0 else 1 }

/** Chronometer text like the clock app shows it, or null without a running counter. */
fun IslandTimer.counter(nowMs: Long): String? {
    val base = baseMs ?: return null
    val ms = if (countDown) base - nowMs else nowMs - base
    // A count-down shows the started second, like Chronometer: 4:59.2 is "5:00".
    val seconds = if (countDown) ((ms.coerceAtLeast(0) + 999) / 1000) else ms.coerceAtLeast(0) / 1000
    return islandDuration(seconds)
}

fun islandDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

private const val MAX_ACTIONS = 3
private const val MAX_TEXT = 256
