package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.data.SMART_STACK_SIZES
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.data.smartStackKinds

/** Gallery selection for the Smart Stack detail. */
internal data object SmartStackPick

/** Gallery row of the Smart Stack. */
@Composable
internal fun SmartStackRow(onClick: () -> Unit) =
    GalleryRow({ SmartStackIcon(52.dp) }, stringResource(R.string.stack_smart), onClick)

/** The Smart Stack sizes, swiped like a widget's, each previewing the stack it adds (swipe it vertically). */
@Composable
internal fun SmartStackDetail(onAdd: (WidgetSize) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState { SMART_STACK_SIZES.size }
    val name = stringResource(R.string.stack_smart)
    GalleryDetail(
        icon = { SmartStackIcon(40.dp) },
        name = name,
        title = name,
        description = stringResource(R.string.stack_smart_description),
        pager = pager,
        onAdd = { onAdd(SMART_STACK_SIZES[pager.currentPage]) },
        onBack = onBack,
        onClose = onClose,
    ) { page ->
        val size = SMART_STACK_SIZES[page]
        val preview = remember(size) {
            WidgetStackEntry("preview-stack-${size.name}", size, smartStackKinds(size).map { WidgetEntry("preview-${it.name}-${size.name}", it, size, photo = null) }, 0)
        }
        val design = size.designSize
        WidgetStackBody(preview, Modifier.size(design.width * 0.9f, design.height * 0.9f))
    }
}

@Composable
private fun SmartStackIcon(size: Dp) = Box { GlyphTile(Icons.Rounded.Layers, size, Color(0xFF5E5CE6), Color.White) }
