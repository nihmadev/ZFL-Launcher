package com.zfl.launcher.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.ImportSource
import com.zfl.launcher.ui.settings.IconAppearanceRows
import com.zfl.launcher.ui.settings.IconPreviewRow
import com.zfl.launcher.ui.settings.ImportConfirmation
import com.zfl.launcher.ui.settings.ImportSourceList
import com.zfl.launcher.ui.settings.ImportSummary
import com.zfl.launcher.ui.settings.SectionFooter
import com.zfl.launcher.ui.settings.SettingsCard
import com.zfl.launcher.ui.settings.SettingsViewModel
import com.zfl.launcher.ui.settings.rememberImportState
import com.zfl.launcher.ui.settings.sampleApps

/** Apps in the live icon preview of the Appearance step. */
private const val PREVIEW_APPS = 4

/**
 * Asks for the home role. The flow moves on by itself once ZFL is the home app (checked when
 * the activity resumes); after a refusal it stays here and can be skipped.
 */
@Composable
internal fun DefaultHomeStep(progress: StepProgress?, onRequest: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_home_title),
        subtitle = stringResource(R.string.onboarding_home_body),
        primaryText = stringResource(R.string.onboarding_home_action),
        onPrimary = onRequest,
        secondaryText = stringResource(R.string.onboarding_not_now),
        onSecondary = onSkip,
        header = { StepGlyph(Icons.Rounded.Home) },
    )
}

/** Opens the system "All files access" screen; without it the glass keeps its flat fallback. */
@Composable
internal fun WallpaperAccessStep(progress: StepProgress?, onRequest: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_wallpaper_title),
        subtitle = stringResource(R.string.onboarding_wallpaper_body),
        primaryText = stringResource(R.string.onboarding_wallpaper_action),
        onPrimary = onRequest,
        secondaryText = stringResource(R.string.onboarding_not_now),
        onSecondary = onSkip,
        header = { StepGlyph(Icons.Rounded.BlurOn) },
    )
}

/** The Import screen of Settings in one step: same list, review, alert and apply. */
@Composable
internal fun ImportStep(vm: SettingsViewModel, sources: List<ImportSource>, progress: StepProgress?, onNext: () -> Unit) {
    val state = rememberImportState(vm)
    // With a single launcher there is nothing to choose: review it right away.
    LaunchedEffect(sources) {
        if (state.selected == null) sources.singleOrNull()?.let(state::select)
    }
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_import_title),
        subtitle = stringResource(R.string.onboarding_import_body),
        primaryText = stringResource(R.string.import_action),
        onPrimary = { state.confirming = true },
        primaryEnabled = state.preview != null,
        secondaryText = stringResource(R.string.onboarding_not_now),
        onSecondary = onNext,
        header = { StepGlyph(Icons.Rounded.SystemUpdateAlt) },
    ) {
        ImportSourceList(sources, state)
        if (state.failed) SectionFooter(stringResource(R.string.import_not_allowed))
        state.preview?.let { preview ->
            Spacer(Modifier.height(16.dp))
            ImportSummary(preview, state)
        }
    }
    ImportConfirmation(state, onApplied = onNext)
}

/**
 * Last step: icon look with a live row of the user's own icons. The choice is saved on tap (the
 * same setting as Settings › Icons), so Get Started is the only button.
 */
@Composable
internal fun AppearanceStep(vm: SettingsViewModel, progress: StepProgress?, onNext: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps = sampleApps(vm, PREVIEW_APPS)
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_appearance_title),
        subtitle = stringResource(R.string.onboarding_appearance_body),
        primaryText = stringResource(R.string.onboarding_get_started),
        onPrimary = onNext,
        header = { SettingsCard { IconPreviewRow(apps, settings) } },
    ) {
        SettingsCard {
            IconAppearanceRows(settings.iconAppearance) { appearance ->
                vm.settings.update { it.copy(iconAppearance = appearance) }
            }
        }
    }
}
