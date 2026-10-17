package com.zfl.launcher.ui.home

import com.zfl.launcher.data.*
import com.zfl.launcher.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

class TodayVisibilityTest {
    @Test fun hiddenTodayRetainsPhotosStacksAndHostedIdsAcrossSerialization() {
        val photo = WidgetItem("photo", WidgetKind.Photo, WidgetSize.Small, photo = 123L)
        val hosted = AppWidgetItem("hosted", 77, "app/.Widget", WidgetSize.Small.span)
        val stack = WidgetStack("stack", WidgetSize.Small, listOf(photo, hosted), current = 1)
        val layout = LayoutData(listOf(emptyList()), emptyList(), today = listOf(stack))
        val defaults = SettingsState()
        val hidden = defaults.copy(homeBehavior = defaults.homeBehavior.copy(showToday = false))
        val before = buildHomeLayout(layout, emptyList(), defaults)
        val after = buildHomeLayout(layout, emptyList(), hidden)
        assertEquals(before.today, after.today)
        assertEquals(setOf(77), layout.usedAppWidgetIds())
        assertFalse((0 until HomePages(1, false, true).count).any { HomePages(1, false, true).page(it) == HomePage.Today })
        val restored = layoutFromJson(layout.toJson(), GridSpec.Default)!!
        assertEquals(layout.today, restored.today)
        assertEquals(setOf(77), restored.usedAppWidgetIds())
        assertEquals(123L, restored.allWidgets().single().photo)
        assertEquals(before.today, buildHomeLayout(restored, emptyList(), defaults).today)
    }
}
