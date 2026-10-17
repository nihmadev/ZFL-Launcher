package com.zfl.launcher.ui.glass

import com.zfl.launcher.ui.theme.LiquidGlassTokens
import kotlin.math.max
import kotlin.math.min

/**
 * Mean luminance of any rectangle of a small image in O(1): a summed-area table built once
 * per wallpaper, so a glass element can adapt its tone whenever it moves without reading
 * pixels on the main thread. Coordinates are in pixels of that image.
 */
class LuminanceMap private constructor(
    val width: Int,
    val height: Int,
    /** (width + 1) × (height + 1) prefix sums; row 0 and column 0 are zero. */
    private val table: FloatArray,
) {
    /** Mean luminance (0..1) over the rectangle, clipped to the image; 0.5 if nothing is left. */
    fun mean(left: Float, top: Float, right: Float, bottom: Float): Float {
        val l = left.toInt().coerceIn(0, width)
        val t = top.toInt().coerceIn(0, height)
        val r = (right + 0.999f).toInt().coerceIn(0, width)
        val b = (bottom + 0.999f).toInt().coerceIn(0, height)
        val area = (r - l) * (b - t)
        if (area <= 0) return NEUTRAL
        val stride = width + 1
        val sum = table[b * stride + r] - table[t * stride + r] - table[b * stride + l] + table[t * stride + l]
        return (sum / area).coerceIn(0f, 1f)
    }

    companion object {
        private const val NEUTRAL = 0.5f

        /** Builds the table from ARGB pixels (row-major, [width] × [height]). */
        fun fromArgb(pixels: IntArray, width: Int, height: Int): LuminanceMap {
            require(pixels.size >= width * height) { "pixels too short" }
            val stride = width + 1
            val table = FloatArray(stride * (height + 1))
            for (y in 0 until height) {
                var row = 0f
                for (x in 0 until width) {
                    row += luminance(pixels[y * width + x])
                    table[(y + 1) * stride + x + 1] = table[y * stride + x + 1] + row
                }
            }
            return LuminanceMap(width, height, table)
        }

        /** Rec. 709 luma of an sRGB pixel, 0..1. */
        fun luminance(argb: Int): Float {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        }
    }
}

/**
 * Tone of a glass element over a background of a given luminance: the tint laid over the
 * glass (straight RGBA) and multipliers of the specular rim and the shadow under the glass. Mutable, so
 * a node can refill it without allocating when it moves.
 */
class GlassTone {
    var red: Float = 1f
    var green: Float = 1f
    var blue: Float = 1f
    var alpha: Float = 0f
    var highlight: Float = 1f
    var shadow: Float = 1f
}

/**
 * Fills [out] for the default (white, [baseAlpha]) glass tint over a background of
 * [luminance]: lighter glass over a dark background, grey glass with a more contrasting
 * highlight over a light one, like iOS. A caller-given tint is kept as it is.
 */
fun glassTone(luminance: Float, baseAlpha: Float, out: GlassTone) {
    val l = luminance.coerceIn(0f, 1f)
    val light = smoothstep(LiquidGlassTokens.ToneLightStart, LiquidGlassTokens.ToneLightEnd, l)
    val dark = 1f - smoothstep(LiquidGlassTokens.ToneDark, LiquidGlassTokens.ToneLightStart, l)
    val level = 1f + (LiquidGlassTokens.LightTintLevel - 1f) * light
    out.red = level
    out.green = level
    out.blue = level
    out.alpha = min(baseAlpha * (1f + LiquidGlassTokens.DarkTintBoost * dark), 1f)
    out.highlight = 1f + LiquidGlassTokens.LightHighlightBoost * light
    out.shadow = 1f - LiquidGlassTokens.LightShadowReduction * light
}

/** Fills [out] with a fixed tint given by the caller (straight RGBA); only the highlight adapts. */
fun fixedTone(red: Float, green: Float, blue: Float, alpha: Float, luminance: Float, out: GlassTone) {
    val light = smoothstep(LiquidGlassTokens.ToneLightStart, LiquidGlassTokens.ToneLightEnd, luminance.coerceIn(0f, 1f))
    out.red = red
    out.green = green
    out.blue = blue
    out.alpha = alpha
    out.highlight = 1f + LiquidGlassTokens.LightHighlightBoost * light
    out.shadow = 1f - LiquidGlassTokens.LightShadowReduction * light
}

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / max(edge1 - edge0, 1e-6f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
