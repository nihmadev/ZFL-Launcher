package com.zfl.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.onboarding.OnboardingFlow
import com.zfl.launcher.ui.onboarding.OnboardingMode
import kotlin.math.roundToInt
import com.zfl.launcher.ui.theme.Motion

/** Screens of the settings app. */
enum class SettingsRoute {
    Main, General, Grid, Transition, Animations, Glass, Icons, HomeScreen, ContextMenu,
    HiddenApps, AppIcons, AppNames, License, Import, Weather, FinishSetup,
}

/**
 * Root of the settings UI: a simple back stack with iOS "push" transitions.
 * The new screen slides in from the right on top, the old one drifts left and dims.
 */
@Composable
fun SettingsApp(vm: SettingsViewModel, actions: SettingsActions, initialRoute: SettingsRoute? = null) {
    // Opened from a widget: start on its screen, with the main page behind it for "back".
    var stack by remember { mutableStateOf(listOfNotNull(SettingsRoute.Main, initialRoute)) }
    val navigate: (SettingsRoute) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    AnimatedContent(
        targetState = stack,
        contentKey = { it.last() },
        transitionSpec = { pushTransition(isPush = targetState.size >= initialState.size) },
        label = "settingsNavigation",
    ) { current ->
        when (current.last()) {
            SettingsRoute.Main -> MainSettingsScreen(vm, actions, navigate)
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

/**
 * Common layout of a settings screen.
 *
 * The main page ([onBack] == null) has a large left-aligned title on black, like iOS27L.
 * Sub pages get a back button, a centered title and the blurred wallpaper as background.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val surface = if (onBack == null) SettingsSurface.Solid else SettingsSurface.Glass
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalSettingsSurface provides surface) {
        Box(
            Modifier
                .fillMaxSize()
                .settingsBackground(surface),
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                if (onBack == null) LargeTitleBar(title, action) else TopBar(title, onBack, action)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + navBarPadding),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun Modifier.settingsBackground(surface: SettingsSurface): Modifier {
    if (surface == SettingsSurface.Solid) return drawBehind { drawRect(SettingsStyle.Background) }
    val glass = LocalGlassSource.current
    return drawBehind {
        if (glass != null) {
            // The blurred wallpaper, stretched to the screen and darkened for contrast.
            drawImage(
                image = glass.image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawRect(Color.Black.copy(alpha = 0.45f))
        } else {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2238), Color(0xFF0B0E18), Color.Black)))
        }
    }
}

@Composable
private fun LargeTitleBar(title: String, action: (@Composable () -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
    ) {
        GlassCircleButton(
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
        )
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}
