package com.zfl.launcher.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.ImportSource

/**
 * Imports the Home Screen (pages, folders, dock) from another launcher, e.g. iOS27L.
 * The user picks a launcher, reviews what will be imported, then confirms.
 */
@Composable
fun ImportScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val sources by produceState<List<ImportSource>?>(null) { value = vm.importSources() }
    val state = rememberImportState(vm)
    val settings by vm.settings.state.collectAsStateWithLifecycle()

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
        item { ImportSourceList(sources, state) }

        if (state.failed) {
            item { SectionFooter(stringResource(R.string.import_not_allowed)) }
        }

        state.preview?.let { preview ->
            item { SectionHeader(stringResource(R.string.import_summary)) }
            item { ImportSummary(preview, state) }
            item { SectionFooter(stringResource(R.string.import_widgets_note)) }
            // Importing replaces the arrangement, so a locked layout has to be unlocked here first.
            if (settings.layoutLocked) {
                item { SectionFooter(stringResource(R.string.import_locked)) }
                item {
                    Spacer(Modifier.height(12.dp))
                    SettingsCard { SwitchRow(stringResource(R.string.layout_lock), true, vm.settings::setLayoutLocked) }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.import_action), onClick = { state.confirming = true }, enabled = !settings.layoutLocked)
            }
        }
    }

    ImportConfirmation(state) {
        Toast.makeText(context, R.string.import_done, Toast.LENGTH_SHORT).show()
        onBack()
    }
}
