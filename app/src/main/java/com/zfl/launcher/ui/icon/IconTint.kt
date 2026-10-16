package com.zfl.launcher.ui.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.zfl.launcher.ui.theme.Tokens

/*
 * Draws icons in an [IconStyle] onto a square canvas of side `s`. Everything here happens
 * before IconLoader cuts the bitmap to the icon shape, so size, corners and mask are the same
 * in every style.
 *
 * - Adaptive icons: Dark swaps the background layer for the dark backdrop; Tinted uses the
 *   monochrome layer (Android 13+) or, without one, a glyph built from the foreground layer.
 *   A background with its own symbol on a plate keeps the symbol in both.
 * - Legacy icons and custom images: the plate the symbol sits on (a white tile, an OEM tile,
 *   a disk) is found by [findPlate]; Dark removes a light plate or dims a colored one, Tinted
 *   turns everything except the plate into a glyph.
 */

/** Adaptive layers are 108×108 with a visible 72×72 center: they are drawn 1.5× larger. */
private fun Drawable.setLayerBounds(s: Int) {
    val inset = s / 4
    setBounds(-inset, -inset, s + inset, s + inset)
}

/** An offscreen copy of one layer and its pixels, edited in place and then drawn. */
private class Layer(val bitmap: Bitmap, val pixels: IntArray) {
    fun drawTo(canvas: Canvas) {
        val s = bitmap.width
        bitmap.setPixels(pixels, 0, s, 0, 0, s, s)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
    }

    fun recycle(): Unit = bitmap.recycle()

    companion object {
        fun of(s: Int, draw: (Canvas) -> Unit): Layer {
            val bitmap = createBitmap(s, s)
            draw(Canvas(bitmap))
            return Layer(bitmap, IntArray(s * s).also { bitmap.getPixels(it, 0, s, 0, 0, s, s) })
        }
    }
}

internal fun drawAdaptive(icon: AdaptiveIconDrawable, canvas: Canvas, s: Int, style: IconStyle) {
    if (style == IconStyle.Plain) {
        listOf(icon.background, icon.foreground).forEach { layer ->
            layer ?: return@forEach
            layer.setLayerBounds(s)
            layer.draw(canvas)
        }
        return
    }
    drawBackdrop(canvas, s, style)
    if (style is IconStyle.Tinted) {
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
        if (mono != null) {
            // The monochrome layer only defines a shape (alpha): fill it with the tint.
            val glyph = Layer.of(s) { mono.setLayerBounds(s); mono.draw(it) }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(style.color, PorterDuff.Mode.SRC_IN) }
            canvas.drawBitmap(glyph.bitmap, 0f, 0f, paint)
            glyph.recycle()
            return
        }
    }
    val foreground = icon.foreground?.let { fg -> Layer.of(s) { fg.setLayerBounds(s); fg.draw(it) } }
    // Some apps paint their art on the background layer and leave the foreground (nearly) empty:
    // then both layers are processed together, like a legacy tile.
    if (foreground == null || coverage(foreground.pixels) < MIN_FOREGROUND) {
        foreground?.recycle()
        val all = Layer.of(s) { canvas -> listOfNotNull(icon.background, icon.foreground).forEach { it.setLayerBounds(s); it.draw(canvas) } }
        if (styleTile(all.pixels, s, style)) all.drawTo(canvas) else all.recycle()
        return
    }
    // The background is replaced by the backdrop, unless it carries a symbol of its own on a
    // plate (Google Translate draws its "文" there): then only the plate goes.
    icon.background?.let { bg ->
        val background = Layer.of(s) { bg.setLayerBounds(s); bg.draw(it) }
        val plate = findPlate(background.pixels, s)
        val keep = plate != null && when (style) {
            is IconStyle.Tinted -> tintGlyph(background.pixels, plate, style.color)
            else -> true.also { darkenLayer(background.pixels, plate, PLATE_DIM) }
        }
        if (keep) background.drawTo(canvas) else background.recycle()
    }
    val plate = findPlate(foreground.pixels, s)
    when (style) {
        is IconStyle.Tinted -> if (!tintGlyph(foreground.pixels, plate, style.color)) return foreground.recycle()
        // The foreground is drawn as is, except a white plate baked into it.
        else -> darkenLayer(foreground.pixels, plate?.takeIf { it.isLight })
    }
    foreground.drawTo(canvas)
}

/**
 * Legacy (non-adaptive) icons come with their own baked-in shape. Many OEM icons
 * (e.g. MIUI system apps) are already a filled rounded square: those are stretched
 * edge to edge so our squircle clip replaces their shape, instead of showing an
 * "icon inside an icon". Anything else (round, irregular, transparent) gets a plate:
 * white, or the dark backdrop in Dark and Tinted.
 */
internal fun drawLegacy(drawable: Drawable, canvas: Canvas, s: Int, style: IconStyle) {
    val source = Layer.of(s) { drawable.setBounds(0, 0, s, s); drawable.draw(it) }
    val pixels = source.pixels
    val box = opaqueBounds(pixels, s)
    if (box != null && fillsBounds(pixels, s, box)) {
        // Scale the tile slightly past the edges so its own rounded corners get clipped away.
        val scale = FULL_BLEED_SCALE * s / maxOf(box.width, box.height)
        val w = box.width * scale
        val h = box.height * scale
        val dst = RectF((s - w) / 2f, (s - h) / 2f, (s + w) / 2f, (s + h) / 2f)
        if (style == IconStyle.Plain) {
            // The icon's own edge color hides any slivers.
            canvas.drawColor(pixels[(box.top + 2) * s + box.centerX] or Color.BLACK)
        } else {
            drawBackdrop(canvas, s, style)
            if (!styleTile(pixels, s, style)) return source.recycle()
            source.bitmap.setPixels(pixels, 0, s, 0, 0, s, s)
        }
        canvas.drawBitmap(source.bitmap, Rect(box.left, box.top, box.right, box.bottom), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return
    }
    source.recycle()
    val pad = (s * LEGACY_PADDING).toInt()
    if (style == IconStyle.Plain) {
        canvas.drawColor(Color.WHITE)
        drawable.setBounds(pad, pad, s - pad, s - pad)
        drawable.draw(canvas)
        return
    }
    drawBackdrop(canvas, s, style)
    val padded = Layer.of(s) { drawable.setBounds(pad, pad, s - pad, s - pad); drawable.draw(it) }
    val plate = findPlate(padded.pixels, s)
    when (style) {
        is IconStyle.Tinted -> if (!tintGlyph(padded.pixels, plate, style.color)) return padded.recycle()
        // A round or irregular icon keeps its colors; only a white disk or plate goes.
        else -> darkenLayer(padded.pixels, plate?.takeIf { it.isLight })
    }
    padded.drawTo(canvas)
}

/** A custom image picked by the user ([image] is s×s): handled like a full-bleed legacy tile. */
internal fun drawCustom(image: Bitmap, canvas: Canvas, s: Int, style: IconStyle) {
    if (style != IconStyle.Plain) {
        drawBackdrop(canvas, s, style)
        val pixels = IntArray(s * s).also { image.getPixels(it, 0, s, 0, 0, s, s) }
        if (!styleTile(pixels, s, style)) return
        image.setPixels(pixels, 0, s, 0, 0, s, s)
    }
    canvas.drawBitmap(image, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
}

/** Dark or Tinted for a filled tile: its plate is dimmed (or removed if light) or dropped from the glyph. */
private fun styleTile(pixels: IntArray, s: Int, style: IconStyle): Boolean {
    val plate = findPlate(pixels, s)
    if (style is IconStyle.Tinted) return tintGlyph(pixels, plate, style.color)
    darkenLayer(pixels, plate, PLATE_DIM, UNPLATED_DIM)
    return true
}

/** The dark gradient behind Dark icons; Tinted icons get it slightly colored by the tint. */
private fun drawBackdrop(canvas: Canvas, s: Int, style: IconStyle) {
    var top = Tokens.IconDarkTop.toArgb()
    var bottom = Tokens.IconDarkBottom.toArgb()
    if (style is IconStyle.Tinted) {
        top = mix(top, style.color, TINTED_BACKDROP_TOP)
        bottom = mix(bottom, style.color, TINTED_BACKDROP_BOTTOM)
    }
    val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, s.toFloat(), s.toFloat(), paint)
}

/** How far a legacy tile is scaled past the icon edges. */
private const val FULL_BLEED_SCALE = 1.06f
/** Padding of a non-tile legacy icon on its plate, relative to the icon size. */
private const val LEGACY_PADDING = 0.12f
/** A foreground layer covering less of the icon than this carries no art. */
private const val MIN_FOREGROUND = 0.02f
/** Brightness kept by a colored OEM tile in Dark. */
private const val PLATE_DIM = 0.45f
/** Brightness kept by a tile without a recognizable plate (photos, multicolor art) in Dark. */
private const val UNPLATED_DIM = 0.8f
/** How much the tint colors the backdrop of Tinted icons, top and bottom. */
private const val TINTED_BACKDROP_TOP = 0.16f
private const val TINTED_BACKDROP_BOTTOM = 0.07f
