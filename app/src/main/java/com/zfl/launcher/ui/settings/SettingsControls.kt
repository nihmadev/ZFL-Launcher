package com.zfl.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Motion

/** Sizes of the iOS controls (Apple HIG): 51×31 switches and sliders with a round knob. */
private object ControlStyle {
    val SwitchWidth = 51.dp
    val SwitchHeight = 31.dp
    val KnobInset = 2.dp
    val KnobWidth = 27.dp
    /** The knob stretches a little while held, like on iOS. */
    val KnobPressedWidth = 33.dp
    val Track = Color.White.copy(alpha = 0.18f)
    val SliderTrackHeight = 4.dp
    val SliderKnobWidth = 26.dp
    val SliderKnobHeight = 26.dp
    const val SLIDER_PRESSED_SCALE = 1.15f
    val SegmentHeight = 36.dp
    val PrimaryButtonHeight = 52.dp
    val SegmentTrack = Color.White.copy(alpha = 0.10f)
    val SegmentSelected = Color.White.copy(alpha = 0.22f)
    val ButtonSize = 44.dp
}

/** iOS switch: a pill in the accent color with a round knob that stretches while held. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val toggle = withToggleHaptic(onCheckedChange)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (checked) 1f else 0f, Motion.control(), label = "switch")
    val knobWidth by animateDpAsState(
        if (pressed) ControlStyle.KnobPressedWidth else ControlStyle.KnobWidth,
        Motion.press(),
        label = "switchKnob",
    )
    val onColor = LocalAccent.current.switchOn
    Box(
        Modifier
            .size(ControlStyle.SwitchWidth, ControlStyle.SwitchHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .background(lerp(ControlStyle.Track, onColor, progress), CircleShape)
            .clickable(interaction, indication = null, enabled = enabled) { toggle(!checked) }
            .padding(ControlStyle.KnobInset),
    ) {
        Box(
            Modifier
                .offset {
                    // Read in the layout phase: the animation doesn't recompose the switch.
                    val travel = (ControlStyle.SwitchWidth - ControlStyle.KnobInset * 2 - knobWidth).toPx()
                    IntOffset((travel * progress).toInt(), 0)
                }
                .width(knobWidth)
                .fillMaxHeight()
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * iOS slider: thin track, accent fill, round white knob that grows while dragged.
 * [onValueChange] fires while dragging, [onValueChangeFinished] once the finger is lifted
 * (the moment to persist the value).
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
    var dragging by remember { mutableStateOf(false) }
    val knobScale by animateFloatAsState(if (dragging) ControlStyle.SLIDER_PRESSED_SCALE else 1f, Motion.press(), label = "sliderKnob")
    val knob = ControlStyle.SliderKnobWidth
    val accent = LocalAccent.current.primary
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
                    dragging = true
                    update(down.position.x)
                    horizontalDrag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                    dragging = false
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
                .height(ControlStyle.SliderTrackHeight)
                .background(ControlStyle.Track, CircleShape),
        )
        Box(
            Modifier
                .padding(start = knob / 2)
                .width(travel * fraction)
                .height(ControlStyle.SliderTrackHeight)
                .background(accent, CircleShape),
        )
        Box(
            Modifier
                .offset(x = travel * fraction)
                .size(knob, ControlStyle.SliderKnobHeight)
                .graphicsLayer { scaleX = knobScale; scaleY = knobScale }
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
            .padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTitle(title, modifier = Modifier.weight(1f))
            RowValue(valueText)
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

/** iOS segmented control with a sliding capsule highlight. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = launcherHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(ControlStyle.SegmentHeight)
            .background(ControlStyle.SegmentTrack, CircleShape)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val indicatorX by animateDpAsState(segment * selectedIndex, Motion.control(), label = "segment")
        Box(
            Modifier
                // Read in the layout phase: the sliding highlight doesn't recompose every frame.
                .offset { IntOffset(indicatorX.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, CircleShape)
                .background(ControlStyle.SegmentSelected, CircleShape),
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
                        .clickable(interactionSource = null, indication = null) {
                            if (index != selectedIndex) haptic.performHapticFeedback(Haptics.Tick)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** Round Liquid glass button of the top bar ("back" and screen actions), like the launcher's buttons. */
@Composable
fun GlassCircleButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(ControlStyle.ButtonSize)
            .glass(CircleShape, profile = GlassProfile.SmallButton)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (pressed) SettingsStyle.SecondaryText else Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Full-width accent capsule for the main action of a page (Import, Apply); dimmed when not [enabled]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(ControlStyle.PrimaryButtonHeight)
            .clip(CircleShape)
            .background(LocalAccent.current.primary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
