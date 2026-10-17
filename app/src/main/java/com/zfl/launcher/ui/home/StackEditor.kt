package com.zfl.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.R
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetStackEntry
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.ui.widgets.PageWidgetBody
import com.zfl.launcher.ui.widgets.designSize
import com.zfl.launcher.ui.widgets.variantTitleRes
import com.zfl.launcher.ui.theme.Motion

private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
private val RowHeight = 92.dp
private val PreviewHeight = 72.dp

/** What the "Edit Stack" sheet changes; each call is one saved layout change. */
interface StackEditorActions {
    fun move(stackId: String, from: Int, to: Int)
    fun remove(stackId: String, itemId: String)
    fun takeOut(stackId: String, itemId: String)
    fun close()
}

/**
 * "Edit Stack": a glass sheet listing the stack's widgets with previews. Drag the handle to
 * reorder, take a widget out onto the Home Screen, or remove it.
 */
@Composable
internal fun StackEditor(stack: WidgetStackEntry?, actions: StackEditorActions) {
    // Keep the last stack while the sheet slides away.
    var shown by remember { mutableStateOf(stack) }
    if (stack != null) shown = stack
    BackHandler(enabled = stack != null) { actions.close() }
    AnimatedVisibility(stack != null, enter = fadeIn(Motion.fade()), exit = fadeOut(Motion.fade())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Tokens.Scrim)
                .pointerInput(Unit) { detectTapGestures { actions.close() } },
        )
    }
    AnimatedVisibility(
        stack != null,
        enter = slideInVertically(Motion.sheet()) { it },
        exit = slideOutVertically(Motion.sheet()) { it },
    ) {
        val current = shown ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .glass(SheetShape, tint = Tokens.SheetTint)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stack_edit), color = Tokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.action_done),
                        color = LocalAccent.current.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = actions::close).padding(8.dp),
                    )
                }
                StackRows(current, actions)
            }
        }
    }
}

@Composable
private fun StackRows(stack: WidgetStackEntry, actions: StackEditorActions) {
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    // Local order while a row is dragged; saved once, on release.
    var order by remember(stack.items) { mutableStateOf(stack.items) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val currentStack by rememberUpdatedState(stack)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        order.forEach { item ->
            key(item.id) {
                val dragged = item.id == draggedId
                StackRow(
                    item = item,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = if (dragged) dragOffset.floatValue else 0f
                            shadowElevation = if (dragged) 12f else 0f
                        },
                    handle = Modifier.pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = { draggedId = item.id; dragOffset.floatValue = 0f },
                            onDragEnd = {
                                val from = currentStack.items.indexOfFirst { it.id == item.id }
                                val to = order.indexOfFirst { it.id == item.id }
                                draggedId = null
                                dragOffset.floatValue = 0f
                                if (from >= 0 && from != to) actions.move(currentStack.id, from, to)
                            },
                            onDragCancel = { draggedId = null; dragOffset.floatValue = 0f; order = currentStack.items },
                        ) { change, amount ->
                            change.consume()
                            dragOffset.floatValue += amount.y
                            // Past half a row, the neighbor swaps places with the dragged row.
                            val index = order.indexOfFirst { it.id == item.id }
                            val step = when {
                                dragOffset.floatValue > rowPx / 2 && index < order.lastIndex -> 1
                                dragOffset.floatValue < -rowPx / 2 && index > 0 -> -1
                                else -> 0
                            }
                            if (step != 0) {
                                order = order.toMutableList().apply { add(index + step, removeAt(index)) }
                                dragOffset.floatValue -= step * rowPx
                            }
                        }
                    },
                    onTakeOut = { actions.takeOut(stack.id, item.id) },
                    onRemove = { actions.remove(stack.id, item.id) },
                )
            }
        }
    }
}

@Composable
private fun StackRow(item: PageWidgetEntry, modifier: Modifier, handle: Modifier, onTakeOut: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val removeLabel = stringResource(R.string.stack_remove_widget)
        RemoveBadge(onClick = onRemove, modifier = Modifier.semantics { contentDescription = removeLabel })
        Spacer(Modifier.width(12.dp))
        val design = (item as? WidgetEntry)?.size?.designSize
        val aspect = design?.let { it.width / it.height } ?: (item.span.columns.toFloat() / item.span.rows)
        PageWidgetBody(item, Modifier.size(PreviewHeight * aspect.coerceAtMost(2.2f), PreviewHeight), live = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(widgetLabel(item), color = Tokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item is WidgetEntry) {
                Text(stringResource(variantTitleRes(item.kind, item.size)), color = Tokens.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(36.dp).clickable(onClick = onTakeOut),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Launch, stringResource(R.string.stack_take_out), tint = Tokens.TextSecondary, modifier = Modifier.size(20.dp)) }
        Box(
            handle.size(40.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.DragHandle, stringResource(R.string.stack_reorder), tint = Tokens.TextSecondary, modifier = Modifier.size(22.dp)) }
    }
}
