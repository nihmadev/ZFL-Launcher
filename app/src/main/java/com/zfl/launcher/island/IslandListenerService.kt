package com.zfl.launcher.island

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.zfl.launcher.settings.BadgeStyle
import com.zfl.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * System-bound owner. No started service, foreground service or restart loop. Two independent
 * consumers: the Island reads clock activities (timers, stopwatches, ringing alarms, see
 * [islandTimerEntry]) and runs only under its own settings; badges ([BadgeTracker]) only count
 * notifications per app while they are turned on. Each filters in code, since the listener
 * receives every notification type. Nothing is stored or leaves the process.
 */
class IslandListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runtime: IslandRuntime? = null
    private val timers = LinkedHashMap<String, IslandTimerEntry>()
    private val badges = BadgeTracker(this)

    override fun onCreate(): Unit {
        super.onCreate()
        val settings = LauncherSettings.get(this).state
        scope.launch {
            settings.map { it.badges != BadgeStyle.Off }.distinctUntilChanged().collect { enabled ->
                badges.enabled(enabled) { runCatching { activeNotifications }.getOrNull() }
            }
        }
        scope.launch {
            combine(settings, IslandAccess.state) { settings, grants ->
                settings.island.environment(grants.window, grants.notifications, grants.connected) to settings.island.style()
            }.collect { (environment, style) ->
                if (!environment.ready) {
                    runtime?.close()
                    runtime = null
                } else {
                    val window = IslandAccess.windowContext ?: return@collect
                    val current = runtime ?: IslandRuntime(applicationContext, window) {
                        IslandAccess.connection(this@IslandListenerService, false)
                    }.also { runtime = it }
                    current.style(style)
                    current.environment(environment)
                    current.timers(timers.values.toList())
                }
            }
        }
    }

    override fun onListenerConnected(): Unit {
        super.onListenerConnected()
        timers.clear()
        val active = runCatching { activeNotifications }.getOrNull()
        active?.forEach { sbn ->
            islandTimerEntry(sbn, packageName)?.let { timers[sbn.key] = it }
        }
        badges.connected(active)
        IslandAccess.connection(this, true)
        runtime?.timers(timers.values.toList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification): Unit {
        val entry = islandTimerEntry(sbn, packageName)
        val changed = if (entry != null) {
            timers[sbn.key] = entry
            true
        } else timers.remove(sbn.key) != null
        if (changed) runtime?.timers(timers.values.toList())
        badges.posted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification): Unit {
        if (timers.remove(sbn.key) != null) runtime?.timers(timers.values.toList())
        badges.removed(sbn)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap): Unit {
        badges.rankingChanged()
    }

    override fun onListenerDisconnected(): Unit {
        runtime?.close()
        runtime = null
        timers.clear()
        badges.disconnected()
        IslandAccess.connection(this, false)
        super.onListenerDisconnected()
    }

    override fun onDestroy(): Unit {
        runtime?.close()
        runtime = null
        timers.clear()
        badges.disconnected()
        scope.cancel()
        IslandAccess.connection(this, false)
        super.onDestroy()
    }
}
