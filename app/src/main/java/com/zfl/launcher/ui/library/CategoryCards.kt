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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.data.AppGroup
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.ui.home.DragController
import androidx.compose.ui.geometry.Offset
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.GroupKind
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

/**
 * The Categories view: pinned apps, the smart groups and every category as a card. Each card
 * shows three apps big and, when there are more, a 2×2 cluster that opens the whole group.
 * Empty groups have no card.
 */
@Composable
internal fun CategoriesView(
    library: AppLibraryState,
    header: @Composable () -> Unit,
    onOpenGroup: (String) -> Unit,
    callbacks: LibraryCallbacks,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    drag: DragController? = null,
) {
    val areas = remember { linkedMapOf<String, (Offset) -> DropPosition.Library?>() }
    val owner = remember { Any() }
    val bounds = remember { WindowBounds() }
    DisposableEffect(drag) {
        if (drag != null) drag.libraryTargets[owner] = { point -> areas.values.firstNotNullOfOrNull { it(point) } }
        onDispose { drag?.libraryTargets?.remove(owner) }
    }
    LaunchedEffect(drag, state) { snapshotFlow { state.layoutInfo }.collect { drag?.refreshTarget() } }
    LibraryDragScroll(drag, state, bounds, owner)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = modifier.fillMaxSize().then(bounds.modifier).onGloballyPositioned { drag?.refreshTarget() },
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header", span = { GridItemSpan(2) }) { header() }
        // Group ids never collide with each other: system, user and smart ids have their own forms.
        items(library.cards, key = { "group:${it.id}" }) { group ->
            CategoryCard(group, onOpenGroup = { onOpenGroup(group.id) }, callbacks = callbacks, drag = drag, areas = areas)
        }
    }
}

/**
 * A group opened full screen: big title and all its apps in a 4-column grid, over the
 * blurred Home Screen. Tapping empty space or Back closes it. A renamed group updates in
 * place; the caller closes it when the group is gone.
 */
@Composable
fun CategoryOverlay(group: AppGroup?, callbacks: LibraryCallbacks, onClose: () -> Unit, drag: DragController? = null) {
    var last by remember { mutableStateOf(group) }
    if (group != null) last = group
    BackHandler(enabled = group != null) { drag?.cancel(); onClose() }
    AnimatedVisibility(
        visible = group != null,
        enter = fadeIn(Motion.fade()) + scaleIn(Motion.expand(), initialScale = 0.92f),
        exit = fadeOut(Motion.fade()) + scaleOut(Motion.expand(), targetScale = 0.92f),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 28.dp),
                )
                if (drag?.isDragging == true && shown.kind !in listOf(GroupKind.Suggestions, GroupKind.Recent)) {
                    LibraryPlacementGrid(shown.apps, null, callbacks, rememberLazyGridState(), drag, groupId = shown.id)
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(Tokens.LibraryColumns),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown.apps, key = { it.key }) { app -> LibraryCell(app, callbacks) }
                }
            }
        }
    }
}
