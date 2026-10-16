package com.zfl.launcher.ui.home

import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.theme.Haptics

/** What a [HomeIcon] reports back. Bounds and pointers are in window coordinates. */
interface HomeIconCallbacks {
    fun onTap(entry: HomeEntry, iconBounds: Rect)
    /** Normal mode long-press: show the menu (a drag may follow if the finger moves). */
    fun onLongPress(entry: HomeEntry, iconBounds: Rect, pointer: Offset)
    /** Edit mode: the finger moved past the touch slop on this icon. */
    fun onDragStart(entry: HomeEntry, iconBounds: Rect, pointer: Offset)
    fun onRemove(entry: AppEntry)
}

/**
 * One app or folder on the home screen, in the dock or in an open folder.
 *
 * Normal mode: tap opens, long-press shows the menu. Edit mode: icons jiggle, apps get a
 * "−" badge, dragging starts as soon as the finger moves, taps open folders.
 *
 * @param jiggleSeed varies the jiggle phase per icon (its position works well).
 * @param hidden keeps the slot but draws nothing (the icon is lifted by the menu).
 * @param mergeTarget an app is hovering over this one and will make a folder.
 */
@Composable
fun HomeIcon(
    entry: HomeEntry,
    showLabel: Boolean,
    editMode: Boolean,
    jiggleSeed: Int,
    callbacks: HomeIconCallbacks,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    mergeTarget: Boolean = false,
) {
    if (entry is PlaceholderEntry || entry is PageWidgetEntry) return
    val settings = LocalSettingsState.current
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var columnOrigin by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val currentEntry by rememberUpdatedState(entry)
    val currentCallbacks by rememberUpdatedState(callbacks)

    // Keyed on the mode so the gesture handler restarts when edit mode toggles.
    val gestures = if (editMode) {
        Modifier.pointerInput(true) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        currentCallbacks.onTap(currentEntry, bounds)
                        break
                    }
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                        change.consume()
                        haptic.performHapticFeedback(Haptics.Lift)
                        currentCallbacks.onDragStart(currentEntry, bounds, columnOrigin + change.position)
                        break
                    }
                }
            }
        }
    } else {
        Modifier.pointerInput(false) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { currentCallbacks.onTap(currentEntry, bounds) },
                onLongPress = { position ->
                    haptic.performHapticFeedback(Haptics.Lift)
                    currentCallbacks.onLongPress(currentEntry, bounds, columnOrigin + position)
                },
            )
        }
    }

    Column(
        modifier
            .width(settings.iconSize + 16.dp)
            .onGloballyPositioned { columnOrigin = it.boundsInWindow().topLeft }
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .jiggle(editMode, jiggleSeed)
            .then(gestures),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MergeHalo(visible = mergeTarget && entry is AppEntry)
            val iconModifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
            when (entry) {
                is AppEntry -> AppIconImage(entry.app, iconModifier, dimmed = pressed)
                is FolderEntry -> FolderIcon(entry, iconModifier, highlighted = mergeTarget)
                is PlaceholderEntry, is PageWidgetEntry -> Unit
            }
            if (editMode && entry is AppEntry) {
                RemoveBadge(
                    onClick = { callbacks.onRemove(entry) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-8).dp),
                )
            }
        }
        if (showLabel && settings.showLabels) {
            LabelSpacer()
            IconLabel(
                when (entry) {
                    is AppEntry -> entry.app.label
                    is FolderEntry -> entry.name
                    is PlaceholderEntry, is PageWidgetEntry -> ""
                },
            )
        }
    }
}
