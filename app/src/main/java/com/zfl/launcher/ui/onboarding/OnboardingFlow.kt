package com.zfl.launcher.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.zfl.launcher.data.ImportSource
import com.zfl.launcher.ui.settings.SettingsActions
import com.zfl.launcher.ui.settings.SettingsViewModel
import com.zfl.launcher.ui.settings.pushTransition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Hello hands over to Welcome with a crossfade (both are black; Hello has already faded out). */
private const val HELLO_FADE_IN_MS = 600
private const val HELLO_FADE_OUT_MS = 300

/** What the setup knows about the device, from the activity (refreshed in onResume). */
fun SettingsActions.setupState(hasImportSources: Boolean?): SetupSystemState = SetupSystemState(
    isDefaultHome = isDefaultHome,
    homeRoleAvailable = isHomeRoleAvailable,
    hasWallpaperAccess = hasWallpaperAccess,
    hasImportSources = hasImportSources,
)

/**
 * The first-run setup ([OnboardingMode.Full]) or "Finish Setting Up" ([OnboardingMode.FinishSetup]).
 *
 * The steps come from [onboardingSteps] over the live system state, so a step whose job got done
 * outside (home role granted, access given) disappears and the flow moves on. The current step
 * and the steps seen so far are saved, so recreation (language change) and process death return
 * to the same step without replaying Hello. Each step applies its action immediately; [onFinish]
 * runs after the last one (Get Started on Appearance in the full setup).
 */
@Composable
fun OnboardingFlow(vm: SettingsViewModel, actions: SettingsActions, mode: OnboardingMode, onFinish: () -> Unit) {
    val sources by produceState<List<ImportSource>?>(if (mode == OnboardingMode.Full) null else emptyList()) {
        if (mode == OnboardingMode.Full) value = vm.importSources()
    }
    fun steps(): List<OnboardingStep> = onboardingSteps(actions.setupState(sources?.isNotEmpty()), mode)

    val steps = steps()
    var current by rememberSaveable { mutableStateOf(steps.firstOrNull() ?: OnboardingStep.Appearance) }
    var shown by rememberSaveable { mutableStateOf(setOf(current)) }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun go(step: OnboardingStep?) {
        when {
            finished -> Unit
            step == null -> {
                finished = true
                onFinish()
            }
            else -> {
                current = step
                shown = shown + step
            }
        }
    }

    /** Leaves [from]; a second tap during the transition (already left) is ignored. */
    fun next(from: OnboardingStep) {
        if (current != from) return
        scope.launch {
            if (needsImportLookup(from, mode, sources?.isNotEmpty())) snapshotFlow { sources }.first { it != null }
            if (current == from) go(nextStep(from, steps()))
        }
    }

    // Back from a system screen: a step whose job is now done moves on by itself.
    LaunchedEffect(steps) {
        if (current !in steps) go(resolveStep(current, steps))
    }

    val previous = previousStep(current, steps)
    // On Hello and Welcome back is not handled: it leaves the app, the setup stays unfinished.
    BackHandler(enabled = previous != null) { go(previous) }

    AnimatedContent(
        targetState = current,
        transitionSpec = { stepTransition(initialState, targetState) },
        label = "onboarding",
    ) { step ->
        val progress = stepProgress(step, steps, shown)
        when (step) {
            OnboardingStep.Hello -> HelloScreen(onFinished = { next(step) })
            OnboardingStep.Welcome -> WelcomeScreen(onContinue = { next(step) })
            OnboardingStep.Language -> LanguageStep(progress, onSelect = actions::setAppLanguage, onContinue = { next(step) })
            OnboardingStep.DefaultHome -> DefaultHomeStep(progress, onRequest = actions::requestDefaultHome, onSkip = { next(step) })
            OnboardingStep.WallpaperAccess ->
                WallpaperAccessStep(progress, onRequest = actions::requestWallpaperAccess, onSkip = { next(step) })
            OnboardingStep.Import -> ImportStep(vm, sources.orEmpty(), progress, onNext = { next(step) })
            OnboardingStep.Appearance -> AppearanceStep(vm, progress, onNext = { next(step) })
        }
    }
}

private fun stepTransition(from: OnboardingStep, to: OnboardingStep): ContentTransform =
    if (from == OnboardingStep.Hello) {
        fadeIn(tween(HELLO_FADE_IN_MS)) togetherWith fadeOut(tween(HELLO_FADE_OUT_MS))
    } else {
        pushTransition(isPush = to > from)
    }
