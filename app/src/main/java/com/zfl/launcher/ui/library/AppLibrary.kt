package com.zfl.launcher.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.LibraryCategory
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens

/** What App Library icons report back. Bounds are in window coordinates. */
interface LibraryCallbacks {
    fun onLaunch(app: AppInfo, bounds: Rect)
    fun onLongPress(app: AppInfo, bounds: Rect)
}

private val CardShape = ContinuousRoundedShape(30.dp)
private val CardPadding = 14.dp
private val CardGap = 12.dp

/**
 * The App Library, the last "page" of the Home Screen (as in iOS / iOS27L): a search pill
 * and every app grouped into category cards. Each card shows three apps big and, when
 * there are more, a 2×2 cluster that opens the whole category.
 */
@Composable
fun AppLibraryPage(
    categories: List<LibraryCategory>,
    onSearch: () -> Unit,
    onOpenCategory: (String) -> Unit,
    callbacks: LibraryCallbacks,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(2) }) { LibrarySearchPill(onSearch) }
        items(categories, key = { it.id }) { category ->
            CategoryCard(category, onOpenCategory = { onOpenCategory(category.id) }, callbacks = callbacks)
        }
    }
}

@Composable
private fun LibrarySearchPill(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .glass(CircleShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.library_title), color = Tokens.TextPrimary, fontSize = 18.sp)
    }
}

@Composable
private fun CategoryCard(category: LibraryCategory, onOpenCategory: () -> Unit, callbacks: LibraryCallbacks) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .glass(CardShape, tint = Color.White.copy(alpha = 0.08f)),
        ) {
            val big = (maxWidth - CardPadding * 2 - CardGap) / 2
            val showCluster = category.apps.size > 4
            val bigApps = category.apps.take(if (showCluster) 3 else 4)
            bigApps.forEachIndexed { index, app ->
                LibraryIcon(
                    app = app,
                    size = big,
                    callbacks = callbacks,
                    modifier = Modifier.offset(
                        x = CardPadding + (big + CardGap) * (index % 2),
                        y = CardPadding + (big + CardGap) * (index / 2),
                    ),
                )
            }
            if (showCluster) {
                MiniCluster(
                    apps = category.apps.drop(3).take(4),
                    size = big,
                    onClick = onOpenCategory,
                    modifier = Modifier.offset(x = CardPadding + big + CardGap, y = CardPadding + big + CardGap),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            category.title,
            color = Tokens.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 2×2 tiny icons in the corner of a category card; tapping it opens the category. */
@Composable
private fun MiniCluster(apps: List<AppInfo>, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gap = size * 0.1f
    val mini = (size - gap) / 2
    Box(
        modifier
            .size(size)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        apps.forEachIndexed { index, app ->
            AppIconImage(
                app = app,
                size = mini,
                modifier = Modifier.offset(x = (mini + gap) * (index % 2), y = (mini + gap) * (index / 2)),
            )
        }
    }
}

/** An App Library icon: tap opens the app, long-press shows its menu. */
@Composable
private fun LibraryIcon(app: AppInfo, size: Dp, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val currentApp by rememberUpdatedState(app)
    AppIconImage(
        app = app,
        size = size,
        dimmed = pressed,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { callbacks.onLaunch(currentApp, bounds) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        callbacks.onLongPress(currentApp, bounds)
                    },
                )
            },
    )
}

/**
 * A category opened full screen: big title and all its apps in a 4-column grid, over the
 * blurred Home Screen. Tapping empty space or Back closes it.
 */
@Composable
fun CategoryOverlay(category: LibraryCategory?, callbacks: LibraryCallbacks, onClose: () -> Unit) {
    var last by remember { mutableStateOf(category) }
    if (category != null) last = category
    BackHandler(enabled = category != null, onBack = onClose)
    AnimatedVisibility(
        visible = category != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        val shown = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Text(
                    shown.title,
                    color = Tokens.TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 56.dp, bottom = 28.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Tokens.GridColumns),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown.apps, key = { it.key }) { app ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LibraryIcon(app, Tokens.IconSize, callbacks)
                            Spacer(Modifier.height(6.dp))
                            IconLabel(app.label)
                        }
                    }
                }
            }
        }
    }
}
