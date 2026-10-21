package com.zfl.launcher.ui.home

/** Logical pages are independent of optional pager neighbors. */
sealed interface HomePage {
    data object Today : HomePage
    data class Desktop(val ordinal: Int) : HomePage
    data object Library : HomePage
}

data class HomePages(val homes: Int, val today: Boolean, val library: Boolean) {
    val homeCount: Int get() = homes.coerceAtLeast(1)
    val firstHome: Int get() = if (today) 1 else 0
    val libraryPosition: Int get() = if (library) firstHome + homeCount else -1
    val count: Int get() = firstHome + homeCount + if (library) 1 else 0
    fun main(ordinal: Int): Int = firstHome + ordinal.coerceIn(0, homeCount - 1)
    fun page(position: Int): HomePage = when {
        today && position == 0 -> HomePage.Today
        library && position == libraryPosition -> HomePage.Library
        else -> HomePage.Desktop((position - firstHome).coerceIn(0, homeCount - 1))
    }
    fun position(page: HomePage, main: Int): Int = when (page) {
        HomePage.Today -> if (today) 0 else main(main)
        HomePage.Library -> if (library) libraryPosition else main(main)
        is HomePage.Desktop -> this.main(page.ordinal)
    }
    fun key(position: Int): String = when (val page = page(position)) {
        HomePage.Today -> "today"
        HomePage.Library -> "library"
        is HomePage.Desktop -> "home_${page.ordinal}"
    }

    /** Drag page indices exclude Today; only individual apps can enter the library. */
    fun dragNeighbor(current: Int, direction: Int, allowLibrary: Boolean): Int? {
        val last = if (library && allowLibrary) homeCount else homeCount - 1
        return (current + direction).takeIf { direction != 0 && it in 0..last }
    }
}
