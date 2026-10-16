package com.zfl.launcher.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import kotlinx.coroutines.launch
import com.zfl.launcher.ui.theme.Motion

private const val APPEAR_MS = 700
private const val APPEAR_FIRST_DELAY_MS = 150
private const val APPEAR_STAGGER_MS = 220
private val AppearRise = 24.dp

/** "Welcome to ZFL Launcher": the logo, the title and Continue appear one after another. */
@Composable
internal fun WelcomeScreen(onContinue: () -> Unit) {
    // Icon, title, button. One-shot animations; nothing keeps running once they settle.
    val appear = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        appear.forEachIndexed { index, item ->
            launch {
                item.animateTo(1f, tween(APPEAR_MS, delayMillis = APPEAR_FIRST_DELAY_MS + index * APPEAR_STAGGER_MS, easing = Motion.IosEasing))
            }
        }
    }
    fun Modifier.appearing(index: Int): Modifier = graphicsLayer {
        val p = appear[index].value
        alpha = p
        translationY = (1f - p) * AppearRise.toPx()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(OnboardingStyle.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AppLogo(Modifier.appearing(0))
        Spacer(Modifier.height(32.dp))
        StepTitle(
            // The app name never breaks across lines.
            stringResource(R.string.onboarding_welcome_title, stringResource(R.string.app_name).replace(' ', '\u00A0')),
            size = OnboardingStyle.WelcomeTitleSize,
            modifier = Modifier
                .appearing(1)
                .fillMaxWidth()
                .padding(horizontal = OnboardingStyle.SideMargin),
        )
        Spacer(Modifier.weight(1.4f))
        BottomButtons(stringResource(R.string.onboarding_continue), onContinue, modifier = Modifier.appearing(2))
    }
}
