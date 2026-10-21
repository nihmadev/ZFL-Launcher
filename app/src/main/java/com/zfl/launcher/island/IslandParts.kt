package com.zfl.launcher.island

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
internal fun IslandArtwork(bitmap: ImageBitmap?, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.size(size).clip(shape).background(IslandTokens.ArtworkFallback), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = IslandTokens.TextSecondary, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The live audio bars of iOS: they dance while [playing] and rest as dots when paused. The
 * frame clock runs only while playing and only invalidates this canvas' drawing.
 */
@Composable
internal fun IslandWaveform(playing: Boolean, color: Color, width: Dp, height: Dp, bar: Dp, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val level by animateFloatAsState(if (playing) 1f else 0f, Motion.settle(), label = "waveLevel")
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it } - (time.floatValue * 1000).toLong()
        while (true) withFrameMillis { time.floatValue = (it - start) / 1000f }
    }
    Canvas(modifier.size(width, height)) {
        val barPx = bar.toPx()
        val gap = (size.width - barPx * WAVE_BARS) / (WAVE_BARS - 1)
        val t = time.floatValue
        for (i in 0 until WAVE_BARS) {
            // Two detuned sines per bar read as music rather than a metronome.
            val swing = abs(sin(t * WAVE_SPEED[i] + i * 1.7f)) * 0.65f + abs(sin(t * WAVE_SPEED[i] * 0.53f + i)) * 0.35f
            val h = barPx + (size.height - barPx) * swing * level
            drawRoundRect(color, topLeft = Offset(i * (barPx + gap), (size.height - h) / 2), size = Size(barPx, h),
                cornerRadius = CornerRadius(barPx / 2))
        }
    }
}

/** An icon button that dips under the finger like iOS controls, with no ripple. */
@Composable
internal fun IslandIconButton(
    icon: ImageVector, label: Int, enabled: Boolean, glyph: Dp, onClick: () -> Unit,
    tint: Color = Tokens.TextPrimary, size: Dp = IslandTokens.Control,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, Motion.press(), label = "islandButton")
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) PRESSED_ALPHA else 1f }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(label), tint = if (enabled) tint else IslandTokens.Disabled,
            modifier = Modifier.size(glyph))
    }
}

private const val WAVE_BARS = 5
private val WAVE_SPEED = floatArrayOf(7.1f, 9.3f, 6.2f, 8.4f, 7.7f).map { it * (PI.toFloat() / 4f) }
private const val PRESSED_SCALE = 0.88f
private const val PRESSED_ALPHA = 0.6f
