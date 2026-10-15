package com.zfl.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserManager
import com.zfl.launcher.R

/** A launcher whose Home Screen layout can be imported. */
data class ImportSource(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** Content provider authority of its layout database. */
    val authority: String,
)

/** What an import would produce, so the user can review it before applying. */
data class ImportPreview(
    val source: ImportSource,
    /** Layout with only the imported items (no "removed" apps yet). */
    val layout: LayoutData,
    val pages: Int,
    val folders: Int,
    val appsOnHome: Int,
    /** Installed apps that the other launcher didn't have on its Home Screen. */
    val appsNotOnHome: Set<String>,
)

/**
 * Imports the Home Screen of launchers based on AOSP Launcher3 (iOS27L, Lawnchair, many
 * OEM and themed launchers). They keep their layout in a `favorites` table exposed by
 * `LauncherProvider` at `content://<package>.settings/favorites`.
 *
 * Imported: home pages, folders (with names and order) and the dock. Widgets and pinned
 * shortcuts are skipped. Apps are matched by component and user profile.
 */
class LauncherImporter(private val context: Context) {
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Installed launchers that expose a Launcher3 layout provider. */
    fun findSources(): List<ImportSource> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val authority = "$pkg.settings"
                pm.resolveContentProvider(authority, 0) ?: return@mapNotNull null
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                ImportSource(
                    packageName = pkg,
                    label = appInfo?.loadLabel(pm)?.toString() ?: pkg,
                    icon = appInfo?.loadIcon(pm),
                    authority = authority,
                )
            }
    }

    /**
     * Reads [source]'s layout and maps it onto [apps] (the installed, visible apps).
     *
     * @throws SecurityException if the launcher protects its layout database.
     */
    fun read(source: ImportSource, apps: List<AppInfo>, pageCapacity: Int, dockCapacity: Int): ImportPreview {
        val rows = queryFavorites(source.authority)
        val screenOrder = queryScreenOrder(source.authority)

        val byComponent = apps.associateBy { it.component to it.user }
        val selfApp = apps.firstOrNull { it.packageName == context.packageName }
        val used = mutableSetOf<String>()

        /** Matches a row to an installed app; each app is used once (duplicates are dropped). */
        fun appKey(row: FavoriteRow): String? {
            val component = row.component ?: return null
            val user = userManager.getUserForSerialNumber(row.profileId) ?: android.os.Process.myUserHandle()
            val app = when (component.packageName) {
                // A shortcut to this launcher in the other one: point it to our settings app.
                context.packageName -> selfApp
                else -> byComponent[component to user]
                    ?: apps.firstOrNull { it.packageName == component.packageName && it.user == user }
            } ?: return null
            return app.key.takeIf { used.add(it) }
        }

        fun item(row: FavoriteRow): HomeItem? = when (row.itemType) {
            ITEM_TYPE_APPLICATION -> appKey(row)?.let(::AppItem)
            ITEM_TYPE_FOLDER -> {
                val folderApps = rows.filter { it.container == row.id }
                    .sortedBy { it.rank }
                    .mapNotNull(::appKey)
                when (folderApps.size) {
                    0 -> null
                    1 -> AppItem(folderApps.single())
                    else -> FolderItem("folder-import-${row.id}", row.title.orEmpty().ifBlank { context.getString(R.string.folder_default_name) }, folderApps)
                }
            }
            else -> null // widgets, pinned shortcuts, app pairs
        }

        val pages = rows.filter { it.container == CONTAINER_DESKTOP }
            .groupBy { it.screen }
            .toSortedMap(compareBy { screenOrder[it] ?: (Int.MAX_VALUE / 2 + it) })
            .values
            .map { page -> page.sortedWith(compareBy({ it.cellY }, { it.cellX })).mapNotNull(::item) }
            .filter { it.isNotEmpty() }
        val dock = rows.filter { it.container == CONTAINER_HOTSEAT }
            .sortedWith(compareBy({ it.screen }, { it.cellX }))
            .mapNotNull(::item)

        // Dock items that don't fit go to the last page instead of being dropped.
        val layout = LayoutData(pages = pages.ifEmpty { listOf(emptyList()) }, dock = dock).normalized(pageCapacity, dockCapacity)
        val placed = layout.allAppKeys()
        return ImportPreview(
            source = source,
            layout = layout,
            pages = layout.pages.count { it.isNotEmpty() },
            folders = (layout.pages.flatten() + layout.dock).count { it is FolderItem },
            appsOnHome = placed.size,
            appsNotOnHome = apps.map { it.key }.filterTo(mutableSetOf()) { it !in placed },
        )
    }

    private fun queryFavorites(authority: String): List<FavoriteRow> {
        val uri = Uri.parse("content://$authority/favorites")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: error("No layout data")
        return cursor.use { c -> buildList { while (c.moveToNext()) add(FavoriteRow.from(c)) } }
    }

    /** Older Launcher3 versions keep page order in `workspaceScreens`; newer ones use the screen id. */
    private fun queryScreenOrder(authority: String): Map<Int, Int> = runCatching {
        val uri = Uri.parse("content://$authority/workspaceScreens")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow("_id")
            val rank = c.getColumnIndexOrThrow("screenRank")
            buildMap { while (c.moveToNext()) put(c.getInt(id), c.getInt(rank)) }
        }
    }.getOrNull().orEmpty()

    /** One row of Launcher3's `favorites` table (only the columns we need). */
    private data class FavoriteRow(
        val id: Int,
        val title: String?,
        val component: ComponentName?,
        val container: Int,
        val screen: Int,
        val cellX: Int,
        val cellY: Int,
        val itemType: Int,
        val rank: Int,
        val profileId: Long,
    ) {
        companion object {
            fun from(c: Cursor): FavoriteRow {
                fun int(name: String): Int = c.getColumnIndex(name).takeIf { it >= 0 }?.let(c::getInt) ?: 0
                fun string(name: String): String? = c.getColumnIndex(name).takeIf { it >= 0 }?.let(c::getString)
                val intent = string("intent")?.let { runCatching { Intent.parseUri(it, 0) }.getOrNull() }
                return FavoriteRow(
                    id = int("_id"),
                    title = string("title"),
                    component = intent?.component,
                    container = int("container"),
                    screen = int("screen"),
                    cellX = int("cellX"),
                    cellY = int("cellY"),
                    itemType = int("itemType"),
                    rank = int("rank"),
                    profileId = c.getColumnIndex("profileId").takeIf { it >= 0 }?.let(c::getLong) ?: 0L,
                )
            }
        }
    }

    private companion object {
        // Values from Launcher3's LauncherSettings.Favorites.
        const val CONTAINER_DESKTOP = -100
        const val CONTAINER_HOTSEAT = -101
        const val ITEM_TYPE_APPLICATION = 0
        const val ITEM_TYPE_FOLDER = 2
    }
}

/** Turns a reviewed import into the new layout, optionally taking every other app off the Home Screen. */
fun ImportPreview.toLayout(removeOtherApps: Boolean): LayoutData =
    if (removeOtherApps) layout.copy(removed = appsNotOnHome) else layout
