package com.zfl.launcher.ui.menu

import android.content.pm.ShortcutInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.MenuTarget
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.ui.widgets.PageWidgetBody
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.widgets.WidgetSizeGlyph
import androidx.compose.material.icons.outlined.Image
import androidx.compose.foundation.layout.Arrangement
import com.zfl.launcher.ui.widgets.WidgetBody
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.AppRepository
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.MenuAction
import com.zfl.launcher.ui.home.LauncherActions
import com.zfl.launcher.ui.home.FolderIcon
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.LocalIconLoader
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/** What is drawn on the left side of a menu row. */
private sealed interface MenuIcon {
    data class Vector(val image: ImageVector) : MenuIcon
    data class Shortcut(val shortcut: ShortcutInfo) : MenuIcon
    data object MinusCircle : MenuIcon
}

/** One row of the menu. */
private data class MenuItem(
    val label: String,
    val icon: MenuIcon,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * iOS-style long-press menu.
 *
 * Dims the screen, "lifts" a sharp copy of the pressed icon above the blurred home screen
 * and shows a menu card next to it. The card opens below the icon, or above it when there
 * is no room (dock icons). Items are reversed when the card is above, so the app shortcuts
 * always stay closest to the icon, like in the reference screenshots.
 */
@Composable
fun ContextMenuOverlay(
    target: MenuTarget?,
    dockIsFull: Boolean,
    repository: AppRepository,
    onDismiss: () -> Unit,
    onToggleDock: (HomeEntry) -> Unit,
    onHide: (AppInfo) -> Unit,
    onEditHome: () -> Unit,
    onRenameFolder: (FolderEntry, Rect) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    widgetActions: WidgetMenuActions,
    actions: LauncherActions,
) {
    // Keep showing the last target while the close animation runs.
    val lastTarget = remember { mutableStateOf<MenuTarget?>(null) }
    SideEffect { if (target != null) lastTarget.value = target }
    // Read only in draw/layer lambdas: the animation itself never recomposes the menu.
    val progress = animateFloatAsState(
        targetValue = if (target != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "menu",
    )
    val settled by remember { derivedStateOf { progress.value < 0.01f } }
    val current = target ?: lastTarget.value ?: return
    if (target == null && settled) return

    val settings = LocalSettingsState.current
    val widget = current.entry as? PageWidgetEntry
    val entries = if (widget != null) {
        widgetEntries(widget, settings.hiddenMenuActions, onEditHome, widgetActions)
    } else {
        buildEntries(current, settings.hiddenMenuActions, dockIsFull, onDismiss, onToggleDock, onHide, onEditHome, onRenameFolder, onAddToHome, actions)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = settings.menuDim * progress.value) }
            .then(
                if (target != null) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
                else Modifier
            ),
    ) {
        val density = LocalDensity.current
        val bounds: Rect = current.iconBounds
        val screenWidthPx = constraints.maxWidth
        val screenHeightPx = constraints.maxHeight
        val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)

        // Decide the side before measuring, because the item order depends on it.
        val estimatedHeightPx = with(density) {
            entries.size * Tokens.MenuItemHeight.roundToPx() + 2 * Tokens.MenuVerticalPadding.roundToPx() +
                if (widget != null) SizeRowHeight.roundToPx() else 0
        }
        val gapPx = with(density) { 12.dp.roundToPx() }
        val above = bounds.bottom + gapPx + estimatedHeightPx > screenHeightPx - bottomInsetPx
        val alignRight = bounds.center.x > screenWidthPx / 2f

        // Lifted copy of the icon.
        val liftedModifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .graphicsLayer {
                val scale = 1f + 0.05f * progress.value
                scaleX = scale
                scaleY = scale
            }
        val liftedSize = with(density) { bounds.width.toDp() }
        when (val entry = current.entry) {
            is AppEntry -> AppIconImage(app = entry.app, size = liftedSize, modifier = liftedModifier)
            is FolderEntry -> FolderIcon(entry, liftedModifier, size = liftedSize)
            is PageWidgetEntry -> PageWidgetBody(entry, liftedModifier.size(liftedSize, with(density) { bounds.height.toDp() }), live = false)
            is PlaceholderEntry -> Unit
        }

        Layout(
            content = {
                MenuCard(
                    entries = if (above) entries.asReversed() else entries,
                    repository = repository,
                    header = widget?.let { w -> widgetActions.sizes(w).takeIf { it.size > 1 } }?.let { sizes ->
                        { WidgetSizeRow(widget, sizes, onResize = { span -> widgetActions.resize(widget.id, span) }) }
                    },
                    modifier = Modifier.graphicsLayer {
                        val p = progress.value
                        val scale = 0.5f + 0.5f * p
                        scaleX = scale
                        scaleY = scale
                        alpha = p.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(if (alignRight) 1f else 0f, if (above) 1f else 0f)
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            // All items turned off in settings: no card, only the lifted icon (it can still be dragged).
            val card = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
                ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
            val margin = 16.dp.roundToPx()
            val x = (if (alignRight) bounds.right - card.width else bounds.left).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin))
            val y = (if (above) bounds.top - gapPx - card.height else bounds.bottom + gapPx).roundToInt()
                .coerceIn(margin, (constraints.maxHeight - card.height - margin).coerceAtLeast(margin))
            layout(constraints.maxWidth, constraints.maxHeight) { card.place(x, y) }
        }
    }
}

@Composable
private fun buildEntries(
    target: MenuTarget,
    hidden: Set<MenuAction>,
    dockIsFull: Boolean,
    onDismiss: () -> Unit,
    onToggleDock: (HomeEntry) -> Unit,
    onHide: (AppInfo) -> Unit,
    onEditHome: () -> Unit,
    onRenameFolder: (FolderEntry, Rect) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    actions: LauncherActions,
): List<MenuItem> {
    val entry = target.entry
    val folder = entry as? FolderEntry
    val app = (entry as? AppEntry)?.app
    if (folder == null && app == null) return emptyList()
    return menuActionsFor(isFolder = folder != null, onHome = target.onHome, hidden = hidden).flatMap { action ->
        when (action) {
            MenuAction.Shortcuts -> target.shortcuts.map { shortcut ->
                MenuItem(
                    label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
                    icon = MenuIcon.Shortcut(shortcut),
                    onClick = { onDismiss(); actions.launchShortcut(shortcut, target.iconBounds) },
                )
            }
            MenuAction.RenameFolder -> listOf(
                MenuItem(stringResource(R.string.menu_rename_folder), MenuIcon.Vector(Icons.Outlined.Edit)) {
                    onRenameFolder(folder!!, target.iconBounds)
                },
            )
            MenuAction.AddToHome -> listOf(
                MenuItem(stringResource(R.string.menu_add_to_home), MenuIcon.Vector(Icons.Outlined.AddBox)) { onAddToHome(app!!) },
            )
            MenuAction.AppInfo -> listOf(
                MenuItem(stringResource(R.string.menu_app_info), MenuIcon.Vector(Icons.Outlined.Info)) {
                    onDismiss(); actions.openAppInfo(app!!)
                },
            )
            MenuAction.Dock -> listOf(
                when {
                    target.inDock -> MenuItem(stringResource(R.string.menu_remove_from_dock), MenuIcon.Vector(Icons.Outlined.Close)) {
                        onToggleDock(entry)
                    }
                    dockIsFull -> MenuItem(stringResource(R.string.menu_dock_full), MenuIcon.Vector(Icons.Outlined.Star), enabled = false) {}
                    else -> MenuItem(stringResource(R.string.menu_add_to_dock), MenuIcon.Vector(Icons.Outlined.Star)) {
                        onToggleDock(entry)
                    }
                },
            )
            MenuAction.HideApp -> listOf(
                MenuItem(stringResource(R.string.menu_hide_app), MenuIcon.Vector(Icons.Outlined.VisibilityOff)) { onHide(app!!) },
            )
            MenuAction.EditHome -> listOf(
                MenuItem(stringResource(R.string.menu_edit_home), MenuIcon.Vector(Icons.Outlined.Apps)) { onEditHome() },
            )
            MenuAction.RemoveApp -> listOf(
                MenuItem(stringResource(R.string.menu_remove_app), MenuIcon.MinusCircle, destructive = true) {
                    onDismiss(); actions.uninstall(app!!)
                },
            )
        }
    }
}

/** Solid list with no dividers, a soft edge highlight and large continuous corners, like iOS27L. */
@Composable
private fun MenuCard(
    entries: List<MenuItem>,
    repository: AppRepository,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val shape = ContinuousRoundedShape(Tokens.MenuRadius)
    // Every item can be turned off in settings: then only the lifted icon is shown.
    if (entries.isEmpty() && header == null) return
    Column(
        modifier
            .width(Tokens.MenuWidth)
            .clip(shape)
            .background(Tokens.MenuBackground)
            .border(1.dp, Tokens.MenuStroke, shape)
            .padding(vertical = Tokens.MenuVerticalPadding),
    ) {
        header?.invoke()
        entries.forEach { MenuRow(it, repository) }
    }
}

/** Height of the widget size buttons row, with its padding. */
private val SizeRowHeight = 50.dp

/** What the widget menu can do. */
interface WidgetMenuActions {
    fun resize(id: String, span: Span)
    /** Sizes the widget can take, in cells. */
    fun sizes(widget: PageWidgetEntry): List<Span>
    fun remove(id: String)
    fun pickPhoto(id: String)
}

@Composable
private fun widgetEntries(widget: PageWidgetEntry, hidden: Set<MenuAction>, onEditHome: () -> Unit, actions: WidgetMenuActions): List<MenuItem> =
    listOfNotNull(
        MenuItem(stringResource(R.string.widget_choose_photo), MenuIcon.Vector(Icons.Outlined.Image)) {
            actions.pickPhoto(widget.id)
        }.takeIf { widget is WidgetEntry && widget.kind == WidgetKind.Photo },
        MenuItem(stringResource(R.string.menu_edit_home), MenuIcon.Vector(Icons.Outlined.Apps)) { onEditHome() }
            .takeIf { MenuAction.EditHome !in hidden },
        MenuItem(stringResource(R.string.widget_remove), MenuIcon.MinusCircle, destructive = true) { actions.remove(widget.id) },
    )

/** One button per available size; the current one is highlighted. */
@Composable
private fun WidgetSizeRow(widget: PageWidgetEntry, sizes: List<Span>, onResize: (Span) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        sizes.forEach { size ->
            val current = size == widget.span
            Box(
                Modifier
                    .size(46.dp, 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (current) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(enabled = !current) { onResize(size) },
                contentAlignment = Alignment.Center,
            ) { WidgetSizeGlyph(size, Tokens.TextPrimary) }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, repository: AppRepository) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = when {
        !item.enabled -> Tokens.TextSecondary
        item.destructive -> Tokens.Destructive
        else -> Tokens.TextPrimary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(Tokens.MenuItemHeight)
            .background(if (pressed) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(interaction, indication = null, enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (val icon = item.icon) {
                is MenuIcon.Vector -> Icon(icon.image, null, tint = color, modifier = Modifier.size(22.dp))
                is MenuIcon.Shortcut -> ShortcutIcon(icon.shortcut, repository)
                MenuIcon.MinusCircle -> MinusCircleIcon(color)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(item.label, color = color, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShortcutIcon(shortcut: ShortcutInfo, repository: AppRepository) {
    val context = LocalContext.current
    val loader = LocalIconLoader.current
    val image: ImageBitmap? by produceState<ImageBitmap?>(null, shortcut.id) {
        value = repository.shortcutIcon(shortcut, context.resources.displayMetrics.densityDpi)?.let(loader::render)
    }
    image?.let {
        Image(it, null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
    }
}

/** The "minus.circle" SF Symbol used by iOS for "Remove App". */
@Composable
private fun MinusCircleIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val radius = size.minDimension / 2 - stroke
        drawCircle(color, radius, style = Stroke(stroke))
        val half = radius * 0.5f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke)
    }
}
