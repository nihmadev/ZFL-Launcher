package com.zfl.launcher.ui.library

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.zfl.launcher.ui.home.DragController
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/** The topmost library surface scrolls only while a held pointer rests near its edge. */
@Composable
internal fun LibraryDragScroll(drag: DragController?, state: LazyGridState, bounds: WindowBounds, owner: Any) {
    val density = LocalDensity.current
    val edge = with(density) { Tokens.LibraryDragScrollEdge.toPx() }
    val speed = with(density) { Tokens.LibraryDragScrollSpeed.toPx() }
    LaunchedEffect(drag, state, edge, speed) {
        if (drag == null) return@LaunchedEffect
        snapshotFlow { drag.session?.takeIf { it.active }?.pointer }.collectLatest { pointer ->
            if (pointer == null || drag.libraryTargets.keys.lastOrNull() !== owner) return@collectLatest
            val rect = bounds.current()
            if (!rect.contains(pointer)) return@collectLatest
            val direction = when {
                pointer.y < rect.top + edge -> -1f
                pointer.y > rect.bottom - edge -> 1f
                else -> return@collectLatest
            }
            var previous = withFrameNanos { it }
            while (isActive && drag.libraryTargets.keys.lastOrNull() === owner) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                if (state.scrollBy(direction * speed * seconds) == 0f) break
                drag.refreshTarget()
            }
        }
    }
}
