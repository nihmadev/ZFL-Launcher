package com.zfl.launcher.settings

import android.content.SharedPreferences
import androidx.core.content.edit

private fun HomeBinding.key(): String = "home_action_${name}"

/** Removes old direction keys atomically after filling only missing new bindings. */
internal fun SharedPreferences.migrateHomeBehavior() {
    if (getBoolean("home_behavior_migrated", false)) return
    val old = SearchSwipe.fromStored(getString("search_swipe", null), getBoolean("swipe_down_search", true))
    edit {
        HomeBinding.entries.forEach { binding ->
            if (!contains(binding.key())) {
                val action = when (binding) {
                    HomeBinding.SwipeUp -> migratedSwipe(null, false, old, true)
                    HomeBinding.SwipeDown -> migratedSwipe(null, false, old, false)
                    else -> HomeBehavior()[binding]
                }
                putString(binding.key(), HomeActionCodec.encode(action))
            }
        }
        remove("search_swipe")
        remove("swipe_down_search")
        putBoolean("home_behavior_migrated", true)
    }
}

internal fun SharedPreferences.readHomeBehavior(): HomeBehavior {
    var result = HomeBehavior()
    HomeBinding.entries.forEach { binding ->
        if (contains(binding.key())) result = result.assign(binding, HomeActionCodec.decode(getString(binding.key(), null)))
    }
    return result.copy(
        mainPage = getInt("home_main_page", 0),
        returnPolicy = HomeReturn.entries.firstOrNull { it.name == getString("home_return", null) } ?: HomeReturn.Stay,
        showToday = getBoolean("home_today", true),
        haptics = getBoolean("home_haptics", true),
    ).sanitized()
}

internal fun SharedPreferences.Editor.putHomeBehavior(value: HomeBehavior) {
    HomeBinding.entries.forEach { putString(it.key(), HomeActionCodec.encode(value[it])) }
    putInt("home_main_page", value.mainPage)
    putString("home_return", value.returnPolicy.name)
    putBoolean("home_today", value.showToday)
    putBoolean("home_haptics", value.haptics)
}
