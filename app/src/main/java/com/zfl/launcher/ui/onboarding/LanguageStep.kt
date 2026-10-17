package com.zfl.launcher.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.settings.AppLanguage
import com.zfl.launcher.settings.flagRegion
import com.zfl.launcher.settings.languageToStore
import com.zfl.launcher.settings.selectedLanguage
import com.zfl.launcher.ui.settings.RowTitle
import com.zfl.launcher.ui.settings.SettingsCard
import com.zfl.launcher.ui.settings.SettingsRow
import com.zfl.launcher.ui.settings.SettingsStyle
import java.util.Locale

/** Flag artwork per region of [com.zfl.launcher.settings.FLAG_REGIONS]. */
internal val FLAG_DRAWABLES: Map<String, Int> = mapOf(
    "US" to R.drawable.flag_us,
    "RU" to R.drawable.flag_ru,
    "PL" to R.drawable.flag_pl,
    "UA" to R.drawable.flag_ua,
    "BY" to R.drawable.flag_by,
    "ES" to R.drawable.flag_es,
    "FR" to R.drawable.flag_fr,
    "IT" to R.drawable.flag_it,
    "DE" to R.drawable.flag_de,
    "CN" to R.drawable.flag_cn,
    "JP" to R.drawable.flag_jp,
    "PT" to R.drawable.flag_pt,
    "KZ" to R.drawable.flag_kz,
    "TR" to R.drawable.flag_tr,
    "RS" to R.drawable.flag_rs,
)

private val FlagSize = 30.dp

/**
 * Picks ZFL's language from `locales_config.xml`. A tap applies it at once: the settings activity
 * takes the new configuration without being recreated, so the text changes in place.
 */
@Composable
internal fun LanguageStep(
    progress: StepProgress?,
    onSelect: (String?) -> Unit,
    onContinue: () -> Unit,
    primaryText: String = stringResource(R.string.onboarding_continue),
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val supported = remember { AppLanguage.supported(context) }
    val systemTags = remember(configuration) { AppLanguage.systemTags(context) }
    val override = remember(configuration) { AppLanguage.override(context) }
    // Checked right away; the new configuration arrives a moment later.
    var tapped by remember(configuration) { mutableStateOf<String?>(null) }
    val selected = tapped ?: selectedLanguage(supported, override, systemTags)

    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_language_title),
        primaryText = primaryText,
        onPrimary = onContinue,
        header = { StepGlyph(Icons.Rounded.Language) },
    ) {
        SettingsCard {
            supported.forEach { tag ->
                LanguageRow(tag, selected = tag == selected) {
                    if (tag != selected) {
                        tapped = tag
                        onSelect(languageToStore(tag, supported, systemTags))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(tag: String, selected: Boolean, onClick: () -> Unit) {
    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    // Each language in itself ("English", "Русский"), so anyone finds theirs.
    val native = remember(locale) {
        val name = if (locale.script.isEmpty()) locale.getDisplayLanguage(locale) else locale.getDisplayName(locale)
        name.replaceFirstChar { it.titlecase(locale) }
    }
    SettingsRow(
        onClick = onClick,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Flag(tag)
        Spacer(Modifier.width(14.dp))
        RowTitle(native, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Round flag of the language, or a neutral globe when there is none. */
@Composable
private fun Flag(tag: String) {
    val flag = flagRegion(tag)?.let(FLAG_DRAWABLES::get)
    val ring = Modifier
        .size(FlagSize)
        .clip(CircleShape)
        .border(0.5.dp, SettingsStyle.CardStroke, CircleShape)
    if (flag != null) {
        Image(painterResource(flag), contentDescription = null, contentScale = ContentScale.Crop, modifier = ring)
    } else {
        Box(ring.background(SettingsStyle.Tile.Gray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = SettingsStyle.SecondaryText, modifier = Modifier.size(20.dp))
        }
    }
}
