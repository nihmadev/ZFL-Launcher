package com.zfl.launcher.island

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class IslandGrants(
    /** The Island accessibility service is connected and can show the window. */
    val window: Boolean = false,
    val notifications: Boolean = false,
    val connected: Boolean = false,
)

/** Live process-local access, never a preferences store and never an Activity reference. */
object IslandAccess {
    private val mutable = MutableStateFlow(IslandGrants())
    val state: StateFlow<IslandGrants> = mutable

    /** Context of the connected [IslandWindowService]: the only one that may add the Island window. */
    @Volatile
    var windowContext: Context? = null
        private set

    fun component(context: Context): ComponentName = ComponentName(context, IslandListenerService::class.java)

    fun read(context: Context): IslandGrants = IslandGrants(
        window = windowContext != null,
        notifications = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context)),
        connected = mutable.value.connected,
    )

    fun refresh(context: Context): Unit {
        mutable.value = read(context)
    }

    internal fun connection(context: Context, connected: Boolean): Unit {
        mutable.value = read(context).copy(connected = connected)
    }

    /** [service] connected ([context] = itself) or went away (null); a stale instance cannot clear a newer one. */
    internal fun windowService(service: IslandWindowService, context: Context?): Unit {
        if (context == null && windowContext !== service) return
        windowContext = context
        refresh(service)
    }

    /**
     * Only called by an explicit settings action; never a periodic reconnection loop. Some
     * systems (MIUI) ignore [NotificationListenerService.requestRebind] after an update, so the
     * listener component is also switched off and back to its manifest state, which makes the
     * notification manager bind it again.
     */
    fun requestConnection(context: Context): Unit {
        refresh(context)
        if (mutable.value.notifications && !mutable.value.connected) {
            val component = component(context)
            val packages = context.packageManager
            packages.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            packages.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            NotificationListenerService.requestRebind(component)
        }
    }
}
