package com.zfl.launcher.settings

import android.content.SharedPreferences

/** Only intent and look are persisted. Grants, sessions and window state are always live. */
data class IslandPreferences(
    val enabled: Boolean = false,
    val charging: Boolean = false,
    /** Running timers, stopwatches and ringing alarms. */
    val timers: Boolean = true,
    /** An empty capsule stays around the camera when nothing is live, like iOS. */
    val alwaysOn: Boolean = false,
    /** Real blur behind the expanded card (where Android allows cross-window blur). */
    val blur: Boolean = true,
    /** The card takes the album art's color. */
    val artworkTint: Boolean = true,
    val compactWidth: Float = DEFAULT_COMPACT_WIDTH,
    val sideMargin: Float = DEFAULT_SIDE_MARGIN,
    val topOffset: Float = 0f,
    val collapseSeconds: Float = DEFAULT_COLLAPSE_SECONDS,
) {
    fun storedValues(): Map<String, Any> = mapOf(
        ENABLED to enabled, CHARGING to charging, TIMERS to timers, ALWAYS_ON to alwaysOn, BLUR to blur,
        ARTWORK_TINT to artworkTint, COMPACT_WIDTH to compactWidth, SIDE_MARGIN to sideMargin,
        TOP_OFFSET to topOffset, COLLAPSE_SECONDS to collapseSeconds,
    )

    companion object {
        const val DEFAULT_COMPACT_WIDTH: Float = 164f
        const val DEFAULT_SIDE_MARGIN: Float = 10f
        const val DEFAULT_COLLAPSE_SECONDS: Float = 12f
        val COMPACT_WIDTH_RANGE: ClosedFloatingPointRange<Float> = 120f..220f
        val SIDE_MARGIN_RANGE: ClosedFloatingPointRange<Float> = 0f..32f
        val TOP_OFFSET_RANGE: ClosedFloatingPointRange<Float> = -12f..24f
        val COLLAPSE_RANGE: ClosedFloatingPointRange<Float> = 4f..30f

        private const val ENABLED = "island_enabled"
        private const val CHARGING = "island_charging"
        private const val TIMERS = "island_timers"
        private const val ALWAYS_ON = "island_always_on"
        private const val BLUR = "island_blur"
        private const val ARTWORK_TINT = "island_artwork_tint"
        private const val COMPACT_WIDTH = "island_compact_width"
        private const val SIDE_MARGIN = "island_side_margin"
        private const val TOP_OFFSET = "island_top_offset"
        private const val COLLAPSE_SECONDS = "island_collapse_seconds"

        /** Missing, legacy or wrongly typed values cannot opt an install into an overlay. */
        fun fromStored(values: Map<String, *>): IslandPreferences {
            val defaults = IslandPreferences()
            fun flag(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
            fun number(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
                (values[key] as? Float)?.takeIf { it.isFinite() }?.coerceIn(range) ?: default
            return IslandPreferences(
                enabled = flag(ENABLED, false),
                charging = flag(CHARGING, false),
                timers = flag(TIMERS, defaults.timers),
                alwaysOn = flag(ALWAYS_ON, defaults.alwaysOn),
                blur = flag(BLUR, defaults.blur),
                artworkTint = flag(ARTWORK_TINT, defaults.artworkTint),
                compactWidth = number(COMPACT_WIDTH, defaults.compactWidth, COMPACT_WIDTH_RANGE),
                sideMargin = number(SIDE_MARGIN, defaults.sideMargin, SIDE_MARGIN_RANGE),
                topOffset = number(TOP_OFFSET, defaults.topOffset, TOP_OFFSET_RANGE),
                collapseSeconds = number(COLLAPSE_SECONDS, defaults.collapseSeconds, COLLAPSE_RANGE),
            )
        }
    }
}

internal fun SharedPreferences.Editor.putIsland(value: IslandPreferences) {
    value.storedValues().forEach { (key, stored) ->
        when (stored) {
            is Boolean -> putBoolean(key, stored)
            is Float -> putFloat(key, stored)
        }
    }
}
