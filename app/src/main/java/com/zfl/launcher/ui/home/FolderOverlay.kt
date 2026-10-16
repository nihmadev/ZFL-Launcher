package com.zfl.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.zfl.launcher.OpenFolder
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.ui.theme.Motion

private val PanelWidth = 340.dp
private val PanelHeight = 356.dp
// Two full cells plus the last row's icon and label leave ~20dp of slack; split it evenly.
private val FolderGridTopPadding = 20.dp
private val PanelShape = ContinuousRoundedShape(40.dp)

/**
 * An open folder: a translucent panel that grows out of the folder icon, with the folder
 * name above it. Apps are shown nine per page. In edit mode the name is editable and apps
 * can be reordered; dragging one out of the panel closes the folder (see [DragController]).
 */
@Composable
fun FolderOverlay(
    open: OpenFolder?,
    folder: FolderEntry?,
    editMode: Boolean,
    drag: DragController,
    iconCallbacks: HomeIconCallbacks,
    onClose: () -> Unit,
    onRename: (id: String, name: String) -> Unit,
) {
    // Keep drawing the last folder while the close animation runs.
    var last by remember { mutableStateOf<Pair<OpenFolder, FolderEntry>?>(null) }
    if (open != null && folder != null) last = open to folder
    // Read only in draw/layer lambdas: the animation itself never recomposes the folder.
    val progress = animateFloatAsState(
        targetValue = if (open != null && folder != null) 1f else 0f,
        animationSpec = Motion.expand(),
        label = "folder",
    )
    val settled by remember { derivedStateOf { progress.value < 0.01f } }
    // The folder disappeared (e.g. its last apps were dragged out): close it.
    LaunchedEffect(open, folder) { if (open != null && folder == null) onClose() }

    val (shown, entry) = last ?: return
    if (settled && open == null) {
        SideEffect { drag.folderPanel = null; drag.folderGrid = null; drag.openFolder = null }
        return
    }
    val isOpen = open != null
    SideEffect { drag.openFolder = if (isOpen) entry else null }

    var name by remember(entry.id) { mutableStateOf(entry.name) }
    val focusManager = LocalFocusManager.current
    val commitAndClose = {
        focusManager.clearFocus()
        onRename(entry.id, name)
        onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.25f * progress.value) }
            .then(if (isOpen) Modifier.pointerInput(Unit) { detectTapGestures { commitAndClose() } } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        var panelBounds by remember { mutableStateOf(Rect.Zero) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FolderTitle(
                name = name,
                editable = editMode && isOpen,
                focusOnStart = shown.renaming,
                onNameChange = { name = it },
                onDone = { focusManager.clearFocus(); onRename(entry.id, name) },
                modifier = Modifier.graphicsLayer { alpha = progress.value },
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(PanelWidth, PanelHeight)
                    .onGloballyPositioned {
                        panelBounds = it.boundsInWindow()
                        if (isOpen) drag.folderPanel = panelBounds
                    }
                    .graphicsLayer {
                        // Grow out of the folder icon.
                        val icon = shown.iconBounds
                        val startScale = if (panelBounds.width > 0) icon.width / panelBounds.width else 0.2f
                        val p = progress.value
                        val scale = lerp(startScale, 1f, p)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerp(icon.center.x - panelBounds.center.x, 0f, p)
                        translationY = lerp(icon.center.y - panelBounds.center.y, 0f, p)
                    }
                    .background(Color.White.copy(alpha = 0.16f), PanelShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), PanelShape)
                    // Taps inside the panel must not close the folder.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // Rebuilt only when the folder or the drag target changes, so icons don't recompose.
                val apps = remember(entry, drag.target, drag.draggedId) { drag.previewFolder(entry) }
                val pages = apps.chunked(DragController.FOLDER_PAGE_SIZE).ifEmpty { listOf(emptyList()) }
                val pager = rememberPagerState { pages.size }
                SideEffect { drag.folderPage = pager.currentPage }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    IconGrid(
                        entries = pages.getOrElse(page) { emptyList() },
                        rows = 3,
                        columns = 3,
                        topPadding = FolderGridTopPadding,
                        onGeometry = if (page == pager.currentPage && isOpen) ({ drag.folderGrid = it }) else null,
                        marker = drag.folderMarker(entry)
                            ?.takeIf { it / DragController.FOLDER_PAGE_SIZE == page }
                            ?.let { it % DragController.FOLDER_PAGE_SIZE to Span.One },
                    ) { app ->
                        HomeIcon(
                            entry = app,
                            showLabel = true,
                            editMode = editMode,
                            jiggleSeed = apps.indexOf(app),
                            callbacks = iconCallbacks,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(
    name: String,
    editable: Boolean,
    focusOnStart: Boolean,
    onNameChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(color = Tokens.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    if (!editable) {
        Text(name, style = style, maxLines = 1, modifier = modifier.padding(horizontal = 24.dp))
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnStart) { if (focusOnStart) focus.requestFocus() }
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Tokens.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .focusRequester(focus)
            .background(Color.White.copy(alpha = 0.12f), ContinuousRoundedShape(14.dp))
            .padding(vertical = 4.dp),
    )
}
