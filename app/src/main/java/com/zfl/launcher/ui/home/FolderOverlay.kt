package com.zfl.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.zfl.launcher.OpenFolder
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.glass.FolderGlass
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.Tokens

/** An interruptible expansion from the folder tile into a glass panel, nine apps per page. */
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
    val appearance = LocalSettingsState.current.folders
    val panelShape = remember(appearance.radius) { ContinuousRoundedShape(appearance.radius.dp) }
    var last by remember { mutableStateOf<Pair<OpenFolder, FolderEntry>?>(null) }
    val current = if (open != null && folder != null) open to folder else null
    SideEffect { if (current != null) last = current }
    val progress = animateFloatAsState(
        targetValue = if (current != null) 1f else 0f,
        animationSpec = Motion.expand(),
        label = "folder",
    )
    val hidden by remember { derivedStateOf { progress.value == 0f } }
    LaunchedEffect(open, folder) { if (open != null && folder == null) onClose() }
    val retained = current ?: last
    if (retained == null || (hidden && current == null)) {
        SideEffect {
            last = null
            drag.folderPanel = null
            drag.folderGrid = null
            drag.openFolder = null
        }
        return
    }
    val (shown, entry) = retained
    val isOpen = current != null
    SideEffect {
        drag.openFolder = if (isOpen) entry else null
        if (!isOpen) {
            drag.folderPanel = null
            drag.folderGrid = null
        }
    }

    key(entry.id) {
        var name by remember(entry.name) { mutableStateOf(entry.name) }
        val focusManager = LocalFocusManager.current
        val commitName = {
            if (name.trim().isNotEmpty() && name.trim() != entry.name) onRename(entry.id, name)
        }
        val commitAndClose by rememberUpdatedState {
            focusManager.clearFocus()
            commitName()
            onClose()
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color.Black, alpha = appearance.dim * progress.value.coerceIn(0f, 1f))
                }
                .then(if (isOpen) Modifier.pointerInput(Unit) {
                    detectTapGestures { commitAndClose() }
                } else Modifier.clearAndSetSemantics { }),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(Tokens.FolderScreenMargin),
                contentAlignment = Alignment.Center,
            ) {
                var panelBounds by remember { mutableStateOf(Rect.Zero) }
                SideEffect {
                    if (isOpen && panelBounds.width > 0f && panelBounds.height > 0f) drag.folderPanel = panelBounds
                }
                Column(
                    Modifier.width(min(appearance.width.dp, maxWidth)).heightIn(max = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (appearance.showTitle || editMode) {
                        FolderTitle(
                            name = name,
                            editable = editMode && isOpen,
                            focusOnStart = shown.renaming,
                            onNameChange = { name = it },
                            onDone = { focusManager.clearFocus(); commitName() },
                            scale = appearance.titleScale,
                            modifier = Modifier.graphicsLayer {
                                val p = progress.value.coerceIn(0f, 1f)
                                alpha = folderDetailAlpha(p)
                                translationY = Tokens.FolderTitleGap.toPx() * (1f - p)
                            },
                        )
                        Spacer(Modifier.height(Tokens.FolderTitleGap))
                    }
                    // Measure outside the animated layer: its destination and drag bounds
                    // stay fixed while the inner surface grows out of the source tile.
                    Box(
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .height(appearance.height.dp)
                            .onGloballyPositioned {
                                panelBounds = it.boundsInWindow()
                            },
                    ) {
                        FolderGlass(appearance) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        val p = progress.value.coerceIn(0f, 1f)
                                        val icon = shown.iconBounds
                                        scaleX = lerp(icon.width / panelBounds.width.coerceAtLeast(1f), 1f, p)
                                        scaleY = lerp(icon.height / panelBounds.height.coerceAtLeast(1f), 1f, p)
                                        translationX = (icon.center.x - panelBounds.center.x) * (1f - p)
                                        translationY = (icon.center.y - panelBounds.center.y) * (1f - p)
                                        alpha = p
                                        shape = panelShape
                                        clip = true
                                    }
                                    .glass(panelShape)
                                    .semantics { paneTitle = entry.name }
                                    .pointerInput(Unit) { detectTapGestures { } },
                            ) {
                                FolderContents(entry, isOpen, editMode, panelBounds, progress, drag, iconCallbacks)
                            }
                        }
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
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val style = folderTitleStyle(scale)
    if (!editable) {
        Text(name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnStart) { if (focusOnStart) focus.requestFocus() }
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(LocalAccent.current.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .background(Tokens.GlassTint, ContinuousRoundedShape(Tokens.FolderTitleFieldRadius))
            .padding(vertical = Tokens.FolderTitleFieldPadding),
    )
}

internal fun folderTitleStyle(scale: Float): TextStyle = TextStyle(
    color = Tokens.TextPrimary,
    fontSize = Tokens.FolderTitleSize * scale,
    fontWeight = FontWeight.Normal,
    textAlign = TextAlign.Center,
)
