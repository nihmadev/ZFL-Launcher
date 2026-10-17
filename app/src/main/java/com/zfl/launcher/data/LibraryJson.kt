package com.zfl.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * LibraryConfig as one JSON value in the settings. Reading is lenient: unknown enum names
 * (from a newer version) fall back to the defaults, broken entries are skipped, and sizes are
 * bounded, so a damaged value never takes the rest of the settings down with it.
 */

fun LibraryConfig.toJson(): String = JSONObject()
    .put("view", view.name)
    .put("suggestions", showSuggestions)
    .put("recent", showRecent)
    .put("newApps", newApps.name)
    .put("categories", JSONArray(categories.map { JSONObject().put("id", it.id).put("name", it.name) }))
    .put("assignments", JSONArray(assignments.map { (app, group) -> JSONObject().put("app", app).put("group", group) }))
    .put("pinned", JSONArray(pinned))
    .put("order", JSONArray(groupOrder))
    .toString()

/** Parses [toJson] output; defaults for a missing or unreadable value. */
fun libraryConfigFromJson(json: String?): LibraryConfig {
    if (json == null) return LibraryConfig()
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return LibraryConfig()
    val defaults = LibraryConfig()
    val categories = root.optJSONArray("categories").objects()
        .mapNotNull { obj ->
            val id = obj.optString("id").takeIf(LibraryIds::isUser) ?: return@mapNotNull null
            val name = obj.optString("name").trim().take(MAX_CATEGORY_NAME).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            UserCategory(id, name)
        }
        .distinctBy { it.id }
        .take(MAX_USER_CATEGORIES)
    // Kept in their saved order (oldest first), which withOrphansCapped relies on.
    val assignments = LinkedHashMap<String, String>()
    root.optJSONArray("assignments").objects().forEach { obj ->
        val app = obj.optString("app").takeIf { it.isNotEmpty() } ?: return@forEach
        val group = obj.optString("group").takeIf { it.isNotEmpty() } ?: return@forEach
        assignments[app] = group
    }
    return LibraryConfig(
        view = enumOrNull<LibraryView>(root.optString("view")) ?: defaults.view,
        showSuggestions = root.optBoolean("suggestions", defaults.showSuggestions),
        showRecent = root.optBoolean("recent", defaults.showRecent),
        newApps = enumOrNull<NewAppPlacement>(root.optString("newApps")) ?: defaults.newApps,
        categories = categories,
        assignments = assignments.entries.toList().takeLast(MAX_STORED_ENTRIES).associate { it.key to it.value },
        pinned = root.optJSONArray("pinned").strings().distinct().takeLast(MAX_STORED_ENTRIES),
        groupOrder = root.optJSONArray("order").strings().distinct().take(MAX_STORED_ENTRIES),
    )
}

/** Hard bound on any list read back, far above real use (see [MAX_REMEMBERED_MISSING]). */
private const val MAX_STORED_ENTRIES = 2000

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it, "") }.filter { it.isNotEmpty() }
