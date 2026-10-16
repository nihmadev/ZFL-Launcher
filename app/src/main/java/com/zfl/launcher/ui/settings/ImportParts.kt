package com.zfl.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.zfl.launcher.R
import com.zfl.launcher.data.ImportPreview
import com.zfl.launcher.data.ImportSource
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.shape.SquircleShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Picking a launcher, reviewing what its layout would give and applying it. Shared by the
 * Import screen in Settings and the Import step of the onboarding.
 */
@Stable
class ImportState internal constructor(private val vm: SettingsViewModel, private val scope: CoroutineScope) {
    var selected: ImportSource? by mutableStateOf(null)
        private set
    var preview: ImportPreview? by mutableStateOf(null)
        private set
    /** The selected launcher doesn't let other apps read its layout. */
    var failed: Boolean by mutableStateOf(false)
        private set
    var removeOthers: Boolean by mutableStateOf(true)
    /** The "Import from …?" alert is open. */
    var confirming: Boolean by mutableStateOf(false)

    fun select(source: ImportSource) {
        selected = source
        preview = null
        failed = false
        scope.launch {
            vm.previewImport(source)
                .onSuccess { if (selected == source) preview = it }
                .onFailure { if (selected == source) failed = true }
        }
    }

    /** Replaces the Home Screen: one layout change, saved once. */
    internal fun apply() {
        confirming = false
        preview?.let { vm.applyImport(it, removeOthers) }
    }
}

@Composable
fun rememberImportState(vm: SettingsViewModel): ImportState {
    val scope = rememberCoroutineScope()
    return remember(vm) { ImportState(vm, scope) }
}

/** Card listing the launchers to import from; [sources] is `null` while they are being looked up. */
@Composable
fun ImportSourceList(sources: List<ImportSource>?, state: ImportState) {
    SettingsCard {
        if (sources?.isEmpty() == true) {
            Text(
                stringResource(R.string.import_no_launchers),
                color = SettingsStyle.SecondaryText,
                fontSize = 15.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        sources?.forEach { source ->
            SettingsRow(onClick = { state.select(source) }) {
                SourceIcon(source)
                Spacer(Modifier.width(14.dp))
                RowTitle(source.label, modifier = Modifier.weight(1f))
                if (source == state.selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** What the import would bring (pages, folders, apps) and the "remove other apps" switch. */
@Composable
fun ImportSummary(preview: ImportPreview, state: ImportState) {
    SettingsCard {
        InfoRow(stringResource(R.string.import_pages), preview.pages.toString())
        InfoRow(stringResource(R.string.import_folders), preview.folders.toString())
        InfoRow(stringResource(R.string.import_apps_on_home), preview.appsOnHome.toString())
        SwitchRow(
            title = stringResource(R.string.import_remove_others),
            description = pluralStringResource(
                R.plurals.import_remove_others_count, preview.appsNotOnHome.size, preview.appsNotOnHome.size,
            ),
            checked = state.removeOthers,
            onCheckedChange = { state.removeOthers = it },
        )
    }
}

/** Shows the confirmation alert while [ImportState.confirming]; [onApplied] runs after the import. */
@Composable
fun ImportConfirmation(state: ImportState, onApplied: () -> Unit) {
    val preview = state.preview
    if (state.confirming && preview != null) {
        ConfirmImportDialog(
            launcherName = preview.source.label,
            onConfirm = {
                state.apply()
                onApplied()
            },
            onDismiss = { state.confirming = false },
        )
    }
}

@Composable
private fun SourceIcon(source: ImportSource) {
    val bitmap = remember(source.packageName) { source.icon?.toBitmap(96, 96)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(SettingsStyle.IconTileSize).clip(SquircleShape))
    } else {
        Spacer(Modifier.size(SettingsStyle.IconTileSize))
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    SettingsRow(onClick = null) {
        RowTitle(title, modifier = Modifier.weight(1f))
        Text(value, color = SettingsStyle.SecondaryText, fontSize = 16.sp)
    }
}

/** iOS alert: title, message, "Cancel | Import". */
@Composable
private fun ConfirmImportDialog(launcherName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val shape = ContinuousRoundedShape(20.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(290.dp)
                .background(Color(0xF02C2C2E), shape)
                .border(1.dp, SettingsStyle.CardStroke, shape),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) {
                Text(
                    stringResource(R.string.import_confirm_title, launcherName),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.import_confirm_message),
                    color = SettingsStyle.SecondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.15f)))
            Row(Modifier.height(48.dp)) {
                AlertButton(stringResource(R.string.action_cancel), bold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                Box(Modifier.width(0.5.dp).height(48.dp).background(Color.White.copy(alpha = 0.15f)))
                AlertButton(stringResource(R.string.import_action), bold = true, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, bold: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = SettingsStyle.Link, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
