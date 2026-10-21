package com.zfl.launcher.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Wall clock for counters, ticking once a second only while a counter is composed. */
@Composable
private fun rememberWallClock(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(IslandTiming.PROGRESS_MS - now % IslandTiming.PROGRESS_MS)
        }
    }
    return now
}

private fun IslandTimer.icon(): ImageVector = when (kind) {
    IslandTimerKind.Timer -> Icons.Rounded.Timer
    IslandTimerKind.Stopwatch -> Icons.Rounded.AvTimer
    IslandTimerKind.Alarm -> Icons.Rounded.Alarm
}

private fun IslandTimer.label(): Int = when (kind) {
    IslandTimerKind.Timer -> R.string.island_timer
    IslandTimerKind.Stopwatch -> R.string.island_stopwatch
    IslandTimerKind.Alarm -> R.string.island_alarm
}

/** Big text of a clock activity: its counter, or the time of day for a ringing alarm. */
@Composable
private fun IslandTimer.value(): String? {
    val now = rememberWallClock(baseMs != null || kind == IslandTimerKind.Alarm)
    return counter(now) ?: if (kind == IslandTimerKind.Alarm) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)) else null
}

private val Digits = TextStyle(fontFeatureSettings = "tnum")

/** Orange badge on the left of the camera, the counter on the right. */
@Composable
internal fun CompactTimer(timer: IslandTimer) {
    Row(
        Modifier.width(LocalIslandStyle.current.compactWidth.dp).height(IslandTokens.CompactHeight).padding(horizontal = IslandTokens.CompactInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IslandTokens.CompactBadge).background(IslandTokens.OrangeFill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(timer.icon(), contentDescription = stringResource(timer.label()), tint = IslandTokens.Orange,
                modifier = Modifier.size(IslandTokens.CompactGlyph))
        }
        Spacer(Modifier.weight(1f))
        timer.value()?.let {
            Text(it, style = Digits, color = IslandTokens.Orange, fontSize = IslandTokens.CompactText, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.padding(end = IslandTokens.CompactInset))
        }
    }
}

/** Expanded clock activity: what it is, its big counter and the clock's own buttons. */
@Composable
internal fun TimerCard(timer: IslandTimer, width: Dp, action: (Int?) -> Unit) {
    Column(
        Modifier.width(width).padding(start = IslandTokens.CardPadding, end = IslandTokens.CardPadding,
            top = IslandTokens.CardTop, bottom = IslandTokens.CardBottom),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (timer.canOpen) Modifier.clickable(
                interactionSource = null, indication = null, role = Role.Button,
                onClickLabel = stringResource(R.string.island_open_app), onClick = { action(null) },
            ) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IslandTokens.Gap),
        ) {
            Box(Modifier.size(IslandTokens.Artwork).background(IslandTokens.OrangeFill, CircleShape), contentAlignment = Alignment.Center) {
                Icon(timer.icon(), contentDescription = null, tint = IslandTokens.Orange, modifier = Modifier.size(IslandTokens.ControlGlyph))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(timer.label()), color = IslandTokens.Orange, fontSize = IslandTokens.Title,
                    lineHeight = IslandTokens.TitleLine, fontWeight = FontWeight.SemiBold, maxLines = 1)
                (timer.title ?: timer.text)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = IslandTokens.TextSecondary, fontSize = IslandTokens.Subtitle, lineHeight = IslandTokens.SubtitleLine,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            timer.value()?.let {
                Text(it, style = Digits, color = IslandTokens.Orange, fontSize = IslandTokens.BigTime, lineHeight = IslandTokens.BigTimeLine,
                    fontWeight = FontWeight.Light, maxLines = 1)
            }
        }
        if (timer.actions.isNotEmpty()) {
            Spacer(Modifier.height(IslandTokens.Gap + IslandTokens.CardTopGap))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IslandTokens.Gap)) {
                timer.actions.forEachIndexed { index, label ->
                    TimerAction(label, primary = index == timer.actions.lastIndex, Modifier.weight(1f)) { action(index) }
                }
            }
        }
    }
}

/** A clock button as a capsule; the last one (stop, dismiss) is the orange primary. */
@Composable
private fun TimerAction(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.heightIn(min = IslandTokens.ActionHeight)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .background(if (primary) IslandTokens.OrangeFill else IslandTokens.ActionFill, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = IslandTokens.Gap),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) IslandTokens.Orange else Tokens.TextPrimary, fontSize = IslandTokens.Subtitle,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Plug-in feedback: a green bolt, then the level next to a battery glyph. */
@Composable
internal fun CompactCharging(percent: Int?) {
    Row(
        Modifier.width(LocalIslandStyle.current.compactWidth.dp).height(IslandTokens.CompactHeight).padding(horizontal = IslandTokens.CompactInset * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.island_charging), tint = IslandTokens.Green,
            modifier = Modifier.size(IslandTokens.CompactBadge))
        Spacer(Modifier.weight(1f))
        if (percent != null) {
            Text(stringResource(R.string.island_percent, percent), style = Digits, color = IslandTokens.Green,
                fontSize = IslandTokens.CompactText, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            BatteryGlyph(percent / 100f, IslandTokens.Green)
        }
    }
}

@Composable
private fun BatteryGlyph(level: Float, color: Color) {
    Canvas(Modifier.size(25.dp, 12.dp)) {
        val stroke = 1.dp.toPx()
        val cap = 2.dp.toPx()
        val body = Size(size.width - cap - stroke, size.height - stroke)
        val radius = CornerRadius(3.5.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(stroke / 2, stroke / 2), body, radius, Stroke(stroke))
        val inset = 2.dp.toPx()
        drawRoundRect(color, Offset(inset, inset), Size((body.width - inset * 2 + stroke) * level.coerceIn(0f, 1f), size.height - inset * 2),
            CornerRadius(2.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(size.width - cap, size.height * 0.33f), Size(cap, size.height * 0.34f),
            CornerRadius(cap))
    }
}
