package com.zfl.launcher.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            item {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(SettingsStyle.Link)
                        .clickable { state.confirming = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.import_action), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    ImportConfirmation(state) {
        Toast.makeText(context, R.string.import_done, Toast.LENGTH_SHORT).show()
        onBack()
    }
}
