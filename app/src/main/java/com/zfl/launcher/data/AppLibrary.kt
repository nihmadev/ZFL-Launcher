package com.zfl.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import com.zfl.launcher.R
import com.zfl.launcher.settings.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

/** One App Library group of real apps. */
typealias AppGroup = LibraryGroup<AppInfo>

/** The App Library of real apps. */
typealias AppLibraryState = LibraryState<AppInfo>

/**
 * Group names in the language of [context] (an Activity, so the in-app language applies on
 * Android 12 too). System category names come from Android, already translated
 * ("Productivity" → «Работа»). Read again whenever the language changes.
 */
fun libraryTitles(context: Context): LibraryTitles = LibraryTitles(
    locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
    system = (0..MAX_SYSTEM_CATEGORY).mapNotNull { category ->
        ApplicationInfo.getCategoryTitle(context, category)?.toString()?.let { category to it }
    }.toMap(),
    other = context.getString(R.string.library_other),
    suggestions = context.getString(R.string.library_suggestions),
    recent = context.getString(R.string.library_recently_added),
    pinned = context.getString(R.string.library_pinned),
)

/** Highest category number asked from Android; unknown ones return no title and go to "Other". */
private const val MAX_SYSTEM_CATEGORY = 31

/** Visible (not hidden) apps with custom labels applied. */
fun visibleApps(apps: List<AppInfo>, prefs: SettingsState): List<AppInfo> =
    apps.filter { it.key !in prefs.hiddenApps }.map { app -> prefs.customLabels[app.key]?.let(app::withLabel) ?: app }

/**
 * The App Library, rebuilt off the main thread when the visible apps, the library settings,
 * usage, or the language ([titles]) change; other settings don't rebuild it. Shared by the
 * launcher and the settings.
 */
fun libraryStates(
    visible: Flow<List<AppInfo>>,
    settings: Flow<SettingsState>,
    scores: Flow<Map<String, Double>>,
    titles: Flow<LibraryTitles?>,
): Flow<AppLibraryState> = combine(
    visible,
    settings.map { it.library }.distinctUntilChanged(),
    scores,
    titles.filterNotNull(),
) { apps, config, usage, names -> buildLibrary(apps, usage, config, names) }.flowOn(Dispatchers.Default)
