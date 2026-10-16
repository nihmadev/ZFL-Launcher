package com.zfl.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.data.canStackOnto
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.ui.theme.Motion

/*
 * Drop-onto rules shared by the drag controller: an app on an app or folder makes or fills
 * a folder; a widget on a widget or stack of the same size makes or fills a Smart Stack.
 */

/** Whether dropping [dragged] on [under] merges them (folder or stack) instead of moving. */
internal fun canMergeOnto(dragged: HomeEntry, under: HomeEntry): Boolean = when (dragged) {
    is AppEntry -> under.id != dragged.id && under !is PlaceholderEntry && under !is PageWidgetEntry
    is PageWidgetEntry -> canStackOnto(dragged, under)
    else -> false
}

/** Adding to an existing folder or stack is unambiguous, so it arms at once; a new one needs a short hover. */
internal fun mergesAtOnce(target: HomeEntry?): Boolean = target is FolderEntry || target is WidgetStackEntry

/**
 * The point compared with a merge target and the target's center: icons use the dragged
 * icon's center and the target icon; widgets use the dragged card's center and the center
 * of the target's footprint.
 */
internal fun mergeDistance(session: DragSession, grid: GridGeometry, under: HomeEntry, cell: Int): Pair<Float, Float> =
    if (session.entry is PageWidgetEntry) {
        val span = (under as? PageWidgetEntry)?.span ?: Span.One
        val center = Offset(
            grid.origin.x + (cell % grid.columns + span.columns / 2f) * grid.cellWidth,
            grid.origin.y + (cell / grid.columns + span.rows / 2f) * grid.cellHeight,
        )
        val card = session.pointer - session.touchOffset + Offset(session.itemSize.width / 2, session.itemSize.height / 2)
        (card - center).getDistance() to minOf(span.columns * grid.cellWidth, span.rows * grid.cellHeight)
    } else {
        (session.hotspot - grid.iconCenter(cell)).getDistance() to grid.iconSize
    }

/** A widget that will become (or join) a stack grows a little and gets a light edge, like a folder target. */
internal fun Modifier.stackMergeHighlight(active: Boolean): Modifier = composed {
    val progress = animateFloatAsState(if (active) 1f else 0f, Motion.bouncy(), label = "stackTarget")
    graphicsLayer {
        val scale = 1f + 0.05f * progress.value
        scaleX = scale
        scaleY = scale
    }.drawWithContent {
        drawContent()
        val p = progress.value
        if (p <= 0f) return@drawWithContent
        val outline = ContinuousRoundedShape(22.dp).createOutline(size, layoutDirection, this)
        drawOutline(outline, Tokens.GlassStroke, alpha = p, style = Stroke(2.dp.toPx()))
    }
}
