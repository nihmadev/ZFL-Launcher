package com.zfl.launcher.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual SharedPreferences migration, including its once-only marker. */
class HomeBehaviorPrefsTest {
    @Test fun migrationIsAtomicAndDoesNotReplaceNewChoices() {
        val data = mutableMapOf<String, Any>("search_swipe" to "Up", "home_action_SwipeDown" to "settings")
        val prefs = preferences(data)
        prefs.migrateHomeBehavior()
        assertEquals(HomeAction.Search, prefs.readHomeBehavior().swipeUp)
        assertEquals(HomeAction.Settings, prefs.readHomeBehavior().swipeDown)
        assertFalse(data.containsKey("search_swipe"))
        assertFalse(data.containsKey("swipe_down_search"))
        data["home_action_SwipeUp"] = "none"
        prefs.migrateHomeBehavior()
        assertEquals(HomeAction.None, prefs.readHomeBehavior().swipeUp)
        val expected = HomeBehavior(repeatHome = HomeAction.OpenApp(AppTarget("app/.Home", "10")), showToday = false, haptics = false, mainPage = 4)
        val editor = prefs.edit()
        editor.putHomeBehavior(expected)
        editor.apply()
        assertEquals(expected, prefs.readHomeBehavior())
    }

    @Test fun migrationUsesLegacyToggleAndPreservesUnknownNewKeysSafely() {
        for (legacy in listOf(false, true)) {
            val data = mutableMapOf<String, Any>("swipe_down_search" to legacy, "home_action_SwipeUp" to "future")
            val prefs = preferences(data)
            prefs.migrateHomeBehavior()
            assertEquals(if (legacy) HomeAction.Search else HomeAction.None, prefs.readHomeBehavior().swipeDown)
            assertEquals(HomeAction.None, prefs.readHomeBehavior().swipeUp)
        }
        val prefs = preferences(mutableMapOf())
        prefs.migrateHomeBehavior()
        assertEquals(HomeBehavior(), prefs.readHomeBehavior())
    }

    private fun preferences(data: MutableMap<String, Any>): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        val pending = mutableMapOf<String, Any?>()
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "apply", "commit" -> { pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }; pending.clear(); true }
                "remove" -> { pending[args!![0] as String] = null; editor }
                else -> { pending[args!![0] as String] = args[1]; editor }
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "contains" -> data.containsKey(args!![0])
                "getAll" -> data.toMap()
                else -> data[args!![0]] ?: args[1]
            }
        } as SharedPreferences
    }
}
