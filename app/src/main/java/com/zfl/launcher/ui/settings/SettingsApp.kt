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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zfl.launcher.data.libraryTitles
import com.zfl.launcher.R
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.ui.onboarding.LanguageStep
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.zfl.launcher.ui.onboarding.OnboardingFlow
import com.zfl.launcher.ui.onboarding.OnboardingMode
import com.zfl.launcher.ui.theme.Motion

/** Screens of the settings app. */
enum class SettingsRoute {
    Main, HomeBehavior, General, Grid, Transition, Animations, Glass, Icons, HomeScreen, ContextMenu,
    HiddenApps, AppIcons, AppNames, License, Import, Weather, FinishSetup, Language,
    HomeIcons, DockIcons, FolderIcons,
    AppLibrary, LibraryPinned, LibraryCategories, LibraryCategory,
}

/** Size settings page of each icon area. */
private val IconArea.route: SettingsRoute
    get() = when (this) {
        IconArea.Home -> SettingsRoute.HomeIcons
        IconArea.Dock -> SettingsRoute.DockIcons
        IconArea.Folder -> SettingsRoute.FolderIcons
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
    // Category open in the editor; null while it is a new one, not created yet.
    var editingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // Group names follow the UI language, read from the Activity like the launcher does.
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { vm.libraryTitles.value = libraryTitles(context) }

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
            SettingsRoute.Icons -> IconSettingsScreen(vm, onOpenArea = { navigate(it.route) }, onBack = back)
            SettingsRoute.HomeIcons -> AreaIconsScreen(vm, IconArea.Home, back)
            SettingsRoute.DockIcons -> AreaIconsScreen(vm, IconArea.Dock, back)
            SettingsRoute.FolderIcons -> AreaIconsScreen(vm, IconArea.Folder, back)
            SettingsRoute.HomeBehavior -> HomeBehaviorScreen(vm, back)
            SettingsRoute.HomeScreen -> HomeScreenSettingsScreen(vm, back)
            SettingsRoute.ContextMenu -> MenuSettingsScreen(vm, back)
            SettingsRoute.HiddenApps -> AppListScreen(vm, AppListMode.Hidden, back)
            SettingsRoute.AppIcons -> AppListScreen(vm, AppListMode.Icon, back)
            SettingsRoute.AppNames -> AppListScreen(vm, AppListMode.Name, back)
            SettingsRoute.License -> LicenseScreen(back)
            SettingsRoute.Import -> ImportScreen(vm, back)
            SettingsRoute.Weather -> WeatherSettingsScreen(back)
            SettingsRoute.FinishSetup -> OnboardingFlow(vm, actions, OnboardingMode.FinishSetup, onFinish = back)
            SettingsRoute.AppLibrary -> LibrarySettingsScreen(vm, navigate, back)
            SettingsRoute.LibraryPinned -> LibraryPinnedScreen(vm, back)
            SettingsRoute.LibraryCategories -> LibraryCategoriesScreen(
                vm,
                onEdit = { id -> editingCategory = id; navigate(SettingsRoute.LibraryCategory) },
                onBack = back,
            )
            SettingsRoute.LibraryCategory -> CategoryEditorScreen(vm, editingCategory, onCreated = { editingCategory = it }, onBack = back)
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
