package com.zfl.launcher.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppGroup
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.GroupKind
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.home.DragController
import com.zfl.launcher.ui.home.DropMarker
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.delay

private val CardShape = ContinuousRoundedShape(30.dp)
private val CardPadding = 14.dp
private val CardGap = 12.dp

/** A category keeps its own geometry while the chosen insertion slot opens inside it. */
@Composable
internal fun CategoryCard(
    group: AppGroup,
    onOpenGroup: () -> Unit,
    callbacks: LibraryCallbacks,
    drag: DragController?,
    areas: MutableMap<String, (Offset) -> DropPosition.Library?>,
) {
    val bounds = remember { WindowBounds() }
    val currentGroup by rememberUpdatedState(group)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val picked = (drag?.session?.takeIf { it.active }?.entry as? AppEntry)?.app
    val selected = (drag?.target as? DropPosition.Library)?.takeIf { it.groupId == group.id }
    val rest = group.apps.filter { it.key != picked?.key }
    val gap = selected?.beforeKey?.let { key -> rest.indexOfFirst { it.key == key } }?.coerceAtLeast(0) ?: rest.size
    val preview = if (selected != null && picked != null) {
        rest.map<AppInfo, AppInfo?> { it }.toMutableList().apply { add(gap.coerceAtMost(size), null) }
    } else group.apps

    DisposableEffect(group.id, drag, rtl) {
        areas[group.id] = { point ->
            val rect = bounds.current()
            if (!rect.contains(point) || currentGroup.kind in listOf(GroupKind.Suggestions, GroupKind.Recent)) null
            else {
                val visualCol = if (point.x < rect.center.x) 0 else 1
                val col = if (rtl) 1 - visualCol else visualCol
                val row = if (point.y < rect.center.y) 0 else 1
                val remaining = currentGroup.apps.filter { it.key != drag?.draggedId }
                val index = (row * 2 + col).coerceAtMost(remaining.size)
                DropPosition.Library(remaining.getOrNull(index)?.key, currentGroup.id)
            }
        }
        onDispose { areas.remove(group.id) }
    }
    LaunchedEffect(selected, gap, preview.size) {
        if (selected != null && gap >= 3 && preview.size > 4) {
            delay(Tokens.LibraryGroupHoverMillis)
            if (drag.isDragging && drag.target == selected) onOpenGroup()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .then(bounds.modifier).onGloballyPositioned { drag?.refreshTarget() }
                .glass(CardShape, tint = Color.White.copy(alpha = 0.08f)),
        ) {
            val big = (maxWidth - CardPadding * 2 - CardGap) / 2
            val cluster = preview.size > 4
            preview.take(if (cluster) 3 else 4).forEachIndexed { index, app ->
                CardApp(app, big, callbacks, Modifier.offset(
                    x = CardPadding + (big + CardGap) * (index % 2),
                    y = CardPadding + (big + CardGap) * (index / 2),
                ))
            }
            if (cluster) {
                MiniCluster(preview.drop(3).take(4), big, onOpenGroup,
                    Modifier.offset(x = CardPadding + big + CardGap, y = CardPadding + big + CardGap))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(group.title, color = Tokens.TextPrimary, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpenGroup))
    }
}

@Composable
private fun CardApp(app: AppInfo?, size: Dp, callbacks: LibraryCallbacks, modifier: Modifier) {
    if (app == null) DropMarker(size, modifier.size(size))
    else LibraryIcon(app, size, callbacks, modifier)
}

/** A tiny insertion marker uses the same mini slot the saved app will occupy. */
@Composable
private fun MiniCluster(apps: List<AppInfo?>, size: Dp, onClick: () -> Unit, modifier: Modifier) {
    val gap = size * 0.1f
    val mini = (size - gap) / 2
    Box(modifier.size(size).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)) {
        apps.forEachIndexed { index, app ->
            val slot = Modifier.offset(x = (mini + gap) * (index % 2), y = (mini + gap) * (index / 2))
            if (app == null) DropMarker(mini, slot.size(mini))
            else AppIconImage(app, size = mini, modifier = slot)
        }
    }
}
