package com.zfl.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.net.toUri
import com.zfl.launcher.R
import com.zfl.launcher.settings.LauncherSettings
import java.io.File

/**
 * Persists the home screen layout (pages, dock, folders) as JSON in the app's files dir.
 * A small JSON file is plenty for a few hundred items and keeps the project dependency-free.
 */
class LayoutStore(private val context: Context) {
    private val file = LayoutFile(File(context.filesDir, FILE_NAME))

    /** The saved layout, or null on first launch. */
    fun load(): LayoutData? = file.read(legacyGrid())

    fun save(layout: LayoutData): Unit = file.write(layout)

    /**
     * Grid of layouts saved before the grid was part of the layout file: four columns and the
     * rows chosen in the settings of those versions. Also the grid of a fresh install.
     */
    fun legacyGrid(): GridSpec =
        LauncherSettings.legacyGridRows(context)?.let { GridSpec.home(GridSpec.LEGACY_COLUMNS, it) } ?: GridSpec.Default

    /**
     * First-launch layout: an iOS-like dock (phone, messages, browser, camera) and every
     * other app in alphabetical order. Respects a dock saved by older versions.
     */
    fun initialLayout(apps: List<AppInfo>): LayoutData {
        val dockKeys = legacyDockKeys() ?: defaultDock(apps)
        val dock = dockKeys.filter { key -> apps.any { it.key == key } }.take(DEFAULT_DOCK_CAPACITY)
        val pages = apps.filter { it.key !in dock }.map { AppItem(it.key) }
        return LayoutData(pages = listOf(pages), dock = dock.map(::AppItem), grid = legacyGrid()).normalized()
    }

    /** Dock saved by v0.1 in SharedPreferences, migrated once into the layout file. */
    private fun legacyDockKeys(): List<String>? {
        val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
        val keys = prefs.getString("dock", null)?.split('\n')?.filter { it.isNotEmpty() }
        if (keys != null) prefs.edit { remove("dock") }
        return keys
    }

    private fun defaultDock(apps: List<AppInfo>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
            Intent(Intent.ACTION_VIEW, "https://example.com".toUri()),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
                ?: pm.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName
            apps.firstOrNull { it.packageName == pkg }?.key
        }.distinct()
    }

    /**
     * Name for a new folder, like iOS: the Play Store category of the app it was created
     * from ("Games", "Productivity", …), localized by the system; "Folder" otherwise.
     */
    fun folderNameFor(app: AppInfo?): String {
        val category = app?.info?.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        return ApplicationInfo.getCategoryTitle(context, category)?.toString()
            ?: context.getString(R.string.folder_default_name)
    }

    companion object {
        private const val FILE_NAME = "layout.json"

        /** Whether a Home Screen layout was ever saved on this install. */
        fun hasSavedLayout(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists()
    }
}
