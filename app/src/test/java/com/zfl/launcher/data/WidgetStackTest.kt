package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStackTest {
    private val clock = WidgetItem("clock", WidgetKind.Clock, WidgetSize.Small)
    private val battery = WidgetItem("battery", WidgetKind.Battery, WidgetSize.Small)
    private val weather = WidgetItem("weather", WidgetKind.Weather, WidgetSize.Small)
    private val mediumWeather = WidgetItem("mweather", WidgetKind.Weather, WidgetSize.Medium)
    private val hosted = AppWidgetItem("appwidget-7", 7, "com.example/.Provider", Span(2, 2))
    private val stack = WidgetStack("s", WidgetSize.Small, listOf(clock, hosted, battery), current = 1)

    private fun page(vararg items: HomeItem) = LayoutData(listOf(items.toList()), emptyList(), today = emptyList())

    @Test
    fun droppingWidgetOnSameSizeWidgetMakesStackInTargetCell() {
        val layout = page(clock, AppItem("a"), battery).withPageCells(24)
        val batteryCell = layout.cells.getValue(battery.id)
        val next = layout.move(clock, DropPosition.Merge(battery.id), 24) { "" }
        val made = next.pages.flatten().filterIsInstance<WidgetStack>().single()
        assertEquals(listOf(clock, battery), made.items)
        assertEquals(0, made.current) // the dropped widget is shown
        assertEquals(batteryCell, next.cells[made.id])
        assertEquals(setOf("a"), next.allAppKeys())
        assertEquals(1, next.pages.flatten().count { it is PageWidget })
    }

    @Test
    fun droppingOnStackAddsAndShowsTheWidget() {
        val next = page(stack, weather).move(weather, DropPosition.Merge("s"), 24) { "" }
        val grown = next.findStack("s")!!
        assertEquals(listOf(clock, weather, hosted, battery), grown.items)
        assertEquals(weather, grown.currentItem)
    }

    @Test
    fun otherSizeAndFullStackAreRejected() {
        val layout = page(clock, mediumWeather)
        assertEquals(layout.normalized(24), layout.move(mediumWeather, DropPosition.Merge(clock.id), 24) { "" }.normalized(24))
        assertNull(stackOf(clock, mediumWeather, "x"))
        assertNull(stack.plus(mediumWeather))
        val full = WidgetStack("f", WidgetSize.Small, List(STACK_MAX_ITEMS) { WidgetItem("w$it", WidgetKind.Clock, WidgetSize.Small) })
        assertNull(full.plus(weather))
        assertTrue(!canStackOnto(WidgetEntry("x", WidgetKind.Battery, WidgetSize.Small, null), WidgetStackEntry("f", WidgetSize.Small, List(STACK_MAX_ITEMS) { WidgetEntry("w$it", WidgetKind.Clock, WidgetSize.Small, null) }, 0)))
        // Stacks never nest, and a 4×1 hosted widget fits no stack.
        assertNull(stackOf(clock, stack, "x"))
        assertNull(AppWidgetItem("g", 9, "g/.P", Span(4, 1)).stackSize)
    }

    @Test
    fun removeKeepsShownWidgetAndSingleLeftoverBecomesWidgetInStackCell() {
        assertEquals(hosted, stack.minus(clock.id).currentItem)
        val layout = page(AppItem("a"), WidgetStack("s", WidgetSize.Small, listOf(clock, battery))).withPageCells(24)
        val cell = layout.cells.getValue("s")
        val next = layout.removeWidget(clock.id, 24)
        assertEquals(battery, next.pages[0].last())
        assertEquals(cell, next.cells[battery.id])
        assertNull(next.findStack("s"))
    }

    @Test
    fun reorderKeepsShownWidgetAndCurrentIsClamped() {
        val moved = stack.moved(from = 1, to = 2)
        assertEquals(listOf(clock, battery, hosted), moved.items)
        assertEquals(hosted, moved.currentItem)
        assertEquals(2, stack.showing(99).current)
        assertEquals(2, (stack.copy(current = 7).normalizedStack() as WidgetStack).current)
    }

    @Test
    fun normalizationDropsWrongSizesAndNestingAndDissolves() {
        val mixed = WidgetStack("m", WidgetSize.Small, listOf(clock, mediumWeather, stack, battery))
        assertEquals(listOf(clock, battery), (mixed.normalizedStack() as WidgetStack).items)
        assertEquals(clock, WidgetStack("one", WidgetSize.Small, listOf(clock)).normalizedStack())
        assertNull(WidgetStack("none", WidgetSize.Small, emptyList()).normalizedStack())
    }

    @Test
    fun takingOutPutsWidgetNextToStack() {
        val layout = page(stack, AppItem("a"))
        val next = layout.takeOutOfStack("s", clock.id, 24)
        assertEquals(listOf("s", clock.id, "a"), next.pages[0].map { it.id })
        assertEquals(listOf(hosted, battery), next.findStack("s")!!.items)
        // Taking one out of a pair dissolves the stack into the other widget.
        val pair = page(WidgetStack("p", WidgetSize.Small, listOf(clock, battery))).withPageCells(24)
        val split = pair.takeOutOfStack("p", clock.id, 24)
        assertEquals(listOf(battery.id, clock.id), split.pages[0].map { it.id })
        assertEquals(pair.cells["p"], split.cells[battery.id])
    }

    @Test
    fun jsonRoundTripKeepsStackAndSkipsUnknownNestedEntries() {
        val layout = LayoutData(listOf(listOf(stack, AppItem("a"))), emptyList(), today = listOf(stack.copy(id = "t", items = listOf(weather, clock))))
        assertEquals(layout, LayoutData.fromJson(layout.toJson()))

        val json = """{"version":2,"pages":[[{"stack":"s","widget":"s","kind":"stack","size":"Small","current":2,"items":[
            {"widget":"clock","kind":"Clock","size":"Small"},{"hologram":"h1"},{"widget":"x","kind":"FutureKind","size":"Small"},
            {"widget":"battery","kind":"Battery","size":"Small"}]},{"app":"a"},{"teleport":"t"}]],"dock":[{"app":"d"}]}"""
        val parsed = LayoutData.fromJson(json)!!
        assertEquals(WidgetStack("s", WidgetSize.Small, listOf(clock, battery), current = 1), parsed.pages[0][0])
        assertEquals(setOf("a", "d"), parsed.allAppKeys())
    }

    @Test
    fun stackEntryIsSkippedByBuildsWithoutStacks() {
        // Older builds skip "widget" entries of an unknown kind; the stack's JSON must look like one.
        val obj = org.json.JSONObject(LayoutData(listOf(listOf(stack)), emptyList()).toJson()).getJSONArray("pages").getJSONArray(0).getJSONObject(0)
        assertTrue(obj.has("widget") && WidgetKind.entries.none { it.name == obj.getString("kind") })
    }

    @Test
    fun deadHostedWidgetLeavesOnlyItsStack() {
        val layout = page(stack, AppItem("a"))
        val next = layout.withoutDeadAppWidgets(isAlive = { false }, pageCapacity = 24)
        assertEquals(listOf(clock, battery), next.findStack("s")!!.items)
        assertEquals(setOf("a"), next.allAppKeys())
    }

    @Test
    fun usedAppWidgetIdsIncludeStackedWidgets() {
        // Regression: ids of stacked widgets must not look like orphans, or startup deletes them.
        val layout = LayoutData(listOf(listOf(stack)), emptyList(), today = listOf(AppWidgetItem("t", 8, "p/.P", Span(4, 2))))
        assertEquals(setOf(7, 8), layout.usedAppWidgetIds())
        assertEquals(hosted, layout.findWidget(hosted.id))
        assertTrue(layout.allWidgets().containsAll(listOf(clock, battery)))
    }

    @Test
    fun updateWidgetReplacesWidgetInsideStack() {
        val layout = page(stack)
        val next = layout.updateWidget(clock.copy(photo = 5L), 24)
        assertEquals(5L, (next.findWidget(clock.id) as WidgetItem).photo)
        assertEquals(1, next.findStack("s")!!.current)
        // A size the stack doesn't have drops the widget from it.
        assertEquals(listOf(hosted, battery), layout.updateWidget(clock.copy(size = WidgetSize.Medium), 24).findStack("s")!!.items)
    }

    @Test
    fun resizingStackNeedsEverySizeSupported() {
        val builtIn = WidgetStack("b", WidgetSize.Small, listOf(clock, weather))
        val medium = builtIn.resizedTo(WidgetSize.Medium) { w, s -> s in (w as WidgetItem).kind.sizes }!!
        assertEquals(listOf(WidgetSize.Medium, WidgetSize.Medium), medium.items.map { (it as WidgetItem).size })
        assertNull(builtIn.resizedTo(WidgetSize.Large) { w, s -> s in (w as WidgetItem).kind.sizes })
    }

    @Test
    fun stackTakesOneSlotOnTheGrid() {
        val layout = page(stack, *Array(20) { AppItem("a$it") }).withPageCells(24)
        assertEquals(0, layout.cells["s"])
        // A 2×2 stack covers cells 0, 1, 4, 5: the first app goes to cell 2.
        assertEquals(2, layout.cells["a0"])
        assertEquals(listOf(21), layout.normalized(24).pages.map { it.size })
    }

    @Test
    fun smartStackHasOnlyFittingBuiltInWidgets() {
        val small = smartStack("x", WidgetSize.Small)
        assertTrue(small.items.all { (it as WidgetItem).size == WidgetSize.Small && it.kind != WidgetKind.Photo })
        assertEquals(listOf(WidgetSize.Small, WidgetSize.Medium), SMART_STACK_SIZES)
    }
}
