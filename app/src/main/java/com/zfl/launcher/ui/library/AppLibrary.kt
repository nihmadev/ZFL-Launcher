package com.zfl.launcher.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.data.LibraryView
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconBadge
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.ui.home.DragController

/** What App Library icons report back. Bounds are in window coordinates. */
interface LibraryCallbacks {
    fun onLaunch(app: AppInfo, bounds: Rect)
    fun onLongPress(app: AppInfo, bounds: Rect, pointer: Offset)
}

/**
 * The App Library, the last "page" of the Home Screen (as in iOS / iOS27L): a search pill,
 * a button that switches the view, and the apps as category cards, one grid, or an A–Z list.
 * Each view keeps its own scroll position while the launcher runs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryPage(
    library: AppLibraryState?,
    onSearch: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onViewChange: (LibraryView) -> Unit,
    callbacks: LibraryCallbacks,
    modifier: Modifier = Modifier,
    drag: DragController? = null,
) {
    if (library == null) {
        Box(modifier.fillMaxSize())
        return
    }
    // Hoisted, so switching views and back returns to where each one was.
    val cardsState = rememberLazyGridState(LibraryCacheWindow)
    val gridState = rememberLazyGridState(LibraryCacheWindow)
    val listState = rememberLazyListState(LibraryCacheWindow)
    val arranging = drag?.isDragging == true
    val view = library.view
    val header: @Composable () -> Unit = { LibraryHeader(view, onSearch, onViewChange) }
    when (view) {
        LibraryView.Categories -> CategoriesView(library, header, onOpenGroup, callbacks, cardsState, modifier, drag)
        LibraryView.Grid -> if (arranging || library.manualGrid) {
            LibraryPlacementGrid(library.gridApps, header, callbacks, gridState, drag, modifier)
        } else AllAppsGrid(library, header, callbacks, gridState, modifier)
        LibraryView.List -> AllAppsList(library, header, callbacks, listState, modifier)
    }
}

/** Search pill and the view button; scrolls with the content, like iOS. */
@Composable
private fun LibraryHeader(view: LibraryView, onSearch: () -> Unit, onViewChange: (LibraryView) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(HeaderHeight)
                .glass(CircleShape, profile = GlassProfile.Button)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onSearch),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_title), color = Tokens.TextPrimary, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        // One tap goes to the next view; it opens nothing and never shows the keyboard.
        val next = LibraryView.entries[(view.ordinal + 1) % LibraryView.entries.size]
        Box(
            Modifier
                .size(HeaderHeight)
                .glass(CircleShape, profile = GlassProfile.Button)
                .clickable(interactionSource = null, indication = null, role = Role.Button) { onViewChange(next) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                view.icon,
                contentDescription = stringResource(R.string.library_view_switch, stringResource(view.labelRes)),
                tint = Tokens.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val HeaderHeight = 46.dp

/**
 * Rows a screen ahead are composed in idle frame time, so a fast fling doesn't compose several
 * rows in one frame; half a screen behind stays composed for a fling back. One instance: the
 * states are remembered with it as a key.
 */
@OptIn(ExperimentalFoundationApi::class)
private val LibraryCacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)

private val LibraryView.icon
    get() = when (this) {
        LibraryView.Categories -> Icons.Rounded.Dashboard
        LibraryView.Grid -> Icons.Rounded.Apps
        LibraryView.List -> Icons.AutoMirrored.Rounded.List
    }

/** Name of a view, in the library and in settings. */
val LibraryView.labelRes: Int
    get() = when (this) {
        LibraryView.Categories -> R.string.library_view_categories
        LibraryView.Grid -> R.string.library_view_grid
        LibraryView.List -> R.string.library_view_list
    }

/**
 * Window bounds of an element, computed only when asked (a tap), not on every scroll frame as
 * `onGloballyPositioned` would.
 */
internal class WindowBounds {
    private var coordinates: LayoutCoordinates? = null

    /** Put on the element whose bounds [current] reports. */
    val modifier: Modifier = Modifier.onPlaced { coordinates = it }

    fun current(): Rect = coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
}

/** An App Library icon: tap opens the app, long-press shows its menu. */
@Composable
internal fun LibraryIcon(app: AppInfo, size: Dp, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val haptic = launcherHaptics()
    val bounds = remember { WindowBounds() }
    var pressed by remember { mutableStateOf(false) }
    val currentApp by rememberUpdatedState(app)
    AppIconImage(
        app = app,
        size = size,
        dimmed = pressed,
        badge = IconBadge.Shown,
        modifier = modifier
            .then(bounds.modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { callbacks.onLaunch(currentApp, bounds.current()) },
                    onLongPress = { position ->
                        haptic.performHapticFeedback(Haptics.Lift)
                        val iconBounds = bounds.current()
                        callbacks.onLongPress(currentApp, iconBounds, iconBounds.topLeft + position)
                    },
                )
            },
    )
}

/** Icon and name, as in a category opened full screen and in the Grid view. */
@Composable
internal fun LibraryCell(app: AppInfo, callbacks: LibraryCallbacks) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LibraryIcon(app, Tokens.IconSize, callbacks)
        Spacer(Modifier.height(6.dp))
        IconLabel(app.label)
    }
}
