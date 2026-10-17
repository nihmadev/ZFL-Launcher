package com.zfl.launcher.settings

/** Legacy search direction, retained only for reading pre-assignment preferences. */
enum class SearchSwipe {
    Disabled, Down, Up;

    /** Uses signed vertical travel: positive is down, negative is up. */
    fun isTriggered(travel: Float, threshold: Float): Boolean = when (this) {
        Disabled -> false
        Down -> travel > threshold
        Up -> travel < -threshold
    }

    companion object {
        /** Preserves the previous swipe-down toggle until a direction has been saved. */
        fun fromStored(name: String?, legacyEnabled: Boolean): SearchSwipe =
            entries.firstOrNull { it.name == name } ?: if (legacyEnabled) Down else Disabled
    }
}
