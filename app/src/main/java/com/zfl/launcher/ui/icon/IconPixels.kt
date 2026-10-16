package com.zfl.launcher.ui.icon

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Pixel operations behind the Dark and Tinted icon styles. They work on unpremultiplied ARGB
 * arrays (as returned by Bitmap.getPixels) of a square icon of side `s`, and use no Android
 * classes so they are covered by JVM tests.
 */

/** Alpha above which a pixel counts as part of the shape (ignores soft drop shadows). */
internal const val ALPHA_SOLID: Int = 200

/** Pixel rectangle: [left]..[right) × [top]..[bottom). */
internal data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

/** Bounding box of the mostly opaque pixels, or `null` if there are none. */
internal fun opaqueBounds(pixels: IntArray, s: Int): PixelBox? {
    var left = s; var top = s; var right = -1; var bottom = -1
    for (y in 0 until s) for (x in 0 until s) {
        if (alpha(pixels[y * s + x]) > ALPHA_SOLID) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    return if (right < left) null else PixelBox(left, top, right + 1, bottom + 1)
}

/**
 * True when the shape is a filled, roughly square tile (e.g. MIUI system icons). Probes points
 * near the corners: they are inside a rounded square (corner radius up to ~30%) but outside a circle.
 */
internal fun fillsBounds(pixels: IntArray, s: Int, box: PixelBox): Boolean {
    val aspect = box.width.toFloat() / box.height
    if (aspect !in 0.9f..1.1f || box.width < s / 2) return false
    val probes = listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.1f to 0.9f, 0.9f to 0.9f, 0.5f to 0.5f, 0.5f to 0.03f, 0.03f to 0.5f)
    return probes.all { (fx, fy) ->
        val x = (box.left + fx * (box.width - 1)).toInt()
        val y = (box.top + fy * (box.height - 1)).toInt()
        alpha(pixels[y * s + x]) > ALPHA_SOLID
    }
}

/**
 * The backdrop of an icon: a tile, disk or plate behind the actual symbol. Described by the
 * per-channel color range of its rim, so gradient plates are recognized as a whole.
 */
internal class Plate(private val lo: IntArray, private val hi: IntArray) {
    /** Brightness of the plate's middle color. */
    val luma: Float = luma(argb(255, (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2))

    /** A white or near-white plate: Dark removes it instead of dimming it. */
    val isLight: Boolean get() = luma > LIGHT_PLATE_LUMA

    /** 1 for the plate's own colors, fading to 0 for clearly different (symbol) colors. */
    fun weight(c: Int): Float {
        val d = maxOf(
            distance(red(c), lo[0], hi[0]),
            distance(green(c), lo[1], hi[1]),
            distance(blue(c), lo[2], hi[2]),
        )
        if (d <= 0) return 1f
        if (d >= PLATE_SOFTNESS) return 0f
        val t = d / PLATE_SOFTNESS.toFloat()
        return 1f - t * t * (3f - 2f * t)
    }

    private fun distance(v: Int, lo: Int, hi: Int): Int = max(0, max(lo - v, v - hi))
}

/**
 * Finds the plate the symbol sits on: the rim just inside the opaque shape must be one color
 * (or one smooth gradient), be the outermost part of the shape, cover most of the icon and
 * leave a visible symbol.
 * Returns `null` for transparent-background glyphs and multicolor art such as a Chrome logo.
 */
internal fun findPlate(pixels: IntArray, s: Int): Plate? {
    val outer = ArrayList<Int>(s * 4)
    val samples = rimSamples(pixels, s, outer)
    if (samples.size < s) return null
    val median = IntArray(3) { ch -> samples.map { channel(it, ch) }.sorted()[samples.size / 2] }
    val near = samples.filter { c -> (0 until 3).all { ch -> kotlin.math.abs(channel(c, ch) - median[ch]) <= RIM_TOLERANCE } }
    if (near.size < samples.size * RIM_SHARE) return null
    val plate = Plate(
        lo = IntArray(3) { ch -> near.minOf { channel(it, ch) } },
        hi = IntArray(3) { ch -> near.maxOf { channel(it, ch) } },
    )

    var solid = 0
    var onPlate = 0
    for (c in pixels) {
        if (alpha(c) <= ALPHA_SOLID) continue
        solid++
        if (plate.weight(c) > 0.5f) onPlate++
    }
    if (solid == 0) return null
    // The plate is the outermost part of the shape: a light glyph with a dark outline is not one.
    if (outer.count { plate.weight(it) > 0.5f } < outer.size * OUTER_SHARE) return null
    val symbol = solid - onPlate
    // A one-color glyph is all "rim": it is the symbol, not a plate.
    if (onPlate < solid * MIN_PLATE_SHARE || symbol < solid * MIN_SYMBOL_SHARE) return null
    // A backdrop fills most of the icon. A smaller one-color shape with a symbol inside (VLC's
    // white cone, Authenticator's lock) is part of the art and must stay.
    if (onPlate < pixels.size * BACKDROP_AREA) return null
    return plate
}

/**
 * Colors a few pixels inside the left/right and top/bottom edges of the opaque shape; the
 * edge pixels themselves are added to [outer].
 */
private fun rimSamples(pixels: IntArray, s: Int, outer: MutableList<Int>): List<Int> {
    val inset = max(2, s / 32)
    val out = ArrayList<Int>(s * 4)
    fun take(i: Int) {
        if (alpha(pixels[i]) > ALPHA_SOLID) out += pixels[i]
    }
    for (line in 0 until s) {
        for (horizontal in booleanArrayOf(true, false)) {
            fun index(pos: Int) = if (horizontal) line * s + pos else pos * s + line
            var first = -1
            var last = -1
            for (pos in 0 until s) {
                if (alpha(pixels[index(pos)]) > ALPHA_SOLID) {
                    if (first < 0) first = pos
                    last = pos
                }
            }
            if (first < 0 || last - first <= inset * 2) continue
            take(index(first + inset))
            take(index(last - inset))
            outer += pixels[index(first)]
            outer += pixels[index(last)]
        }
    }
    return out
}

private fun channel(c: Int, ch: Int): Int = when (ch) {
    0 -> red(c)
    1 -> green(c)
    else -> blue(c)
}

/**
 * Turns the icon into a monochrome glyph in [tint], in place. The plate (if any) and faint
 * shadows or sheens become transparent; what is left keeps its shape (alpha) and, for multicolor art
 * without a plate, its relative brightness, so details inside the glyph stay visible.
 *
 * @return false when nothing visible is left.
 */
internal fun tintGlyph(pixels: IntArray, plate: Plate?, tint: Int): Boolean {
    val coverage = FloatArray(pixels.size)
    val histogram = IntArray(LUMA_BINS)
    var count = 0
    for (i in pixels.indices) {
        val c = pixels[i]
        val a = alpha(c) / 255f
        // Faint overlays (drop shadows, sheens) are not part of the symbol; antialiased edges
        // fade out smoothly instead of being cut.
        val solid = ((a - OVERLAY_ALPHA) / OVERLAY_FADE).coerceIn(0f, 1f)
        if (solid == 0f) continue
        var cover = a * solid * (1f - (plate?.weight(c) ?: 0f))
        // A symbol sits inside its plate: what reaches the outline of the shape is the plate's
        // own edge, blended with shadows by scaling, not part of the symbol.
        if (plate != null && cover > 0f && nearOutline(pixels, i)) cover = 0f
        coverage[i] = cover
        if (cover > 0.5f) {
            histogram[(luma(c) * (LUMA_BINS - 1)).roundToInt()]++
            count++
        }
    }
    if (count == 0) return false
    // Robust brightness range of the glyph (ignores a few outliers such as antialiasing).
    val lo = percentile(histogram, count, 0.05f)
    val hi = percentile(histogram, count, 0.95f)
    val range = hi - lo
    for (i in pixels.indices) {
        val cover = coverage[i]
        if (cover <= 0f) {
            pixels[i] = 0
            continue
        }
        // A symbol cut from a plate is flat: its antialiased edges are blends with the plate,
        // and keying brightness on them would draw an outline instead of a solid symbol.
        val intensity = if (plate != null || range < FLAT_GLYPH_RANGE) {
            1f
        } else {
            MIN_GLYPH_INTENSITY + (1f - MIN_GLYPH_INTENSITY) * ((luma(pixels[i]) - lo) / range).coerceIn(0f, 1f)
        }
        pixels[i] = argb(
            (cover * 255).roundToInt(),
            (red(tint) * intensity).roundToInt(),
            (green(tint) * intensity).roundToInt(),
            (blue(tint) * intensity).roundToInt(),
        )
    }
    return true
}

/** True if a pixel [EDGE_REACH] px left, right, above or below is not solid (or off the icon). */
private fun nearOutline(pixels: IntArray, i: Int): Boolean {
    val s = sqrt(pixels.size.toFloat()).toInt()
    val x = i % s
    val y = i / s
    fun solid(px: Int, py: Int) = px in 0 until s && py in 0 until s && alpha(pixels[py * s + px]) > ALPHA_SOLID
    return !(solid(x - EDGE_REACH, y) && solid(x + EDGE_REACH, y) && solid(x, y - EDGE_REACH) && solid(x, y + EDGE_REACH))
}

private fun percentile(histogram: IntArray, count: Int, fraction: Float): Float {
    val target = (count * fraction).toInt()
    var seen = 0
    for (bin in histogram.indices) {
        seen += histogram[bin]
        if (seen > target) return bin / (LUMA_BINS - 1f)
    }
    return 1f
}

/**
 * Adapts an icon layer for a dark backdrop, in place:
 * - a light plate is removed (the dark backdrop shows through, like iOS dark icons);
 * - a colored plate is multiplied by [plateDim] (1 keeps it), never inverted;
 * - without a plate, everything is multiplied by [unplatedDim];
 * - content that is mostly near-black is lifted toward white so it doesn't vanish.
 */
internal fun darkenLayer(pixels: IntArray, plate: Plate?, plateDim: Float = 1f, unplatedDim: Float = 1f) {
    val plateWeight = FloatArray(pixels.size)
    var weightSum = 0f
    var lumaSum = 0f
    for (i in pixels.indices) {
        val c = pixels[i]
        if (alpha(c) == 0) continue
        var out = c
        val w = plate?.weight(c) ?: 0f
        plateWeight[i] = w
        when {
            plate == null -> if (unplatedDim < 1f) out = scale(c, unplatedDim)
            plate.isLight -> out = (c and 0xFFFFFF) or (((alpha(c) * (1f - w)).roundToInt()) shl 24)
            plateDim < 1f -> out = scale(c, 1f - (1f - plateDim) * w)
        }
        pixels[i] = out
        // The brightness that decides about lifting only counts the symbol, not the plate.
        val symbol = alpha(out) / 255f * (1f - w)
        weightSum += symbol
        lumaSum += symbol * luma(out)
    }
    if (weightSum == 0f || lumaSum / weightSum >= DARK_CONTENT_LUMA) return
    for (i in pixels.indices) {
        val c = pixels[i]
        if (alpha(c) == 0) continue
        val lift = LIFT * (1f - plateWeight[i]) * (1f - luma(c) / LIFT_BELOW).coerceIn(0f, 1f)
        if (lift > 0f) pixels[i] = mix(c, WHITE, lift)
    }
}

/** Fraction of the pixels with a visible alpha. */
internal fun coverage(pixels: IntArray): Float = pixels.count { alpha(it) > ALPHA_SOLID } / pixels.size.toFloat()

private fun scale(c: Int, k: Float): Int =
    argb(alpha(c), (red(c) * k).roundToInt(), (green(c) * k).roundToInt(), (blue(c) * k).roundToInt())

/** Rim colors may differ by this much per channel and still be one plate. */
private const val RIM_TOLERANCE = 40
/** Share of rim samples that must belong to the plate. */
private const val RIM_SHARE = 0.6f
/** Share of the whole icon a plate must cover to be a backdrop. */
private const val BACKDROP_AREA = 0.5f
/** Share of the shape's edge pixels that must have the plate color. */
private const val OUTER_SHARE = 0.5f
/** The plate must cover at least this share of the opaque shape... */
private const val MIN_PLATE_SHARE = 0.3f
/** ...and leave at least this share for the symbol. */
private const val MIN_SYMBOL_SHARE = 0.02f
/** Colors this far (per channel) outside the plate range are fully part of the symbol. */
private const val PLATE_SOFTNESS = 36
private const val LIGHT_PLATE_LUMA = 0.72f

private const val LUMA_BINS = 64
/** Pixels more transparent than this are overlays, fully part of the symbol from +[OVERLAY_FADE]. */
private const val OVERLAY_ALPHA = 0.15f
private const val OVERLAY_FADE = 0.25f
/** How close to the outline of the shape a pixel counts as the plate's edge. */
private const val EDGE_REACH = 2
/** Glyphs with less brightness variation than this are drawn flat in the tint. */
private const val FLAT_GLYPH_RANGE = 0.2f
/** Darkest parts of a multicolor glyph keep this much of the tint. */
private const val MIN_GLYPH_INTENSITY = 0.45f

/** Symbol content darker than this on average is lifted in Dark. */
private const val DARK_CONTENT_LUMA = 0.28f
private const val LIFT = 0.75f
private const val LIFT_BELOW = 0.5f
