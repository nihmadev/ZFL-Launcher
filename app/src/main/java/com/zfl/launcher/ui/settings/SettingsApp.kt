package com.zfl.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.zfl.launcher.R
import com.zfl.launcher.ui.onboarding.LanguageStep
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.zfl.launcher.ui.onboarding.OnboardingFlow
import com.zfl.launcher.ui.onboarding.OnboardingMode
import com.zfl.launcher.ui.theme.Motion

/** Screens of the settings app. */
enum class SettingsRoute {
    Main, General, Grid, Transition, Animations, Glass, Icons, HomeScreen, ContextMenu,
    HiddenApps, AppIcons, AppNames, License, Import, Weather, FinishSetup, Language,
}

/**
 * Root of the settings UI: a simple back stack with iOS "push" transitions.
 * The new screen slides in from the right on top, the old one drifts left and dims.
 */
@Composable
fun SettingsApp(vm: SettingsViewModel, actions: SettingsActions, initialRoute: SettingsRoute? = null) {
    // Opened from a widget: start on its screen, with the main page behind it for "back".
    var stack by rememberSaveable { mutableStateOf(arrayListOf(*listOfNotNull(SettingsRoute.Main, initialRoute).toTypedArray())) }
    val navigate: (SettingsRoute) -> Unit = { stack = ArrayList(stack + it) }
    val back: () -> Unit = { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    AnimatedContent(
        targetState = stack,
        contentKey = { it.last() },
        transitionSpec = { pushTransition(isPush = targetState.size >= initialState.size) },
        label = "settingsNavigation",
    ) { current ->
        when (current.last()) {
            SettingsRoute.Main -> MainSettingsScreen(vm, actions, navigate)
            SettingsRoute.Language -> LanguageStep(
                progress = null,
                onSelect = actions::setAppLanguage,
                onContinue = back,
                primaryText = stringResource(R.string.action_done),
            )
            SettingsRoute.General -> GeneralSettingsScreen(vm, actions, back)
            SettingsRoute.Grid -> GridSettingsScreen(vm, back)
            SettingsRoute.Transition -> TransitionSettingsScreen(vm, back)
            SettingsRoute.Animations -> AnimationSettingsScreen(vm, back)
            SettingsRoute.Glass -> GlassSettingsScreen(vm, actions, back)
            SettingsRoute.Icons -> IconSettingsScreen(vm, back)
            SettingsRoute.HomeScreen -> HomeScreenSettingsScreen(vm, back)
            SettingsRoute.ContextMenu -> MenuSettingsScreen(vm, back)
            SettingsRoute.HiddenApps -> AppListScreen(vm, AppListMode.Hidden, back)
            SettingsRoute.AppIcons -> AppListScreen(vm, AppListMode.Icon, back)
            SettingsRoute.AppNames -> AppListScreen(vm, AppListMode.Name, back)
            SettingsRoute.License -> LicenseScreen(back)
            SettingsRoute.Import -> ImportScreen(vm, back)
            SettingsRoute.Weather -> WeatherSettingsScreen(back)
            SettingsRoute.FinishSetup -> OnboardingFlow(vm, actions, OnboardingMode.FinishSetup, onFinish = back)
        }
    }
}

/** iOS push (or pop when ![isPush]); also used between onboarding steps. */
internal fun pushTransition(isPush: Boolean): ContentTransform {
    val slide = Motion.push<IntOffset>()
    val fade = Motion.push<Float>()
    return if (isPush) {
        (slideInHorizontally(slide) { it } togetherWith
            (slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade, targetAlpha = 0.4f)))
            .apply { targetContentZIndex = 1f }
    } else {
        ((slideInHorizontally(slide) { -it / 4 } + fadeIn(fade, initialAlpha = 0.4f)) togetherWith
            slideOutHorizontally(slide) { it })
            .apply { targetContentZIndex = -1f }
    }
}
