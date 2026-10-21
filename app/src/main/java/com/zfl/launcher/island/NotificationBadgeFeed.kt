package com.zfl.launcher.island

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zfl.launcher.data.BadgeNotification
import com.zfl.launcher.data.countBadges
import com.zfl.launcher.data.profileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notification badge counts by [com.zfl.launcher.data.badgeKey], published by the connected
 * [IslandListenerService]. Lives in process memory only: empty while badges are off, the listener
 * is not connected or access was revoked, and rebuilt from the active notifications on connect.
 */
object NotificationBadgeFeed {
    private val mutable = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = mutable

    internal fun publish(value: Map<String, Int>): Unit {
        mutable.value = value
    }
}

/**
 * The listener's badge bookkeeping, independent of the Island window. Keeps the minimal
 * [BadgeNotification] of each posted notification by key (no text or extras are copied) and
 * recounts at most once per [COALESCE_MS], so a burst of notifications is one UI update.
 * Called on the main thread, like every listener callback.
 */
internal class BadgeTracker(private val service: NotificationListenerService) {
    private val main = Handler(Looper.getMainLooper())
    private val entries = LinkedHashMap<String, BadgeNotification>()
    private val ranking = NotificationListenerService.Ranking()
    private var enabled = false
    private var connected = false
    private val flush = Runnable { publish() }

    /** Badges turned on or off in settings. Off drops everything and ignores events. */
    fun enabled(value: Boolean, active: () -> Array<StatusBarNotification>?): Unit {
        if (enabled == value) return
        enabled = value
        if (value && connected) reset(active()) else clear()
    }

    fun connected(active: Array<StatusBarNotification>?): Unit {
        connected = true
        if (enabled) reset(active)
    }

    fun disconnected(): Unit {
        connected = false
        clear()
    }

    fun posted(sbn: StatusBarNotification): Unit {
        if (!enabled || !connected) return
        entries[sbn.key] = sbn.badgeNotification()
        schedule()
    }

    fun removed(sbn: StatusBarNotification): Unit {
        if (!enabled || !connected) return
        if (entries.remove(sbn.key) != null) schedule()
    }

    /** A channel's badge setting may have changed: [publish] reads the current ranking. */
    fun rankingChanged(): Unit {
        if (enabled && connected && entries.isNotEmpty()) schedule()
    }

    private fun reset(active: Array<StatusBarNotification>?) {
        entries.clear()
        active?.forEach { entries[it.key] = it.badgeNotification() }
        Log.d(TAG, "rebuilt from ${entries.size} active notifications")
        main.removeCallbacks(flush)
        publish()
    }

    /** Badges disappear at once rather than freezing with old numbers. */
    private fun clear() {
        entries.clear()
        main.removeCallbacks(flush)
        NotificationBadgeFeed.publish(emptyMap())
    }

    private fun schedule() {
        if (!main.hasCallbacks(flush)) main.postDelayed(flush, COALESCE_MS)
    }

    private fun publish() {
        val rankings = runCatching { service.currentRanking }.getOrNull()
        val snapshot = entries.values.map { notification ->
            val badged = rankings == null || !rankings.getRanking(notification.key, ranking) || ranking.canShowBadge()
            if (badged) notification else notification.copy(canShowBadge = false)
        }
        NotificationBadgeFeed.publish(countBadges(snapshot, service.packageName))
    }

    private fun StatusBarNotification.badgeNotification(): BadgeNotification {
        val extras = notification.extras
        return BadgeNotification(
            key = key,
            packageName = packageName,
            profile = profileId(user),
            number = notification.number,
            flags = notification.flags,
            media = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || extras.getString(Notification.EXTRA_TEMPLATE) in MEDIA_TEMPLATES,
            groupKey = groupKey,
        )
    }

    private companion object {
        const val TAG = "ZflBadges"
        const val COALESCE_MS = 250L
        val MEDIA_TEMPLATES = setOf(
            "android.app.Notification\$MediaStyle",
            "android.app.Notification\$DecoratedMediaCustomViewStyle",
        )
    }
}
