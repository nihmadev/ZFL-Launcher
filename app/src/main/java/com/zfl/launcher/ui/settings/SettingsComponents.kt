package com.zfl.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/** Colors and sizes of the settings screens, measured from the iOS27L reference (docs/DESIGN.md). */
object SettingsStyle {
    /** Main settings page background. */
    val Background = Color.Black
    /** Card on the black main page. */
    val SolidCard = Color(0xFF1C1C1E)
    /** Card on the blurred-wallpaper background of sub pages. */
    val GlassCard = Color.White.copy(alpha = 0.08f)
    val CardStroke = Color.White.copy(alpha = 0.10f)
    val SectionLabel = Color(0xFF8E8E93)
    val SecondaryText = Color(0xFF8E8E93)
    val Chevron = Color(0xFF6C6C70)
    val Pressed = Color.White.copy(alpha = 0.08f)
    val SwitchOn = Color(0xFF34C759)
    val SwitchOff = Color(0xFF39393D)
    val Link = Tokens.Accent

    val CardShape = ContinuousRoundedShape(26.dp)
    val RowHeight = 54.dp
    val IconTileSize = 34.dp
}

/** Whether cards are drawn on the black main page or on a blurred sub page. */
enum class SettingsSurface { Solid, Glass }

val LocalSettingsSurface = compositionLocalOf { SettingsSurface.Solid }

/** Rounded card that groups settings rows. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val color = if (LocalSettingsSurface.current == SettingsSurface.Solid) SettingsStyle.SolidCard else SettingsStyle.GlassCard
    Column(
        modifier
            .fillMaxWidth()
            .clip(SettingsStyle.CardShape)
            .background(color)
            .border(1.dp, SettingsStyle.CardStroke, SettingsStyle.CardShape),
        content = content,
    )
}

/** Small gray title above a card. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = SettingsStyle.SectionLabel,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** Gray explanatory text under a card. */
@Composable
fun SectionFooter(text: String) {
    Text(
        text = text,
        color = SettingsStyle.SecondaryText,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    )
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Dark rounded tile with a light glyph, used in front of navigation rows. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    val shape = ContinuousRoundedShape(9.dp)
    Box(
        modifier
            .size(SettingsStyle.IconTileSize)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4A4A50), Color(0xFF2A2A2E))))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFD1D1D6), modifier = Modifier.size(20.dp))
    }
}

/** Row that opens another screen: icon tile, title, optional value and a chevron. */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    titleColor: Color = Color.White,
    showChevron: Boolean = true,
) {
    SettingsRow(onClick = onClick) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        RowTitle(title, color = titleColor, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = SettingsStyle.SecondaryText, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        if (showChevron) Chevron()
    }
}

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SettingsStyle.Chevron, modifier = Modifier.size(22.dp))
}

/** Row with a title, an optional description and an iOS switch. Tapping anywhere toggles it. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    SettingsRow(onClick = { onCheckedChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            RowTitle(title)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = SettingsStyle.SecondaryText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked, onCheckedChange)
    }
}

/** Row of a single-choice list, with a checkmark on the selected one. */
@Composable
fun CheckRow(title: String, selected: Boolean, onClick: () -> Unit) {
    SettingsRow(onClick = onClick) {
        RowTitle(title, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** iOS-style switch: 51×31dp pill with a sliding white knob and a spring animation. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "switch",
    )
    val travelPx = with(LocalDensity.current) { 20.dp.toPx() }
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(lerp(SettingsStyle.SwitchOff, SettingsStyle.SwitchOn, progress))
            .clickable(enabled = enabled, interactionSource = null, indication = null) { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset((travelPx * progress).roundToInt(), 0) }
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * iOS-style slider: thin track, blue fill, white knob. [onValueChange] fires while dragging,
 * [onValueChangeFinished] once the finger is lifted (good moment to persist the value).
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val knob = 26.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val knobPx = knob.toPx()
                fun update(x: Float) {
                    val fraction = ((x - knobPx / 2) / (size.width - knobPx)).coerceIn(0f, 1f)
                    onChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x)
                    horizontalDrag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                    onFinished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val travel = maxWidth - knob
        Box(
            Modifier
                .padding(horizontal = knob / 2)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        )
        Box(
            Modifier
                .padding(start = knob / 2)
                .width(travel * fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(SettingsStyle.Link),
        )
        Box(
            Modifier
                .offset(x = travel * fraction)
                .size(knob)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * A titled [IosSlider] with its current value on the right. The caller keeps the dragged
 * value locally ([onValueChange]) and persists it in [onValueChangeFinished].
 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTitle(title, modifier = Modifier.weight(1f))
            Text(valueText, color = SettingsStyle.SecondaryText, fontSize = 15.sp, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        IosSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/** iOS segmented control with a sliding highlight. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val indicatorX by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.8f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, CircleShape)
                .background(Color.White.copy(alpha = 0.22f), CircleShape),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selectedIndex) Color.White else SettingsStyle.SecondaryText, label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = null, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Round translucent button used for "back" and top bar actions. */
@Composable
fun GlassCircleButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = 600f), label = "press")
    Box(
        modifier
            .size(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
