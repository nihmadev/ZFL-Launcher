package com.zfl.launcher.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

/**
 * Imports the Home Screen (pages, folders, dock) from another launcher, e.g. iOS27L.
 * The user picks a launcher, reviews what will be imported, then confirms.
 */
@Composable
fun ImportScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources = remember { vm.importSources() }
    var selected by remember { mutableStateOf<ImportSource?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var failed by remember { mutableStateOf(false) }
    var removeOthers by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf(false) }

    fun load(source: ImportSource) {
        selected = source
        preview = null
        failed = false
        scope.launch {
            vm.previewImport(source)
                .onSuccess { preview = it }
                .onFailure { failed = true }
        }
    }

    SettingsPage(title = stringResource(R.string.import_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.import_description),
                color = SettingsStyle.SecondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.import_choose_launcher)) }
        item {
            SettingsCard {
                if (sources.isEmpty()) {
                    Text(
                        stringResource(R.string.import_no_launchers),
                        color = SettingsStyle.SecondaryText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                sources.forEach { source ->
                    SettingsRow(onClick = { load(source) }) {
                        SourceIcon(source)
                        Spacer(Modifier.width(14.dp))
                        RowTitle(source.label, modifier = Modifier.weight(1f))
                        if (source == selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (failed) {
            item { SectionFooter(stringResource(R.string.import_not_allowed)) }
        }

        preview?.let { p ->
            item { SectionHeader(stringResource(R.string.import_summary)) }
            item {
                SettingsCard {
                    InfoRow(stringResource(R.string.import_pages), p.pages.toString())
                    InfoRow(stringResource(R.string.import_folders), p.folders.toString())
                    InfoRow(stringResource(R.string.import_apps_on_home), p.appsOnHome.toString())
                    SwitchRow(
                        title = stringResource(R.string.import_remove_others),
                        description = pluralStringResource(R.plurals.import_remove_others_count, p.appsNotOnHome.size, p.appsNotOnHome.size),
                        checked = removeOthers,
                        onCheckedChange = { removeOthers = it },
                    )
                }
            }
            item { SectionFooter(stringResource(R.string.import_widgets_note)) }
            item {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(SettingsStyle.Link)
                        .clickable { confirming = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.import_action), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    val p = preview
    if (confirming && p != null) {
        ConfirmImportDialog(
            launcherName = p.source.label,
            onConfirm = {
                confirming = false
                vm.applyImport(p, removeOthers)
                Toast.makeText(context, R.string.import_done, Toast.LENGTH_SHORT).show()
                onBack()
            },
            onDismiss = { confirming = false },
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
