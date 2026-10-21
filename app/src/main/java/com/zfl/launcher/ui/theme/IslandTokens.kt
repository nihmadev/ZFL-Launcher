package com.zfl.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Island: a black capsule wrapped around the front camera that morphs into a glass card.
 * Compact shapes stay between the status bar clock and its icons; the card spans the screen.
 */
object IslandTokens {
    // Shapes. The compact capsule is vertically centered on the camera.
    /** Seed and end of the appear/disappear morph: a dot hidden by the camera. */
    val Dot = 18.dp
    val CompactHeight = 34.dp
    /** The empty capsule kept around the camera when Island is always shown. */
    val IdleWidth = 112.dp
    val CardWidth = 440.dp
    val MinCardWidth = 280.dp
    /** Room reserved in the window while a card expands, more than any card needs. */
    val CardMaxHeight = 320.dp
    /** Corner radius cap: below twice this height the shape is a capsule. */
    val CardRadius = 46.dp
    /** Height at which the black capsule has fully turned into the glass card. */
    val GlassBlendHeight = 110.dp
    val EdgeGap = 6.dp
    /** Extra room in the window while a spring overshoots, so the window is not resized every frame. */
    val MorphSlack = 12.dp

    // Compact content
    val CompactInset = 6.dp
    val CompactArtwork = 22.dp
    val CompactArtworkRadius = 7.dp
    val CompactGlyph = 16.dp
    val CompactBadge = 22.dp
    val Wave = 20.dp
    val WaveHeight = 14.dp
    val WaveBar = 2.5.dp

    // Card content
    val CardPadding = 22.dp
    /** Card content starts below the camera row. */
    val CardTop = 36.dp
    val CardTopGap = 8.dp
    val CardBottom = 18.dp
    val Artwork = 58.dp
    val ArtworkRadius = 14.dp
    val CardWave = 28.dp
    val CardWaveHeight = 20.dp
    val CardWaveBar = 3.dp
    val Gap = 12.dp
    val Control = 52.dp
    val ControlGlyph = 34.dp
    val PlayGlyph = 42.dp
    val ControlGap = 30.dp
    val Track = 6.dp
    val TrackActive = 10.dp
    val ActionHeight = 46.dp
    val Edge = 1.dp

    // Type
    val CompactText = 15.sp
    val Title = 16.sp
    val TitleLine = 21.sp
    val Subtitle = 15.sp
    val SubtitleLine = 19.sp
    val Time = 12.sp
    val TimeLine = 16.sp
    val BigTime = 44.sp
    val BigTimeLine = 50.sp

    // Color
    val Pill = Color.Black
    /** Card body without blur, and over the blurred backdrop (the glass of the reference). */
    val CardBase = Color(0xFA141418)
    val CardGlass = Color(0x8C101014)
    val BlurRadius = 36.dp
    val ArtworkFallback = Color(0xFF2C2C30)
    val Waveform = Color.White.copy(alpha = 0.85f)
    val TrackColor = Color.White.copy(alpha = 0.22f)
    val TrackFill = Color.White.copy(alpha = 0.92f)
    val TextSecondary = Color.White.copy(alpha = 0.58f)
    val Disabled = Color.White.copy(alpha = 0.3f)
    val EdgeTop = Color.White.copy(alpha = 0.34f)
    val EdgeMid = Color.White.copy(alpha = 0.05f)
    val EdgeBottom = Color.White.copy(alpha = 0.14f)
    val Specular = Color.White.copy(alpha = 0.09f)
    const val ACCENT_TOP_ALPHA: Float = 0.34f
    const val ACCENT_BOTTOM_ALPHA: Float = 0.12f
    const val ACCENT_GLOW_ALPHA: Float = 0.55f
    /** iOS system orange (timers, alarms) and green (charging). */
    val Orange = Color(0xFFFF9F0A)
    val OrangeFill = Color(0x3DFF9F0A)
    val Green = Color(0xFF30D158)
    val ActionFill = Color.White.copy(alpha = 0.14f)

    /** Conservative intrinsic height budget for text, controls and progress at the current font scale. */
    const val CARD_FIXED_HEIGHT_DP: Float = 210f
    const val CARD_TEXT_HEIGHT_DP: Float = 60f
}
