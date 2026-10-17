package com.zfl.launcher.ui.home

import com.zfl.launcher.ui.widgets.PageWidgetBody
import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.settings.IconArea
import com.zfl.launcher.R
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import androidx.compose.ui.unit.DpSize
import com.zfl.launcher.ui.widgets.titleRes
import com.zfl.launcher.ui.widgets.WidgetBody
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.HomeState
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens

/**
 * A miniature, non-interactive copy of the first home screen page: the user's wallpaper,
 * apps, labels and dock. It is laid out at the real screen size with the same components
 * and the same grid calculation as the home screen ([IconGrid], [gridFrame]), then scaled
 * down to fit, so it matches the result exactly.
 *
 * @param wallpaper sharp wallpaper image, or null to use a neutral gradient.
 * @param prefs settings to draw with: the saved ones, or a draft being edited.
 */
@Composable
fun HomePreview(
    state: HomeState,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    prefs: SettingsState = LocalSettingsState.current,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { window.width.toDp() }
    val screenHeight = with(density) { window.height.toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val homeMetrics = rememberAreaMetrics(prefs, IconArea.Home)
    val dockMetrics = rememberAreaMetrics(prefs, IconArea.Dock)

    BoxWithConstraints(modifier) {
        val scale = maxWidth / screenWidth
        Box(Modifier.size(maxWidth, screenHeight * scale)) {
            Box(
                Modifier
                    .requiredSize(screenWidth, screenHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                if (wallpaper != null) {
                    Image(wallpaper, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF26324D), Color(0xFF0D1018)))),
                    )
                }
                // Glass sampling uses window coordinates, which a scaled copy can't match: use the flat tint.
                CompositionLocalProvider(LocalGlassSource provides null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = statusBar, bottom = navigationBar),
                    ) {
                        IconGrid(
                            entries = state.pages.firstOrNull().orEmpty(),
                            grid = state.grid,
                            metrics = homeMetrics,
                            margins = prefs.gridMargins,
                            alignment = prefs.gridAlignment,
                            cells = state.cells,
                            modifier = Modifier.padding(bottom = prefs.bottomBarHeight(dockMetrics.labelBlock)),
                        ) { entry, slot -> StaticIcon(entry, slot) }
                        Column(Modifier.align(Alignment.BottomCenter)) {
                            if (prefs.showPagePill) {
                                SearchPill(
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = SettingsState.PILL_MARGIN),
                                )
                            }
                            if (prefs.showDock) {
                                DockBar(
                                    entries = state.dock,
                                    metrics = dockMetrics,
                                    slots = prefs.dockSlots,
                                    height = prefs.dockHeight(dockMetrics.labelBlock),
                                    background = prefs.dockBackground,
                                ) { entry, slot -> StaticIcon(entry, slot) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An app, folder or widget with its label, without any touch handling. */
@Composable
private fun StaticIcon(entry: HomeEntry, slot: IconSlot) {
    if (entry is PageWidgetEntry) {
        StaticWidget(entry, slot)
        return
    }
    Column(Modifier.width(slot.width), horizontalAlignment = Alignment.CenterHorizontally) {
        when (entry) {
            is AppEntry -> AppIconImage(entry.app, size = slot.iconSize)
            is FolderEntry -> FolderIcon(entry, size = slot.iconSize)
            is PlaceholderEntry, is PageWidgetEntry -> Unit
        }
        if (slot.showLabel) {
            Spacer(Modifier.height(LabelGap))
            IconLabel(
                when (entry) {
                    is AppEntry -> entry.app.label
                    is FolderEntry -> entry.name
                    is PlaceholderEntry, is PageWidgetEntry -> ""
                },
                scale = slot.labelScale,
            )
        }
    }
}

@Composable
private fun SearchPill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(Tokens.PillHeight)
            .glass(ContinuousRoundedShape(Tokens.PillHeight / 2))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = Tokens.TextPrimary, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.search), color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A widget card sized like on the real home screen (no name, as on the pages). */
@Composable
private fun StaticWidget(entry: PageWidgetEntry, slot: IconSlot) {
    BoxWithConstraints(contentAlignment = Alignment.TopCenter) {
        val body = widgetBodySize(DpSize(maxWidth, maxHeight), entry.span, entry.isSquare, slot.iconSize, withLabel = false)
        PageWidgetBody(entry, Modifier.size(body), live = false)
    }
}
