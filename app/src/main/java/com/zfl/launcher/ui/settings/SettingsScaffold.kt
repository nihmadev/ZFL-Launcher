package com.zfl.launcher.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.theme.Motion
import kotlin.math.roundToInt

/** Sizes of the page frame: the iOS navigation bar over a scrolling grouped list. */
private object PageStyle {
    val BarHeight = 56.dp
    /** How far the darkening under the bar reaches below it, so rows fade out under the bar. */
    val ScrollEdge = 28.dp
    val ScrollEdgeColor = Color.Black.copy(alpha = 0.82f)
    val SidePadding = 16.dp
    val LargeTitleSize = 34.sp
    val InlineTitleSize = 17.sp
    /** Scroll of the large title after which its small copy appears in the bar. */
    val CollapseOffset = 36.dp
    val NoWallpaperGradient = listOf(Color(0xFF1A2238), Color(0xFF0B0E18), Color.Black)
}

/**
 * Common frame of a settings screen, drawn over the blurred wallpaper like the launcher's
 * own overlays, with the rows grouped in glass cards.
 *
 * The main page ([onBack] == null) starts with a large title that slides away under the bar,
 * where a small copy of it fades in, like iOS. Sub pages get a glass back button and a small
 * centered title. Rows scrolling under the bar fade into it.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val largeTitle = onBack == null
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalSettingsSurface provides SettingsSurface.Glass) {
        Box(
            Modifier
                .fillMaxSize()
                .wallpaperBackground(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PageStyle.SidePadding,
                    end = PageStyle.SidePadding,
                    top = statusBar + PageStyle.BarHeight,
                    bottom = 32.dp + navBar,
                ),
            ) {
                if (largeTitle) item(key = LARGE_TITLE_KEY) { LargeTitle(title) }
                content()
            }
            NavigationBar(title, onBack, action, listState, collapsing = largeTitle)
        }
    }
}

private const val LARGE_TITLE_KEY = "settingsLargeTitle"

/** The blurred wallpaper stretched to the screen and darkened for contrast. */
@Composable
private fun Modifier.wallpaperBackground(): Modifier {
    val glass = LocalGlassSource.current
    return drawBehind {
        if (glass != null) {
            drawImage(
                image = glass.image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawRect(SettingsStyle.PageScrim)
        } else {
            drawRect(Brush.verticalGradient(PageStyle.NoWallpaperGradient))
        }
    }
}

@Composable
private fun LargeTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = PageStyle.LargeTitleSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun NavigationBar(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)?,
    listState: LazyListState,
    collapsing: Boolean,
) {
    val collapsePx = with(LocalDensity.current) { PageStyle.CollapseOffset.toPx() }
    // Both flip at a threshold only, so scrolling doesn't recompose the bar.
    val showTitle by remember(collapsing) {
        derivedStateOf {
            !collapsing || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapsePx
        }
    }
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, Motion.fade(), label = "barTitle")
    val edgeAlpha by animateFloatAsState(if (scrolled) 1f else 0f, Motion.fade(), label = "scrollEdge")
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (edgeAlpha == 0f) return@drawBehind
                // Solid under the status bar and the bar itself, then fading out below it.
                val color = PageStyle.ScrollEdgeColor.copy(alpha = PageStyle.ScrollEdgeColor.alpha * edgeAlpha)
                val fadeStart = size.height - PageStyle.ScrollEdge.toPx()
                drawRect(color, size = Size(size.width, fadeStart))
                drawRect(
                    Brush.verticalGradient(listOf(color, Color.Transparent), startY = fadeStart, endY = size.height),
                    topLeft = Offset(0f, fadeStart),
                    size = Size(size.width, size.height - fadeStart),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = PageStyle.ScrollEdge),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PageStyle.BarHeight)
                .padding(horizontal = PageStyle.SidePadding),
        ) {
            if (onBack != null) {
                GlassCircleButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = PageStyle.InlineTitleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .graphicsLayer { alpha = titleAlpha },
            )
            if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
        }
    }
}
