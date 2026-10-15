package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zfl.launcher.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/** Second hand and hub of the iOS clock face. */
private val ClockOrange = Color(0xFFFF9F0A)
private val ClockFace = Color(0xFFF5F5F5)
private val ClockInk = Color(0xFF1C1C1E)

/**
 * Current time, ticking every [stepMillis] while the screen is visible (stopped in the
 * background, so off-screen clocks don't keep the CPU awake). Read it in draw lambdas.
 */
@Composable
internal fun rememberNow(stepMillis: Long): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), lifecycle, stepMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(stepMillis - System.currentTimeMillis() % stepMillis)
            }
        }
    }
}

/** Small "Clock": a big white dial with numerals, as in iOS27L. */
@Composable
internal fun AnalogClockWidget() {
    val now = rememberNow(1000)
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        ClockDial(now, ZoneId.systemDefault(), measurer, seconds = true, Modifier.fillMaxSize())
    }
}

/** Cities of the World Clock widget, after the local time ("Now"). */
private val WorldCities = listOf(
    R.string.city_paris to "Europe/Paris",
    R.string.city_new_york to "America/New_York",
    R.string.city_london to "Europe/London",
)

/** Medium "World Clock": the local time and three cities, with the hour difference. */
@Composable
internal fun WorldClockWidget() {
    val now = rememberNow(1000)
    val measurer = rememberTextMeasurer()
    val local = ZoneId.systemDefault()
    val zones = listOf(stringResource(R.string.city_now) to local) +
        WorldCities.map { (name, zone) -> stringResource(name) to ZoneId.of(zone) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        zones.forEach { (name, zone) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClockDial(now, zone, measurer, seconds = true, Modifier.size(78.dp))
                Spacer(Modifier.height(6.dp))
                Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(dayLabel(now.value, zone, local), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text(offsetLabel(now.value, zone, local), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun dayLabel(now: Long, zone: ZoneId, local: ZoneId): String {
    val there = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val here = Instant.ofEpochMilli(now).atZone(local).toLocalDate()
    return stringResource(
        when {
            there.isAfter(here) -> R.string.clock_tomorrow
            there.isBefore(here) -> R.string.clock_yesterday
            else -> R.string.clock_today
        },
    )
}

@Composable
private fun offsetLabel(now: Long, zone: ZoneId, local: ZoneId): String {
    val instant = Instant.ofEpochMilli(now)
    val hours = (zone.rules.getOffset(instant).totalSeconds - local.rules.getOffset(instant).totalSeconds) / 3600
    return stringResource(R.string.clock_offset_hours, if (hours > 0) "+$hours" else hours.toString())
}

/** An iOS-style analog dial: minute ticks, numerals 1–12, black hands, orange second hand. */
@Composable
private fun ClockDial(now: State<Long>, zone: ZoneId, measurer: TextMeasurer, seconds: Boolean, modifier: Modifier) {
    val numeralStyle = remember { TextStyle(color = ClockInk, fontWeight = FontWeight.Medium) }
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(ClockFace, radius)
        drawTicks(radius)
        drawNumerals(radius, measurer, numeralStyle)
        // Read the time here, in the draw phase: ticking redraws only this canvas.
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now.value), zone)
        val minutes = time.minute + time.second / 60f
        val hours = time.hour % 12 + minutes / 60f
        drawHand(hours * 30f, radius * 0.5f, radius * 0.07f, ClockInk)
        drawHand(minutes * 6f, radius * 0.78f, radius * 0.07f, ClockInk)
        drawCircle(ClockInk, radius * 0.06f)
        if (seconds) {
            drawHand(time.second * 6f, radius * 0.88f, radius * 0.025f, ClockOrange, tail = radius * 0.18f)
            drawCircle(ClockOrange, radius * 0.035f)
        }
    }
}

private fun DrawScope.drawTicks(radius: Float) {
    for (i in 0 until 60) {
        val major = i % 5 == 0
        val length = radius * if (major) 0.09f else 0.05f
        rotate(i * 6f) {
            drawLine(
                color = if (major) ClockInk else ClockInk.copy(alpha = 0.45f),
                start = Offset(center.x, center.y - radius * 0.94f),
                end = Offset(center.x, center.y - radius * 0.94f + length),
                strokeWidth = radius * if (major) 0.022f else 0.012f,
            )
        }
    }
}

private fun DrawScope.drawNumerals(radius: Float, measurer: TextMeasurer, style: TextStyle) {
    val fontSize = (radius * 0.22f).toSp()
    for (hour in 1..12) {
        val angle = Math.toRadians(hour * 30.0 - 90)
        val layout = measurer.measure(hour.toString(), style.copy(fontSize = fontSize))
        val r = radius * 0.7f
        val x = center.x + (r * cos(angle)).toFloat() - layout.size.width / 2f
        val y = center.y + (r * sin(angle)).toFloat() - layout.size.height / 2f
        drawText(layout, topLeft = Offset(x, y))
    }
}

private fun DrawScope.drawHand(degrees: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(degrees) {
        drawLine(
            color = color,
            start = Offset(center.x, center.y + tail),
            end = Offset(center.x, center.y - length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** A tiny dial for the gallery's kind icon. */
@Composable
internal fun MiniClockIcon(size: Dp) {
    val now = rememberNow(60_000)
    val measurer = rememberTextMeasurer()
    ClockDial(now, TimeZone.getDefault().toZoneId(), measurer, seconds = false, Modifier.size(size))
}
