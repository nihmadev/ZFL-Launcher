package com.zfl.launcher.ui.onboarding

/** Screens of the first-run setup, in the order they are shown. */
enum class OnboardingStep { Hello, Welcome, Language, DefaultHome, WallpaperAccess, Import, Appearance }

/**
 * [Full] is the first-run setup. [FinishSetup] is opened from Settings and only repeats the
 * essentials the user skipped (see [finishSetupItems]).
 */
enum class OnboardingMode { Full, FinishSetup }

/** What the device already provides; read again whenever the settings activity resumes. */
data class SetupSystemState(
    val isDefaultHome: Boolean,
    /** Whether Android lets apps ask for the home role at all. */
    val homeRoleAvailable: Boolean,
    val hasWallpaperAccess: Boolean,
    /** Whether another launcher's layout can be imported; `null` while still looking. */
    val hasImportSources: Boolean?,
)

/** Position of a step among the steps with a progress bar, 1-based. */
data class StepProgress(val position: Int, val total: Int) {
    val fraction: Float get() = position.toFloat() / total
}

private val SetupSystemState.needsDefaultHome: Boolean get() = !isDefaultHome && homeRoleAvailable

/** Skipped essentials, offered again by "Finish Setting Up" in Settings. */
fun finishSetupItems(state: SetupSystemState): List<OnboardingStep> = listOfNotNull(
    OnboardingStep.DefaultHome.takeIf { state.needsDefaultHome },
    OnboardingStep.WallpaperAccess.takeIf { !state.hasWallpaperAccess },
)

/** Steps worth showing right now: a step whose job is already done (or impossible) is left out. */
fun onboardingSteps(state: SetupSystemState, mode: OnboardingMode): List<OnboardingStep> = when (mode) {
    OnboardingMode.FinishSetup -> finishSetupItems(state)
    OnboardingMode.Full -> OnboardingStep.entries.filter { step ->
        when (step) {
            OnboardingStep.DefaultHome -> state.needsDefaultHome
            OnboardingStep.WallpaperAccess -> !state.hasWallpaperAccess
            OnboardingStep.Import -> state.hasImportSources == true
            else -> true
        }
    }
}

/** The step after [current], or `null` when the flow is over. */
fun nextStep(current: OnboardingStep, steps: List<OnboardingStep>): OnboardingStep? = steps.firstOrNull { it > current }

/**
 * The step "back" returns to, or `null` when back leaves the flow. Hello is never replayed and
 * Welcome is the first screen back can reach.
 */
fun previousStep(current: OnboardingStep, steps: List<OnboardingStep>): OnboardingStep? =
    if (current <= OnboardingStep.Welcome) null else steps.lastOrNull { it < current && it != OnboardingStep.Hello }

/**
 * Where to be after the steps were recomputed, e.g. back from the system dialog: the same step
 * if it is still needed, otherwise the next one (`null` = the flow is over).
 */
fun resolveStep(current: OnboardingStep, steps: List<OnboardingStep>): OnboardingStep? =
    if (current in steps) current else nextStep(current, steps)

/**
 * Leaving [current] has to wait for the import lookup: until it is known whether the Import step
 * exists, moving on could skip it or make it pop up behind the user.
 */
fun needsImportLookup(current: OnboardingStep, mode: OnboardingMode, hasImportSources: Boolean?): Boolean =
    mode == OnboardingMode.Full && hasImportSources == null && current < OnboardingStep.Import

/**
 * Progress of [current] over the steps that are or were shown ([shown]), so finishing a step
 * (which then leaves [steps]) doesn't make the bar jump back. Hello and Welcome have no bar.
 */
fun stepProgress(current: OnboardingStep, steps: List<OnboardingStep>, shown: Set<OnboardingStep>): StepProgress? {
    if (current <= OnboardingStep.Welcome) return null
    val counted = (steps + shown + current).filter { it > OnboardingStep.Welcome }.distinct().sorted()
    return StepProgress(counted.indexOf(current) + 1, counted.size)
}
