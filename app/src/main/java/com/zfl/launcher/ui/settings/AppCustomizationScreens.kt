package com.zfl.launcher.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.importCustomIcon
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import kotlinx.coroutines.launch

/** Which customization an [AppListScreen] edits. */
enum class AppListMode { Hidden, Icon, Name }

/**
 * List of every installed app, one glass capsule per app (as in iOS27L), used for
 * hiding apps, picking a custom icon or renaming them.
 */
@Composable
fun AppListScreen(vm: SettingsViewModel, mode: AppListMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val visibleApps = apps.filter { it.packageName != context.packageName }

    // App waiting for the photo picker result; saved so it survives process recreation.
    var pickingIconFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AppInfo?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pickingIconFor
        pickingIconFor = null
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (!importCustomIcon(context, vm.settings, key, uri)) {
                Toast.makeText(context, R.string.icon_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val (title, description) = when (mode) {
        AppListMode.Hidden -> R.string.row_hidden_apps to R.string.hidden_apps_description
        AppListMode.Icon -> R.string.row_change_icon to R.string.change_icon_description
        AppListMode.Name -> R.string.row_change_name to R.string.change_name_description
    }

    SettingsPage(title = stringResource(title), onBack = onBack) {
        item {
            Text(
                stringResource(description),
                color = SettingsStyle.SecondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            )
        }
        items(visibleApps, key = { it.key }) { app ->
            val customLabel = settings.customLabels[app.key]
            val label = customLabel ?: app.label
            AppCapsule(app, label, onClick = {
                when (mode) {
                    AppListMode.Hidden -> vm.settings.setAppHidden(app.key, app.key !in settings.hiddenApps)
                    AppListMode.Icon -> {
                        pickingIconFor = app.key
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    AppListMode.Name -> renaming = app
                }
            }) {
                when (mode) {
                    AppListMode.Hidden -> IosSwitch(
                        checked = app.key in settings.hiddenApps,
                        onCheckedChange = { vm.settings.setAppHidden(app.key, it) },
                    )
                    AppListMode.Icon ->
                        if (app.key in settings.customIcons) ResetButton { vm.settings.resetCustomIcon(app.key) } else Chevron()
                    AppListMode.Name ->
                        if (customLabel != null) ResetButton { vm.settings.setCustomLabel(app.key, null) } else Chevron()
                }
            }
        }
    }

    renaming?.let { app ->
        RenameDialog(
            originalLabel = app.label,
            currentLabel = settings.customLabels[app.key] ?: app.label,
            onDismiss = { renaming = null },
            onSave = { newLabel ->
                // Saving the original name (or an empty one) simply removes the override.
                vm.settings.setCustomLabel(app.key, newLabel.takeIf { it.isNotBlank() && it != app.label })
                renaming = null
            },
        )
    }
}

/** One app as a flat capsule (cheap in long lists): icon, name, optional [subtitle], [trailing]. */
@Composable
internal fun AppCapsule(
    app: AppInfo,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    val shape = ContinuousRoundedShape(22.dp)
    Box(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(SettingsStyle.GlassCard)
            .border(1.dp, SettingsStyle.CardStroke, shape),
    ) {
        SettingsRow(onClick = onClick) {
            AppIconImage(app, size = 34.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                RowTitle(label)
                if (subtitle != null) Text(subtitle, color = SettingsStyle.SecondaryText, fontSize = SettingsStyle.SubtitleSize, maxLines = 1)
            }
            trailing()
        }
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    Text(
        stringResource(R.string.action_reset),
        color = LocalAccent.current.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** iOS-style alert with a text field: title, field, then "Cancel | Save" buttons. */
@Composable
private fun RenameDialog(originalLabel: String, currentLabel: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(currentLabel, TextRange(0, currentLabel.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                    stringResource(R.string.rename_title),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.rename_original, originalLabel),
                    color = SettingsStyle.SecondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(LocalAccent.current.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .background(Color(0xFF1C1C1E), ContinuousRoundedShape(10.dp))
                        .border(1.dp, SettingsStyle.CardStroke, ContinuousRoundedShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.15f)),
            )
            Row(Modifier.height(48.dp)) {
                DialogButton(stringResource(R.string.action_cancel), Modifier.weight(1f), bold = false, onClick = onDismiss)
                Box(
                    Modifier
                        .width(0.5.dp)
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.15f)),
                )
                DialogButton(stringResource(R.string.action_save), Modifier.weight(1f), bold = true) { onSave(text.text) }
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, modifier: Modifier, bold: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = LocalAccent.current.primary, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
