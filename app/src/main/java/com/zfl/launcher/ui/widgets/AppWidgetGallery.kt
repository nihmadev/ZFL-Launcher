package com.zfl.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gallery row of an app that offers widgets. */
@Composable
internal fun AppWidgetsRow(group: WidgetProviderGroup, onClick: () -> Unit) =
    GalleryRow({ AppIconTile(group, 52.dp) }, group.label, onClick)

/** The widgets of one app, swiped like the sizes of a built-in widget. */
@Composable
internal fun AppWidgetsDetail(group: WidgetProviderGroup, onAdd: (AppWidgetProviderInfo) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val widgets = remember { AppWidgets.get(context) }
    val pager = rememberPagerState { group.providers.size }
    val info = group.providers[pager.currentPage]
    val span = remember(info) { widgets.defaultSpan(info) }
    GalleryDetail(
        icon = { AppIconTile(group, 40.dp) },
        name = group.label,
        title = remember(info) { info.loadLabel(context.packageManager) },
        // Android 12 widgets may describe themselves; otherwise show the size, e.g. "4 × 2".
        description = remember(info) { info.loadDescription(context)?.toString() } ?: "${span.columns} × ${span.rows}",
        pager = pager,
        onAdd = { onAdd(info) },
        onBack = onBack,
        onClose = onClose,
    ) { page -> ProviderPreview(group.providers[page]) }
}

/** The widget's preview image (or its icon) at a size matching its cells. */
@Composable
private fun ProviderPreview(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val widgets = remember { AppWidgets.get(context) }
    val span = remember(info) { widgets.defaultSpan(info) }
    val preview: ImageBitmap? by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            val density = resources.displayMetrics.densityDpi
            val drawable = info.loadPreviewImage(context, density) ?: info.loadIcon(context, density)
            drawable?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
        }
    }
    val image = preview ?: return
    Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(PreviewCell * span.columns, PreviewCell * span.rows)
            .clip(ContinuousRoundedShape(22.dp)),
    )
}

/** Width of one cell in the gallery preview. */
private val PreviewCell = 84.dp

@Composable
private fun AppIconTile(group: WidgetProviderGroup, size: Dp) {
    val bitmap = remember(group.packageName) { group.icon?.let { runCatching { it.toBitmap(128, 128).asImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, null, Modifier.size(size).clip(ContinuousRoundedShape(size * 0.225f)))
}
