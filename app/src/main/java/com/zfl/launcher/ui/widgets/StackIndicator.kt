package com.zfl.launcher.ui.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.zfl.launcher.ui.theme.Tokens

/**
 * iOS stack dots, beside the stack's trailing edge: one per widget, the shown one bright.
 * Drawn only while [StackPagerState.indicator] is visible; position and opacity are read in
 * the draw phase, so swiping and fading never recompose.
 */
internal fun Modifier.stackIndicator(state: StackPagerState): Modifier = drawWithContent {
    drawContent()
    val alpha = state.indicator.value
    if (alpha <= 0f || state.count < 2) return@drawWithContent
    val radius = Tokens.StackDotSize.toPx() / 2
    val step = Tokens.StackDotSize.toPx() + Tokens.StackDotGap.toPx()
    val x = size.width + Tokens.StackIndicatorMargin.toPx() + radius
    val top = size.height / 2 - step * (state.count - 1) / 2
    for (i in 0 until state.count) {
        // The bright dot slides between neighbors with the swipe.
        val nearness = (1f - kotlin.math.abs(state.position - i)).coerceIn(0f, 1f)
        val color = lerp(DotInactive, Color.White, nearness)
        drawCircle(color, radius, Offset(x, top + step * i), alpha = alpha)
    }
}

private val DotInactive = Color.White.copy(alpha = 0.35f)

