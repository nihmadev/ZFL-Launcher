package com.zfl.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import com.zfl.launcher.ui.shape.shapeBorder
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.glass.GlassMode
import com.zfl.launcher.ui.glass.LocalGlassStyle
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.IconStyle
import com.zfl.launcher.ui.icon.LocalIconLoader
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Tokens

/** Colors and sizes of the settings screens (iOS grouped lists drawn in the launcher's glass). */
object SettingsStyle {
    /** Darkening of the blurred wallpaper behind every settings page. */
    val PageScrim = Color.Black.copy(alpha = 0.5f)
    /** Card on a solid black page (onboarding). */
    val SolidCard = Color(0xFF1C1C1E)
    /** Tint of the glass cards, dark like the launcher's sheets so white text stays legible. */
    val CardTint = Color.Black.copy(alpha = 0.28f)
    /** Flat translucent fill for small capsules drawn over the page. */
    val GlassCard = Color.White.copy(alpha = 0.08f)
    val CardStroke = Color.White.copy(alpha = 0.10f)
    val Separator = Color.White.copy(alpha = 0.13f)
    val SectionLabel = Color.White.copy(alpha = 0.6f)
    val SecondaryText = Color.White.copy(alpha = 0.55f)
    val Chevron = Color.White.copy(alpha = 0.35f)
    val Pressed = Color.White.copy(alpha = 0.10f)
    val Destructive = Tokens.Destructive

    val CardShape = ContinuousRoundedShape(26.dp)
    val RowHeight = 52.dp
    val RowPadding = 16.dp
    val IconTileSize = 30.dp
    val IconTileGap = 14.dp
    val TitleSize = 17.sp
    val ValueSize = 17.sp
    val SubtitleSize = 13.sp

    /** iOS system colors (dark appearance) for the icon tiles in front of rows. */
    object Tile {
        val Gray = Color(0xFF8E8E93)
        val Blue = Color(0xFF0A84FF)
        val Indigo = Color(0xFF5E5CE6)
        val Purple = Color(0xFFBF5AF2)
        val Pink = Color(0xFFFF375F)
        val Red = Color(0xFFFF453A)
        val Orange = Color(0xFFFF9F0A)
        val Yellow = Color(0xFFFFCC00)
        val Green = Color(0xFF30D158)
        val Teal = Color(0xFF40C8E0)
        val Cyan = Color(0xFF64D2FF)
    }
}

/** Whether cards are drawn on a solid page (onboarding) or as glass over the wallpaper (settings). */
enum class SettingsSurface { Solid, Glass }

val LocalSettingsSurface = compositionLocalOf { SettingsSurface.Solid }

/** Marks a direct child of [SettingsCard]: the separator above it starts [inset] from the card's edge. */
private data class SeparatorInset(val inset: Dp)

/** Separator inset of rows with an icon tile: under the title, like iOS. */
private val TileRowInset = SettingsStyle.RowPadding + SettingsStyle.IconTileSize + SettingsStyle.IconTileGap

/**
 * Rounded card that groups settings rows, with a hairline separator between visible rows.
 * On settings pages it is a pane of the launcher's glass; on a solid page a flat dark card.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val surface = LocalSettingsSurface.current
    // Separator positions (y, inset) found while placing the rows; read only in the draw phase.
    var separators by remember { mutableStateOf(emptyList<Pair<Int, Int>>()) }
    val glassStyle = LocalGlassStyle.current
    val background = if (surface == SettingsSurface.Glass) {
        // Frosted even when the launcher uses Liquid glass: rows of text need the blurred
        // body, a sharp refracted wallpaper behind them would hurt legibility.
        Modifier.glass(SettingsStyle.CardShape, tint = SettingsStyle.CardTint)
    } else {
        Modifier
            .background(SettingsStyle.SolidCard, SettingsStyle.CardShape)
            .shapeBorder(1.dp, SettingsStyle.CardStroke, SettingsStyle.CardShape)
    }
    CompositionLocalProvider(LocalGlassStyle provides glassStyle.copy(mode = GlassMode.Frosted)) {
        Layout(
            content = content,
            modifier = modifier
                .fillMaxWidth()
                .then(background)
                .clip(SettingsStyle.CardShape)
                .drawWithContent {
                    drawContent()
                    val thickness = 1f
                    separators.forEach { (y, inset) ->
                        drawRect(SettingsStyle.Separator, Offset(inset.toFloat(), y - thickness / 2), Size(size.width - inset, thickness))
                    }
                },
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(childConstraints) }
            val defaultInset = SettingsStyle.RowPadding.roundToPx()
            val found = ArrayList<Pair<Int, Int>>()
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                if (placeable.height == 0) return@forEachIndexed
                if (y > 0) {
                    val inset = (measurables[index].layoutId as? SeparatorInset)?.inset?.roundToPx() ?: defaultInset
                    found += y to inset
                }
                y += placeable.height
            }
            if (found != separators) separators = found
            layout(constraints.maxWidth, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                var top = 0
                placeables.forEach { it.place(0, top); top += it.height }
            }
        }
    }
}

/** Small title above a card. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = SettingsStyle.SectionLabel,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = SettingsStyle.RowPadding, end = SettingsStyle.RowPadding, top = 28.dp, bottom = 8.dp),
    )
}

/** Gray explanatory text under a card. */
@Composable
fun SectionFooter(text: String) {
    Text(
        text = text,
        color = SettingsStyle.SecondaryText,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = SettingsStyle.RowPadding, end = SettingsStyle.RowPadding, top = 8.dp),
    )
}

/** Empty space between two cards that need no title, like the groups of the iOS Settings list. */
@Composable
fun SectionGap() {
    Spacer(Modifier.height(28.dp))
}

/** A clickable row that highlights while pressed, like iOS table cells. */
@Composable
fun SettingsRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsStyle.RowHeight)
            .background(if (pressed) SettingsStyle.Pressed else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = SettingsStyle.RowPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Rounded tile with a glyph in front of a row, drawn like the app icons on the home screen:
 * the iOS system [color] with a white glyph for Default icons, the [color] glyph on the dark
 * icon backdrop for Dark ones, and the icons' own tint for Tinted ones, with the same glass edge.
 */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val settings = LocalSettingsState.current
    val shape = ContinuousRoundedShape(SettingsStyle.IconTileSize * settings.iconCorner)
    val style = LocalIconLoader.current.iconStyle
    val plain = style == null || style == IconStyle.Plain
    val background = if (plain) {
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), color))
    } else {
        Brush.verticalGradient(listOf(Tokens.IconDarkTop, Tokens.IconDarkBottom))
    }
    val glyph = when (style) {
        is IconStyle.Tinted -> Color(style.color)
        IconStyle.Dark -> lerp(color, Color.White, 0.15f)
        else -> Color.White
    }
    Box(
        modifier
            .size(SettingsStyle.IconTileSize)
            .clip(shape)
            .background(background)
            .then(if (settings.iconEdge) Modifier.shapeBorder(Tokens.GlassStrokeWidth, TileEdge, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(19.dp))
    }
}

/** The top-lit glass edge of app icons, see [Tokens.GlassStroke]. */
private val TileEdge = Brush.verticalGradient(
    0f to Tokens.GlassStroke,
    0.5f to Tokens.GlassStroke.copy(alpha = Tokens.GlassStroke.alpha * Tokens.GlassStrokeMidAlpha),
    1f to Tokens.GlassStroke.copy(alpha = Tokens.GlassStroke.alpha * Tokens.GlassStrokeBottomAlpha),
)

/** Icon tile, title and whatever follows it; the separator above starts under the title. */
@Composable
private fun TileRow(
    icon: ImageVector,
    tileColor: Color,
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
    title: @Composable RowScope.() -> Unit,
) {
    SettingsRow(onClick = onClick, modifier = Modifier.layoutId(SeparatorInset(TileRowInset))) {
        IconTile(icon, tileColor)
        Spacer(Modifier.width(SettingsStyle.IconTileGap))
        title()
        trailing()
    }
}

/**
 * Row that opens another screen: icon tile, title, optional value and a chevron. Without
 * [onClick] it is a plain information row (no highlight, no chevron).
 */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    value: String? = null,
    tileColor: Color = SettingsStyle.Tile.Gray,
    titleColor: Color = Color.White,
    showChevron: Boolean = onClick != null,
) {
    TileRow(
        icon = icon,
        tileColor = tileColor,
        onClick = onClick,
        title = { RowTitle(title, color = titleColor, modifier = Modifier.weight(1f)) },
        trailing = {
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                RowValue(value)
            }
            if (showChevron) {
                Spacer(Modifier.width(4.dp))
                Chevron()
            }
        },
    )
}

/** Icon tile, title and a switch: a setting that is turned on and off right from the list. */
@Composable
fun IconSwitchRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tileColor: Color) {
    val toggle = withToggleHaptic(onCheckedChange)
    TileRow(
        icon = icon,
        tileColor = tileColor,
        onClick = { toggle(!checked) },
        title = { RowTitle(title, modifier = Modifier.weight(1f)) },
        trailing = {
            Spacer(Modifier.width(12.dp))
            IosSwitch(checked, onCheckedChange)
        },
    )
}

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = SettingsStyle.TitleSize,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Current value on the right of a row, in gray. */
@Composable
fun RowValue(text: String) {
    Text(text, color = SettingsStyle.SecondaryText, fontSize = SettingsStyle.ValueSize, maxLines = 1)
}

@Composable
fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SettingsStyle.Chevron, modifier = Modifier.size(24.dp))
}

/** Row with a title, an optional description and an iOS switch. Tapping anywhere toggles it. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    val toggle = withToggleHaptic(onCheckedChange)
    SettingsRow(onClick = { toggle(!checked) }) {
        Column(Modifier.weight(1f)) {
            RowTitle(title)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = SettingsStyle.SecondaryText, fontSize = SettingsStyle.SubtitleSize)
            }
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked, onCheckedChange)
    }
}

/** Row of a single-choice list, with an accent checkmark on the selected one. */
@Composable
fun CheckRow(title: String, selected: Boolean, onClick: () -> Unit) {
    SettingsRow(onClick = onClick) {
        RowTitle(title, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = LocalAccent.current.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Wraps a switch callback so every toggle by the user is felt, on or off. */
@Composable
internal fun withToggleHaptic(onCheckedChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptic = launcherHaptics()
    val current by rememberUpdatedState(onCheckedChange)
    return remember(haptic) {
        { value -> haptic.performHapticFeedback(if (value) Haptics.ToggleOn else Haptics.ToggleOff); current(value) }
    }
}
