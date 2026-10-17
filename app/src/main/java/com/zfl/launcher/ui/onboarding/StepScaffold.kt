package com.zfl.launcher.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.R
import com.zfl.launcher.ui.settings.SettingsStyle
import com.zfl.launcher.ui.shape.SquircleShape

/** Sizes and colors of the setup screens (iOS Setup Assistant proportions). */
internal object OnboardingStyle {
    val Background = Color.Black
    val SideMargin = 24.dp
    val BottomMargin = 16.dp
    val ButtonHeight = 50.dp
    val LinkHeight = 44.dp
    val ProgressHeight = 4.dp
    val ProgressTrack = Color.White.copy(alpha = 0.16f)
    val GlyphSize = 64.dp
    val LogoSize = 96.dp
    val HeaderTop = 48.dp
    val WelcomeTitleSize = 34.sp
    val TitleSize = 30.sp
    val BodySize = 17.sp
    /** Darkening of the filled button while pressed. */
    val PressedScrim = Color.Black.copy(alpha = 0.22f)
    const val PROGRESS_MS = 300
}

/** The adaptive icon layer is 108dp, of which the middle 72dp is visible. */
private const val ICON_LAYER_RATIO = 108f / 72f

/**
 * Common frame of a setup step: progress bar, a glyph or live preview ([header]), title, one
 * line of why, optional [body], then the main button with an optional "Not Now" link under it.
 * The middle scrolls (large font scales); the buttons stay above the navigation bar.
 */
@Composable
internal fun StepScaffold(
    progress: StepProgress?,
    title: String,
    primaryText: String,
    onPrimary: () -> Unit,
    header: @Composable () -> Unit,
    subtitle: String? = null,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OnboardingStyle.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        StepProgressBar(progress)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingStyle.SideMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(OnboardingStyle.HeaderTop))
            header()
            Spacer(Modifier.height(24.dp))
            StepTitle(title)
            if (subtitle != null) {
                Spacer(Modifier.height(12.dp))
                Text(subtitle, color = SettingsStyle.SecondaryText, fontSize = OnboardingStyle.BodySize, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            body()
            Spacer(Modifier.height(24.dp))
        }
        BottomButtons(primaryText, onPrimary, primaryEnabled = primaryEnabled, secondaryText = secondaryText, onSecondary = onSecondary)
    }
}

@Composable
internal fun StepTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = OnboardingStyle.TitleSize) {
    Text(
        text,
        color = Color.White,
        fontSize = size,
        lineHeight = size * 1.15f,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Main button pinned to the bottom, with an optional text link under it. */
@Composable
internal fun BottomButtons(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = OnboardingStyle.SideMargin, end = OnboardingStyle.SideMargin, bottom = OnboardingStyle.BottomMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(primaryText, onPrimary, enabled = primaryEnabled)
        if (secondaryText != null) {
            Spacer(Modifier.height(6.dp))
            TextLinkButton(secondaryText, onSecondary)
        }
    }
}

/** iOS filled button: full width, capsule, accent color, darkens while pressed. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = OnboardingStyle.ButtonHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(LocalAccent.current.primary)
            .drawWithContent {
                drawContent()
                if (pressed) drawRect(OnboardingStyle.PressedScrim)
            }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = LocalAccent.current.onPrimary, fontSize = OnboardingStyle.BodySize, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** iOS plain text button ("Not Now"): accent text that fades while pressed. */
@Composable
internal fun TextLinkButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .heightIn(min = OnboardingStyle.LinkHeight)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = LocalAccent.current.primary.copy(alpha = if (pressed) 0.4f else 1f),
            fontSize = OnboardingStyle.BodySize,
            textAlign = TextAlign.Center,
        )
    }
}

/** Thin bar over the steps that are actually shown; keeps its height when hidden so nothing jumps. */
@Composable
private fun StepProgressBar(progress: StepProgress?) {
    val fraction by animateFloatAsState(progress?.fraction ?: 0f, tween(OnboardingStyle.PROGRESS_MS), label = "setupProgress")
    val description = progress?.let { stringResource(R.string.onboarding_progress, it.position, it.total) }
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OnboardingStyle.SideMargin, vertical = 12.dp)
            .height(OnboardingStyle.ProgressHeight)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .drawBehind {
                if (progress == null) return@drawBehind
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(OnboardingStyle.ProgressTrack, cornerRadius = radius)
                // Read in the draw phase: the animation only redraws the bar.
                drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            },
    )
}

/** Large accent glyph on top of a step, like the SF Symbols of the iOS setup screens. */
@Composable
internal fun StepGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = LocalAccent.current.primary, modifier = Modifier.size(OnboardingStyle.GlyphSize))
}

/** The app icon artwork (its full-bleed foreground layer) in the squircle of home screen icons. */
@Composable
internal fun AppLogo(modifier: Modifier = Modifier, size: Dp = OnboardingStyle.LogoSize) {
    val description = stringResource(R.string.onboarding_logo_description)
    Box(
        modifier
            .size(size)
            .clip(SquircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            // The layer bleeds past the icon outline for adaptive masking; only its middle shows.
            modifier = Modifier.requiredSize(size * ICON_LAYER_RATIO),
        )
    }
}
