package com.zfl.launcher.ui.home

import com.zfl.launcher.HomeIntentRouter
import com.zfl.launcher.settings.AppTarget
import com.zfl.launcher.settings.HomeAction
import com.zfl.launcher.settings.HomeBinding
import com.zfl.launcher.settings.HomeReturn
import org.junit.Assert.*
import org.junit.Test

class HomeBehaviorRoutingTest {
    private class Commands : HomeCommands {
        val calls = mutableListOf<String>()
        var overlay = false
        override fun search() { calls += "search" }
        override fun library() { calls += "library" }
        override fun settings() { calls += "settings" }
        override fun mainPage() { calls += "main" }
        override fun launch(target: AppTarget) { calls += target.key }
        override fun closeTop(): Boolean = overlay.also { if (it) { calls += "close"; overlay = false } }
    }

    @Test fun actualDispatcherClosesBeforeRepeatAndNoneDoesNotNavigate() {
        val commands = Commands()
        val dispatcher = HomeCommandDispatcher(commands)
        commands.overlay = true
        dispatcher.home(HomeAction.Search, true, false)
        assertEquals(listOf("close"), commands.calls)
        dispatcher.home(HomeAction.None, true, false)
        assertEquals(listOf("close"), commands.calls)
        dispatcher.home(HomeAction.MainPage, true, false)
        dispatcher.home(HomeAction.Search, true, false)
        assertEquals(listOf("close", "main", "search"), commands.calls)
        dispatcher.home(HomeAction.Search, true, true)
        assertEquals(3, commands.calls.size)
    }

    @Test fun commandsExecuteOnceAndRespectAvailabilityAndInputBlocking() {
        val commands = Commands()
        val dispatcher = HomeCommandDispatcher(commands)
        for (action in listOf(HomeAction.Search, HomeAction.Settings, HomeAction.AppLibrary, HomeAction.OpenApp(AppTarget("a/.Main", "10")))) {
            dispatcher.execute(action, true, blocked = true)
        }
        dispatcher.execute(HomeAction.AppLibrary, false)
        assertTrue(commands.calls.isEmpty())
        dispatcher.execute(HomeAction.Search, true)
        dispatcher.execute(HomeAction.Settings, true)
        dispatcher.execute(HomeAction.AppLibrary, true)
        dispatcher.execute(HomeAction.OpenApp(AppTarget("a/.Main", "10")), true)
        assertEquals(listOf("search", "settings", "library", "a/.Main#10"), commands.calls)
        dispatcher.returned(HomeReturn.Stay)
        assertEquals(4, commands.calls.size)
        dispatcher.returned(HomeReturn.MainPage)
        assertEquals("main", commands.calls.last())
    }

    @Test fun resumeSettingsAndPermissionsAreNotRepeatedHome() {
        val router = HomeIntentRouter()
        assertFalse(router.resume())
        assertFalse(router.intent(false, true, false))
        assertFalse(router.resume())
        assertTrue(router.intent(true, true, false))
        assertFalse(router.resume())
        router.appLaunched()
        assertFalse(router.intent(true, false, true))
        assertTrue(router.resume())
        assertFalse(router.resume())
        assertFalse(router.intent(true, false, false))
        assertTrue(router.resume())
        router.appLaunched()
        assertFalse(router.resume()) // A resume before the launch has actually covered Home.
        router.stop()
        assertTrue(router.resume()) // Back from a launched app also follows the return policy.
        assertFalse(router.resume()) // No OpenApp -> return -> RepeatHome loop.
    }

    @Test fun axisThresholdOwnershipCancellationAndSingleDispatch() {
        for (sign in listOf(-1, 1)) {
            val sequence = HomeSwipe(10f, 56f)
            sequence.move(0f, 5f * sign, false, 1, true)
            assertFalse(sequence.claimed)
            sequence.move(1f, 56f * sign, false, 1, true)
            assertFalse(sequence.claimed)
            sequence.move(2f, 57f * sign, false, 1, true)
            assertTrue(sequence.claimed)
            sequence.move(3f, 120f * sign, false, 1, true)
            assertEquals(if (sign < 0) HomeBinding.SwipeUp else HomeBinding.SwipeDown, sequence.release())
            assertNull(sequence.release())
        }
        for (reason in 0..3) {
            val sequence = HomeSwipe(10f, 56f)
            sequence.move(0f, 60f, false, 1, true)
            when (reason) {
                0 -> sequence.move(0f, 90f, true, 1, true) // Initial stack / child consumed.
                1 -> sequence.move(0f, 90f, false, 2, true)
                2 -> sequence.move(0f, 90f, false, 1, false) // Focus, drag or edit.
                3 -> sequence.cancel() // Cancellation / disposal.
            }
            assertFalse(sequence.claimed)
            assertNull(sequence.release())
        }
        val horizontal = HomeSwipe(10f, 56f)
        horizontal.move(20f, 12f, false, 1, true)
        horizontal.move(21f, 100f, false, 1, true)
        assertNull(horizontal.release())
        val vertical = HomeSwipe(10f, 56f)
        vertical.move(12f, 20f, false, 1, true)
        vertical.move(70f, 60f, false, 1, true)
        assertEquals(HomeBinding.SwipeDown, vertical.release())
    }

    @Test fun pageMapperHandlesEveryOptionalPageCombinationAndRemoval() {
        for (today in listOf(false, true)) for (library in listOf(false, true)) for (count in 0..4) {
            val pages = HomePages(count, today, library)
            assertEquals(count.coerceAtLeast(1) + (if (today) 1 else 0) + (if (library) 1 else 0), pages.count)
            assertEquals(HomePage.Desktop(0), pages.page(pages.main(-100)))
            assertEquals(HomePage.Desktop(count.coerceAtLeast(1) - 1), pages.page(pages.main(100)))
            for (position in 0 until pages.count) assertEquals(position, pages.position(pages.page(position), 0))
            assertEquals(pages.count, (0 until pages.count).map(pages::key).toSet().size)
        }
        val smaller = HomePages(2, false, false)
        assertEquals(1, smaller.position(HomePage.Desktop(8), 0))
        assertEquals(1, smaller.position(HomePage.Today, 1))
        assertEquals(1, smaller.position(HomePage.Library, 1))
        assertEquals(0, HomePages(0, false, false).main(9))
    }
}
