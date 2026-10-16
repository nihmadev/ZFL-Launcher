package com.zfl.launcher.ui.onboarding

import com.zfl.launcher.ui.onboarding.OnboardingMode.FinishSetup
import com.zfl.launcher.ui.onboarding.OnboardingMode.Full
import com.zfl.launcher.ui.onboarding.OnboardingStep.Appearance
import com.zfl.launcher.ui.onboarding.OnboardingStep.DefaultHome
import com.zfl.launcher.ui.onboarding.OnboardingStep.Hello
import com.zfl.launcher.ui.onboarding.OnboardingStep.Import
import com.zfl.launcher.ui.onboarding.OnboardingStep.Language
import com.zfl.launcher.ui.onboarding.OnboardingStep.WallpaperAccess
import com.zfl.launcher.ui.onboarding.OnboardingStep.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {
    private val fresh = SetupSystemState(isDefaultHome = false, homeRoleAvailable = true, hasWallpaperAccess = false, hasImportSources = true)

    @Test
    fun freshInstallShowsEveryStep() {
        assertEquals(OnboardingStep.entries, onboardingSteps(fresh, Full))
    }

    @Test
    fun doneOrImpossibleStepsAreLeftOut() {
        assertFalse(DefaultHome in onboardingSteps(fresh.copy(isDefaultHome = true), Full))
        assertFalse(DefaultHome in onboardingSteps(fresh.copy(homeRoleAvailable = false), Full))
        assertFalse(WallpaperAccess in onboardingSteps(fresh.copy(hasWallpaperAccess = true), Full))
        assertFalse(Import in onboardingSteps(fresh.copy(hasImportSources = false), Full))
        assertFalse(Import in onboardingSteps(fresh.copy(hasImportSources = null), Full))
    }

    @Test
    fun forwardAndBackSkipMissingSteps() {
        val steps = onboardingSteps(fresh.copy(isDefaultHome = true, hasImportSources = false), Full)
        assertEquals(WallpaperAccess, nextStep(Language, steps))
        assertEquals(Appearance, nextStep(WallpaperAccess, steps))
        assertEquals(WallpaperAccess, previousStep(Appearance, steps))
        assertEquals(Language, previousStep(WallpaperAccess, steps))
        assertEquals(Welcome, previousStep(Language, steps))
        assertNull(nextStep(Appearance, steps))
    }

    @Test
    fun backLeavesTheFlowOnWelcomeAndHello() {
        val steps = onboardingSteps(fresh, Full)
        assertNull(previousStep(Welcome, steps))
        assertNull(previousStep(Hello, steps))
    }

    @Test
    fun returningFromSystemScreenMovesOnOnlyWhenDone() {
        val granted = onboardingSteps(fresh.copy(isDefaultHome = true), Full)
        assertEquals(WallpaperAccess, resolveStep(DefaultHome, granted))
        val refused = onboardingSteps(fresh, Full)
        assertEquals(DefaultHome, resolveStep(DefaultHome, refused))
        val accessGiven = onboardingSteps(fresh.copy(hasWallpaperAccess = true), Full)
        assertEquals(Import, resolveStep(WallpaperAccess, accessGiven))
    }

    @Test
    fun finishSetupListsSkippedEssentials() {
        assertEquals(listOf(DefaultHome, WallpaperAccess), finishSetupItems(fresh))
        assertEquals(listOf(WallpaperAccess), finishSetupItems(fresh.copy(isDefaultHome = true)))
        assertEquals(listOf(WallpaperAccess), finishSetupItems(fresh.copy(homeRoleAvailable = false)))
        assertTrue(finishSetupItems(fresh.copy(isDefaultHome = true, hasWallpaperAccess = true)).isEmpty())
        assertEquals(finishSetupItems(fresh), onboardingSteps(fresh, FinishSetup))
    }

    @Test
    fun finishSetupEndsWhenLastItemIsDone() {
        val steps = onboardingSteps(fresh.copy(isDefaultHome = true, hasWallpaperAccess = true), FinishSetup)
        assertNull(resolveStep(WallpaperAccess, steps))
        assertNull(previousStep(DefaultHome, onboardingSteps(fresh, FinishSetup)))
    }

    @Test
    fun importLookupBlocksOnlyStepsBeforeImport() {
        assertTrue(needsImportLookup(Language, Full, null))
        assertFalse(needsImportLookup(Language, Full, false))
        assertFalse(needsImportLookup(Appearance, Full, null))
        assertFalse(needsImportLookup(DefaultHome, FinishSetup, null))
    }

    @Test
    fun progressCountsShownStepsAndStaysStable() {
        val all = onboardingSteps(fresh, Full)
        assertNull(stepProgress(Welcome, all, emptySet()))
        assertEquals(StepProgress(1, 5), stepProgress(Language, all, setOf(Language)))
        // Home role granted: the step left the list but was shown, so the total stays.
        val after = onboardingSteps(fresh.copy(isDefaultHome = true), Full)
        assertEquals(StepProgress(3, 5), stepProgress(WallpaperAccess, after, setOf(Language, DefaultHome, WallpaperAccess)))
        // Never shown (already default home): not counted.
        assertEquals(StepProgress(2, 4), stepProgress(WallpaperAccess, after, setOf(Language, WallpaperAccess)))
    }
}
