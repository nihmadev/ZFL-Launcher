package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.WeatherCondition
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.Span
import com.zfl.launcher.ui.shape.ContinuousRoundedShape

/** App-icon-like tile for a widget kind in the gallery list and header. */
@Composable
fun KindIcon(kind: WidgetKind, size: Dp) {
    val shape = ContinuousRoundedShape(size * 0.225f)
    when (kind) {
        WidgetKind.Clock -> Box(Modifier.size(size).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
            MiniClockIcon(size * 0.86f)
        }
        WidgetKind.Calendar -> CalendarIcon(size)
        WidgetKind.Weather -> Box(Modifier.size(size).clip(shape).background(weatherBrush(WeatherCondition.Clear, isDay = true)), contentAlignment = Alignment.Center) {
            WeatherGlyph(WeatherCondition.PartlyCloudy, isDay = true, size = size * 0.62f)
        }
        WidgetKind.Photo -> GlyphTile(Icons.Rounded.Photo, size, Color.White, Color(0xFFFF9F0A))
        WidgetKind.Battery -> GlyphTile(Icons.Rounded.Bolt, size, Color(0xFF30D158), Color.White)
        WidgetKind.Suggestions -> GlyphTile(Icons.Rounded.Apps, size, Color.White, Color(0xFF0A84FF))
    }
}

@Composable
private fun GlyphTile(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, background: Color, tint: Color) {
    Box(
        Modifier
            .size(size)
            .clip(ContinuousRoundedShape(size * 0.225f))
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

/**
 * Outline of a widget size (in cells) for the size buttons of the widget menu, like the
 * iOS27L glyphs: a square, a wide card, a big card with text lines, each with a corner dot.
 */
@Composable
fun WidgetSizeGlyph(span: Span, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        // 2×2 → 16×16 dp, 4×2 → 24×15, 4×4 → 21×23, 4×1 → 24×9: proportional, kept inside the button.
        val w = (if (span.columns >= 4) 24.dp else 16.dp).toPx().let { if (span.columns >= 4 && span.rows >= 4) 21.dp.toPx() else it }
        val h = when {
            span.rows >= 4 -> 23.dp.toPx()
            span.rows == 1 -> 9.dp.toPx()
            span.columns >= 4 -> 15.dp.toPx()
            else -> 16.dp.toPx()
        }
        val topLeft = Offset((this.size.width - w) / 2, (this.size.height - h) / 2)
        drawRoundRect(color, topLeft, Size(w, h), CornerRadius(4.dp.toPx()), style = Stroke(stroke))
        val dot = 3.dp.toPx()
        drawRoundRect(color, topLeft + Offset(dot, dot), Size(dot * 1.6f, dot), CornerRadius(1.dp.toPx()))
        if (span.rows >= 4) {
            for (i in 1..3) {
                val y = topLeft.y + h * (0.35f + i * 0.15f)
                drawLine(color, Offset(topLeft.x + dot, y), Offset(topLeft.x + w - dot, y), stroke * 0.7f)
            }
        }
    }
}
