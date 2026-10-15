package com.zfl.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.exp

/**
 * Counts app launches from the launcher, on this device only, to power "Suggestions"
 * in search and the App Library. Nothing ever leaves the phone.
 */
class LaunchStats private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("launch_stats", Context.MODE_PRIVATE)

    private val _scores = MutableStateFlow(readScores())
    /** App key → relevance; frequent and recent launches score higher. */
    val scores: StateFlow<Map<String, Double>> = _scores

    fun recordLaunch(appKey: String) {
        val count = prefs.getInt("count:$appKey", 0) + 1
        prefs.edit {
            putInt("count:$appKey", count)
            putLong("last:$appKey", System.currentTimeMillis())
        }
        _scores.value = readScores()
    }

    /** The [limit] most relevant apps among [apps]; falls back to alphabetical order. */
    fun suggestions(apps: List<AppInfo>, limit: Int): List<AppInfo> {
        val scores = _scores.value
        return apps.sortedByDescending { scores[it.key] ?: 0.0 }.take(limit)
    }

    private fun readScores(): Map<String, Double> {
        val now = System.currentTimeMillis()
        return prefs.all.keys
            .filter { it.startsWith("count:") }
            .associate { countKey ->
                val app = countKey.removePrefix("count:")
                val count = prefs.getInt(countKey, 0)
                val ageDays = (now - prefs.getLong("last:$app", 0L)) / DAY_MS.toDouble()
                // Launch count, fading with a ~2 week half-life since the last launch.
                app to count * exp(-ageDays / 20.0)
            }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: LaunchStats? = null

        fun get(context: Context): LaunchStats =
            instance ?: synchronized(this) { instance ?: LaunchStats(context).also { instance = it } }
    }
}
