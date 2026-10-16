package com.zfl.launcher.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {
    @Test
    fun freshInstallShowsOnboarding() {
        assertTrue(isOnboardingNeeded(storedDone = null, hasSavedSettings = false, hasSavedLayout = false))
    }

    @Test
    fun updateWithSavedSettingsSkipsIt() {
        assertFalse(isOnboardingNeeded(storedDone = null, hasSavedSettings = true, hasSavedLayout = false))
    }

    @Test
    fun updateWithSavedLayoutSkipsIt() {
        assertFalse(isOnboardingNeeded(storedDone = null, hasSavedSettings = false, hasSavedLayout = true))
    }

    @Test
    fun storedFlagWins() {
        assertFalse(isOnboardingNeeded(storedDone = true, hasSavedSettings = false, hasSavedLayout = false))
        // Started on a fresh install: a layout saved by the home screen later doesn't end it.
        assertTrue(isOnboardingNeeded(storedDone = false, hasSavedSettings = true, hasSavedLayout = true))
    }
}
