package com.zfl.launcher.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.GridMargins
import com.zfl.launcher.ui.home.HomePreview
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Everything the Screen Grid page edits before "Apply". */
private data class GridDraft(val grid: GridSpec, val margins: GridMargins, val alignment: GridAlignment)

/**
 * Columns, rows, margins and vertical position of the home grid, with a live preview of the
 * user's own home screen moved to the draft grid. Nothing is written until "Apply"; Back and
 * "Cancel" leave everything as it was. A grid some widget doesn't fit can't be applied.
 */
@Composable
fun GridSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val saved = GridDraft(layout?.grid ?: vm.savedGrid(), settings.gridMargins, settings.gridAlignment)
    // Null until the user changes something: the page follows the saved values until then.
    var edited by remember { mutableStateOf<GridDraft?>(null) }
    val draft = edited ?: saved
    val preview = remember(apps, settings, layout, draft.grid) { vm.gridPreview(apps, settings, draft.grid) }
    val fits = preview != null
    val update: (GridDraft) -> Unit = { edited = it }

    SettingsPage(
        title = stringResource(R.string.row_grid),
        onBack = onBack,
        action = { ResetAction { update(GridDraft(GridSpec.Default, GridMargins.Default, GridAlignment.Top)) } },
    ) {
        item {
            val shape = ContinuousRoundedShape(34.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HomePreview(
                    state = preview ?: vm.homePreview(apps, settings),
                    wallpaper = wallpaper,
                    prefs = settings.copy(gridMargins = draft.margins, gridAlignment = draft.alignment),
                    modifier = Modifier
                        .width(232.dp)
                        .clip(shape)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), shape),
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            SettingsCard {
                Choice(stringResource(R.string.grid_columns), GridSpec.HOME_COLUMNS.toList(), draft.grid.columns) { columns ->
                    update(draft.copy(grid = GridSpec.home(columns, draft.grid.rows)))
                }
                Choice(stringResource(R.string.grid_rows), GridSpec.HOME_ROWS.toList(), draft.grid.rows) { rows ->
                    update(draft.copy(grid = GridSpec.home(draft.grid.columns, rows)))
                }
            }
        }
        if (!fits) item { SectionFooter(stringResource(R.string.grid_too_small)) }
        item { SectionHeader(stringResource(R.string.grid_section_margins)) }
        item {
            SettingsCard {
                MarginRow(stringResource(R.string.grid_margin_side), draft.margins.side, Tokens.GridSideMarginMax) {
                    update(draft.copy(margins = draft.margins.copy(side = it)))
                }
                MarginRow(stringResource(R.string.grid_margin_top), draft.margins.top, Tokens.GridVerticalMarginMax) {
                    update(draft.copy(margins = draft.margins.copy(top = it)))
                }
                MarginRow(stringResource(R.string.grid_margin_bottom), draft.margins.bottom, Tokens.GridVerticalMarginMax) {
                    update(draft.copy(margins = draft.margins.copy(bottom = it)))
                }
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.grid_alignment))
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        options = GridAlignment.entries.map { stringResource(it.labelRes) },
                        selectedIndex = GridAlignment.entries.indexOf(draft.alignment),
                        onSelect = { update(draft.copy(alignment = GridAlignment.entries[it])) },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                stringResource(R.string.grid_apply),
                enabled = fits && draft != saved,
                onClick = {
                    scope.launch { if (vm.applyGrid(draft.grid, draft.margins, draft.alignment)) onBack() }
                },
            )
        }
        item {
            Text(
                stringResource(R.string.action_cancel),
                color = LocalAccent.current.primary,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(ContinuousRoundedShape(12.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** A titled segmented choice between [options]. */
@Composable
private fun Choice(title: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
        RowTitle(title)
        Spacer(Modifier.height(10.dp))
        SegmentedControl(
            options = options.map { it.toString() },
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
        )
    }
}

/** A margin slider; the draft (and so the preview) follows the finger. */
@Composable
private fun MarginRow(title: String, value: Dp, max: Dp, onChange: (Dp) -> Unit) {
    SliderRow(
        title = title,
        value = value.value,
        valueText = "${value.value.roundToInt()} dp",
        onValueChange = { onChange(it.roundToInt().dp) },
        onValueChangeFinished = {},
        valueRange = 0f..max.value,
    )
}

private val GridAlignment.labelRes: Int
    get() = when (this) {
        GridAlignment.Top -> R.string.grid_align_top
        GridAlignment.Center -> R.string.grid_align_center
        GridAlignment.Bottom -> R.string.grid_align_bottom
    }
