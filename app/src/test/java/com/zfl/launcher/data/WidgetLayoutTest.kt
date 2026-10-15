package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private fun apps(vararg keys: String): List<HomeItem> = keys.map(::AppItem)
    private val clock = WidgetItem("w1", WidgetKind.Clock, WidgetSize.Small)

    @Test
    fun widgetAddedAtTopOfPagePushesAppsDown() {
        val layout = LayoutData(pages = listOf(apps(*Array(20) { "a$it" })), dock = emptyList(), today = emptyList())
        val next = layout.addWidget(clock, WidgetTarget.Page(0), pageCapacity = 20)
        assertEquals(clock, next.pages[0][0])
        // A small widget takes 4 cells of the 4×5 page, so 4 apps move to a new page.
        assertEquals(listOf(17, 4), next.pages.map { it.size })
        assertEquals(layout.allAppKeys(), next.allAppKeys())
    }

    @Test
    fun widgetAddedToTodayGoesLast() {
        val layout = LayoutData(pages = listOf(apps("a")), dock = emptyList(), today = listOf(clock))
        val battery = WidgetItem("w2", WidgetKind.Battery, WidgetSize.Small)
        assertEquals(listOf(clock, battery), layout.addWidget(battery, WidgetTarget.Today, 24).today)
    }

    @Test
    fun resizeAndRemoveWorkOnPagesAndToday() {
        val layout = LayoutData(pages = listOf(listOf(clock) + apps("a")), dock = emptyList(), today = listOf(clock.copy(id = "t")))
        val resized = layout.updateWidget(clock.copy(size = WidgetSize.Medium), 24)
        assertEquals(WidgetSize.Medium, (resized.findWidget("w1") as? WidgetItem)?.size)
        val removed = resized.removeWidget("w1", 24).removeWidget("t", 24)
        assertTrue(removed.allWidgets().isEmpty())
        assertEquals(setOf("a"), removed.allAppKeys())
    }

    @Test
    fun widgetsCannotGoToDockOrFolders() {
        val layout = LayoutData(pages = listOf(listOf(clock) + apps("a")), dock = emptyList(), today = emptyList())
        assertEquals(layout, layout.move(clock, DropPosition.Dock(0), 24) { "" })
        assertEquals(layout, layout.move(AppItem("a"), DropPosition.Merge("w1"), 24) { "" })
    }

    @Test
    fun jsonRoundTripKeepsWidgetsAndToday() {
        val layout = LayoutData(
            pages = listOf(listOf(clock.copy(photo = 42L), AppItem("a"))),
            dock = apps("d"),
            today = listOf(WidgetItem("t", WidgetKind.Weather, WidgetSize.Large)),
        )
        assertEquals(layout, LayoutData.fromJson(layout.toJson()))
    }

    @Test
    fun layoutSavedBeforeWidgetsGetsDefaultToday() {
        val old = """{"version":1,"pages":[[{"app":"a"}]],"dock":[],"removed":[]}"""
        assertEquals(DEFAULT_TODAY_WIDGETS, LayoutData.fromJson(old)?.today)
    }

    @Test
    fun reconcileKeepsWidgets() {
        val layout = LayoutData(pages = listOf(listOf(clock) + apps("a")), dock = emptyList(), today = emptyList())
        assertEquals(clock, layout.reconcile(listOf("a", "b"), 24).pages[0][0])
    }

    private val hosted = AppWidgetItem("appwidget-7", 7, "com.example/.Provider", Span(4, 1))

    @Test
    fun hostedWidgetRoundTripsAndFlowsWithApps() {
        val layout = LayoutData(pages = listOf(listOf(hosted) + apps("a", "b")), dock = emptyList(), today = listOf(hosted.copy(id = "t", appWidgetId = 8)))
        assertEquals(layout, LayoutData.fromJson(layout.toJson()))
        // A 4×1 widget takes the first row; apps start on the second.
        assertEquals(listOf(0, 4, 5), packCells(layout.pages[0].map { it.span }, 4, 6))
    }

    @Test
    fun hostedWidgetsOfUninstalledAppsAreRemoved() {
        val layout = LayoutData(pages = listOf(listOf(hosted) + apps("a")), dock = emptyList(), today = listOf(hosted.copy(id = "t", appWidgetId = 8)))
        val next = layout.withoutDeadAppWidgets(isAlive = { it == 8 }, pageCapacity = 24)
        assertEquals(listOf(apps("a")), next.pages)
        assertEquals(1, next.today.size)
        assertEquals(layout, layout.withoutDeadAppWidgets(isAlive = { true }, pageCapacity = 24))
    }

    @Test
    fun appWidgetSizesRespectMinAndMax() {
        val spans = appWidgetSpans(default = Span(4, 2), resizable = true, min = Span(2, 2), max = Span(4, 2))
        assertEquals(listOf(Span(2, 2), Span(4, 2)), spans)
        assertEquals(listOf(Span(2, 1)), appWidgetSpans(Span(2, 1), resizable = false, min = Span(1, 1), max = null))
    }
}
