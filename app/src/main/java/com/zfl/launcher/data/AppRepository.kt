package com.zfl.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.Collator

/** A launchable activity of an installed app, for a specific user profile (personal / work). */
class AppInfo(
    val info: LauncherActivityInfo,
    val label: String,
) {
    val component: ComponentName get() = info.componentName
    val user: UserHandle get() = info.user
    val packageName: String get() = component.packageName

    /** When the app was installed (for "Recently Added" in the App Library). */
    val installTime: Long get() = info.firstInstallTime

    /** System app category ([android.content.pm.ApplicationInfo.category]) for App Library grouping. */
    val category: Int get() = info.applicationInfo.category

    /** Stable id used to persist the home screen layout. */
    val key: String = "${component.flattenToString()}#${info.user.hashCode()}"
}

/**
 * Source of truth for installed apps. Keeps [apps] up to date by listening
 * to [LauncherApps] package events (install, update, uninstall, SD card).
 */
class AppRepository(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    /**
     * Re-reads the app list from the system. Our own settings activity is included on purpose:
     * it is how users open launcher settings, like the "LauncherOS" icon in iOS27L.
     */
    fun reload() {
        val collator = Collator.getInstance()
        _apps.value = userManager.userProfiles
            .flatMap { launcherApps.getActivityList(null, it) }
            .map { AppInfo(it, it.label.toString()) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * App shortcuts for the long-press menu (e.g. "New tab" in a browser).
     * Android only exposes them to the current default launcher, otherwise this is empty.
     */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching { launcherApps.getShortcuts(query, app.user).orEmpty() }
            .getOrDefault(emptyList())
            .sortedBy { it.rank }
            .take(4)
    }

    /** Icon of an app shortcut, or `null` if it has none. */
    fun shortcutIcon(shortcut: ShortcutInfo, densityDpi: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()

    /** Must be called when the owner is destroyed. */
    fun close(): Unit = launcherApps.unregisterCallback(callback)
}
