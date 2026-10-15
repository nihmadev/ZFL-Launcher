package com.zfl.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import com.zfl.launcher.R

/** One App Library group: a system category, or one of the smart groups at the top. */
data class LibraryCategory(
    val id: String,
    val title: String,
    val apps: List<AppInfo>,
)

/**
 * Groups every visible app like the iOS App Library: "Suggestions" (most used),
 * "Recently Added", then one group per system app category (largest first), "Other" last.
 * Category names come from Android, already translated ("Productivity" → «Работа»).
 */
fun buildAppLibrary(
    context: Context,
    apps: List<AppInfo>,
    scores: Map<String, Double>,
): List<LibraryCategory> {
    if (apps.isEmpty()) return emptyList()
    fun List<AppInfo>.byUsage() = sortedWith(compareByDescending<AppInfo> { scores[it.key] ?: 0.0 }.thenBy { it.label.lowercase() })

    val smart = buildList {
        val used = apps.filter { (scores[it.key] ?: 0.0) > 0.0 }
        if (used.isNotEmpty()) add(LibraryCategory("suggestions", context.getString(R.string.library_suggestions), used.byUsage().take(SMART_GROUP_SIZE)))
        val recent = apps.filter { it.installTime > 0 }.sortedByDescending { it.installTime }.take(SMART_GROUP_SIZE)
        if (recent.isNotEmpty()) add(LibraryCategory("recent", context.getString(R.string.library_recently_added), recent))
    }

    val byCategory = apps.groupBy { it.category }
    val categories = byCategory
        .filterKeys { it != ApplicationInfo.CATEGORY_UNDEFINED }
        .map { (category, members) ->
            LibraryCategory(
                id = "category-$category",
                title = ApplicationInfo.getCategoryTitle(context, category)?.toString() ?: context.getString(R.string.library_other),
                apps = members.byUsage(),
            )
        }
        .sortedByDescending { it.apps.size }
    val other = byCategory[ApplicationInfo.CATEGORY_UNDEFINED]
        ?.let { LibraryCategory("other", context.getString(R.string.library_other), it.byUsage()) }

    return smart + categories + listOfNotNull(other)
}

private const val SMART_GROUP_SIZE = 8
