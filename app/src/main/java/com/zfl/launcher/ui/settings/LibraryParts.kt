package com.zfl.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.LocalAccent
import kotlin.math.roundToInt

/** A one-line text field as a settings row: a name, or a search query. */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    focusOnStart: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (focusOnStart) LaunchedEffect(Unit) { focus.requestFocus() }
    SettingsRow(onClick = null, modifier = modifier) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = SettingsStyle.SecondaryText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) RowTitle(placeholder, color = SettingsStyle.SecondaryText)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = SettingsStyle.TitleSize),
                cursorBrush = SolidColor(LocalAccent.current.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** Trailing mark of an app that can be added to a list (plus) or is in it (check). */
@Composable
internal fun PickMark(selected: Boolean) {
    Icon(
        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircle,
        contentDescription = null,
        tint = if (selected) LocalAccent.current.primary else SettingsStyle.Chevron,
        modifier = Modifier.size(24.dp),
    )
}

/** Minus button that takes an item out of a list (an unpin), like iOS edit lists. */
@Composable
internal fun RemoveButton(contentDescription: String, onClick: () -> Unit) {
    Icon(
        Icons.Rounded.RemoveCircle,
        contentDescription = contentDescription,
        tint = SettingsStyle.Destructive,
        modifier = Modifier
            .size(36.dp)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(6.dp),
    )
}

/**
 * Rows the user can reorder inside a [SettingsCard]: drag a row by its handle, or use the
 * "Move Up" / "Move Down" accessibility actions. [onMove] gets the indices in [items] and is
 * called once, when the row is dropped. Until [items] arrive in the new order, the rows stay
 * where they were dropped.
 */
@Composable
internal fun <T> ReorderRows(
    items: List<T>,
    key: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, handle: @Composable () -> Unit) -> Unit,
) {
    var pending by remember { mutableStateOf<List<String>?>(null) }
    val keys = items.map(key)
    LaunchedEffect(keys) { pending = null }
    val byKey = items.associateBy(key)
    val shown = pending?.mapNotNull(byKey::get)?.takeIf { it.size == items.size } ?: items
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0) }
    val currentOnMove by rememberUpdatedState(onMove)
    val moveUp = stringResource(R.string.library_move_up)
    val moveDown = stringResource(R.string.library_move_down)
    val reorder = stringResource(R.string.library_reorder)

    // Moves start only while the rows show [items] as they are, so indices are indices in [items].
    val currentKeys by rememberUpdatedState(keys)
    val idle by rememberUpdatedState(pending == null)
    fun commit(from: Int, to: Int) {
        val order = currentKeys
        if (from == to || from !in order.indices || to !in order.indices) return
        pending = order.toMutableList().apply { add(to, removeAt(from)) }
        currentOnMove(from, to)
    }

    val draggedIndex = shown.indexOfFirst { key(it) == dragging }
    val target = if (draggedIndex < 0 || rowHeight == 0) -1 else
        (draggedIndex + (offset / rowHeight).roundToInt()).coerceIn(0, shown.lastIndex)
    shown.forEachIndexed { index, item ->
        val itemKey = key(item)
        val isDragged = itemKey == dragging
        // Rows between the dragged one and its target make room for it.
        val shift = when {
            draggedIndex < 0 || isDragged -> 0
            index in (draggedIndex + 1)..target -> -1
            index in target until draggedIndex -> 1
            else -> 0
        }
        Box(
            Modifier
                .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                .zIndex(if (isDragged) 1f else 0f)
                .graphicsLayer { translationY = if (isDragged) offset else shift * rowHeight.toFloat() }
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(moveUp) { idle.also { if (it) commit(index, index - 1) } }.takeIf { index > 0 },
                        CustomAccessibilityAction(moveDown) { idle.also { if (it) commit(index, index + 1) } }.takeIf { index < shown.lastIndex },
                    )
                },
        ) {
            row(item) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = reorder,
                    tint = SettingsStyle.Chevron,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .size(36.dp)
                        .padding(6.dp)
                        .pointerInput(itemKey) {
                            detectDragGestures(
                                onDragStart = { if (idle) dragging = itemKey; offset = 0f },
                                onDragEnd = {
                                    val from = currentKeys.indexOf(itemKey)
                                    val to = if (rowHeight == 0) from else (from + (offset / rowHeight).roundToInt()).coerceIn(0, currentKeys.lastIndex)
                                    val moved = dragging == itemKey
                                    dragging = null
                                    offset = 0f
                                    if (moved) commit(from, to)
                                },
                                onDragCancel = { dragging = null; offset = 0f },
                            ) { change, amount ->
                                change.consume()
                                if (dragging == itemKey) offset += amount.y
                            }
                        },
                )
            }
        }
    }
}

/** A row of [ReorderRows]: the content, then the drag handle at the end. */
@Composable
internal fun ReorderRow(handle: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = SettingsStyle.RowPadding, end = 6.dp).heightIn(min = SettingsStyle.RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { content() }
        handle()
    }
}
