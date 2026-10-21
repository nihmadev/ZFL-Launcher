package com.zfl.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.settings.GridMargins
import com.zfl.launcher.settings.GridAlignment
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.FolderGlass
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.home.FolderPageIndicator
import com.zfl.launcher.ui.home.IconGrid
import com.zfl.launcher.ui.home.StaticIcon
import com.zfl.launcher.ui.home.folderTitleStyle
import com.zfl.launcher.ui.home.rememberAreaMetrics
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens

/** Real app images and the real folder grid, scaled down without changing icon raster sizes. */
@Composable
internal fun FolderPreview(apps: List<AppInfo>, settings: SettingsState): Unit {
    val appearance = settings.folders
    val metrics = rememberAreaMetrics(settings, IconArea.Folder)
    val title = stringResource(R.string.folder_default_name)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleHeight = with(density) {
        measurer.measure(title, folderTitleStyle(appearance.titleScale), maxLines = 1).size.height.toDp()
    }
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val panelWidth = minOf(appearance.width.dp, (screenWidth - Tokens.FolderScreenMargin * 2).coerceAtLeast(0.dp))
    val totalHeight = appearance.height.dp + if (appearance.showTitle) titleHeight + Tokens.FolderTitleGap else 0.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(Tokens.FolderSettingsPreviewHeight)
            .clip(SettingsStyle.CardShape)
            .background(Color.Black.copy(alpha = appearance.dim)),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(Tokens.FolderSettingsPreviewScale, maxWidth / panelWidth, maxHeight / totalHeight)
        val shape = ContinuousRoundedShape(appearance.radius.dp * scale)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (appearance.showTitle) {
                Text(
                    title,
                    style = folderTitleStyle(appearance.titleScale * scale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Tokens.FolderTitleGap * scale))
            }
            FolderGlass(appearance) {
                Box(Modifier.size(panelWidth * scale, appearance.height.dp * scale).clip(shape).glass(shape)) {
                    CompositionLocalProvider(LocalSettingsState provides settings) {
                        Column(
                            Modifier.requiredSize(panelWidth, appearance.height.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale },
                        ) {
                            Box(Modifier.weight(1f)) {
                                IconGrid(
                                    entries = apps.take(GridSpec.Folder.cellCount).map { app ->
                                        AppEntry(app.withLabel(settings.customLabels[app.key] ?: app.label))
                                    },
                                    grid = GridSpec.Folder,
                                    metrics = metrics,
                                    margins = GridMargins(Tokens.GridSidePadding, Tokens.FolderGridTop, 0.dp),
                                    alignment = GridAlignment.Center,
                                ) { app, slot -> StaticIcon(app, slot) }
                            }
                            val pages = (apps.size + GridSpec.Folder.cellCount - 1) / GridSpec.Folder.cellCount
                            if (appearance.showPages && pages > 1) FolderPageIndicator(pages, position = { 0f })
                        }
                    }
                }
            }
        }
    }
}
