package com.zfl.launcher.data

/** Inserts an app before the chosen neighbor, or at the end, remembering absent apps too. */
fun LibraryConfig.placeInGrid(appKey: String, beforeKey: String?, shown: List<String>): LibraryConfig {
    if (appKey !in shown || beforeKey == appKey || (beforeKey != null && beforeKey !in shown)) return this
    val rest = shown.distinct().filter { it != appKey }.toMutableList()
    val index = beforeKey?.let(rest::indexOf) ?: rest.size
    rest.add(index, appKey)
    val visible = shown.toSet()
    return copy(gridOrder = rest + gridOrder.filter { it !in visible })
}

/** Chooses both the category and its exact insertion position without changing the view. */
fun LibraryConfig.placeInGroup(appKey: String, groupId: String, beforeKey: String?, shown: List<String>): LibraryConfig {
    if (beforeKey == appKey || (beforeKey != null && beforeKey !in shown)) return this
    val rest = shown.distinct().filter { it != appKey }.toMutableList()
    rest.add(beforeKey?.let(rest::indexOf) ?: rest.size, appKey)
    if (groupId == LibraryIds.PINNED) return copy(pinned = rest + pinned.filter { it !in rest && it !in shown })
    val assigned = assign(appKey, groupId)
    if (assigned.assignments[appKey] != groupId) return this
    val hidden = groupAppOrder[groupId].orEmpty().filter { it !in rest && it !in shown }
    return assigned.copy(groupAppOrder = assigned.groupAppOrder + (groupId to (rest + hidden)))
}

/** Saved apps keep their positions; newly visible apps follow in their automatic order. */
fun <T : LibraryApp> orderedLibraryGrid(apps: List<T>, saved: List<String>): List<T> {
    val byKey = apps.associateBy { it.key }
    val placed = saved.toSet()
    return saved.distinct().mapNotNull(byKey::get) + apps.filter { it.key !in placed }
}
