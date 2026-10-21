package com.zfl.launcher.island

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.zfl.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * System-bound owner. No started service, foreground service or restart loop. Of all
 * notifications only clock activities (timers, stopwatches, ringing alarms) are read,
 * see [islandTimerEntry]; nothing is stored or leaves the process.
 */
class IslandListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runtime: IslandRuntime? = null
    private val timers = LinkedHashMap<String, IslandTimerEntry>()

    override fun onCreate(): Unit {
        super.onCreate()
        scope.launch {
            combine(LauncherSettings.get(this@IslandListenerService).state, IslandAccess.state) { settings, grants ->
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
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn ->
            islandTimerEntry(sbn, packageName)?.let { timers[sbn.key] = it }
        }
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
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification): Unit {
        if (timers.remove(sbn.key) != null) runtime?.timers(timers.values.toList())
    }

    override fun onListenerDisconnected(): Unit {
        runtime?.close()
        runtime = null
        timers.clear()
        IslandAccess.connection(this, false)
        super.onListenerDisconnected()
    }

    override fun onDestroy(): Unit {
        runtime?.close()
        runtime = null
        timers.clear()
        scope.cancel()
        IslandAccess.connection(this, false)
        super.onDestroy()
    }
}
