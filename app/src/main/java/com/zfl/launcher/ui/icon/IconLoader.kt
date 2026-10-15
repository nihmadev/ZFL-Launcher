package com.zfl.launcher.ui.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LauncherSettings
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.shape.iconOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Renders app icons into bitmaps of [baseSizePx] × the user's icon scale, already cut to the
 * user's icon shape (a squircle by default), so every icon
 * looks consistent and the UI draws them as plain images, with no per-frame clipping.
 *
 * - Adaptive icons: both layers are stretched so the visible 72/108 safe zone fills the square.
 * - Legacy icons: drawn with padding on a white plate, like iOS-style launchers do.
 *
 * Results are kept in an in-memory LRU cache.
 */
class IconLoader(
    private val context: Context,
    private val baseSizePx: Int,
    private val settings: LauncherSettings,
) {
    // Bounded by bytes: bitmaps grow with the icon size setting.
    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /**
     * Cache key of the icon currently shown for [app]. It changes when the user picks
     * a custom icon, so callers can use it as a `remember` key to reload.
     */
    fun cacheKey(app: AppInfo): String {
        val state = settings.state.value
        val icon = state.customIcons[app.key]?.let { stamp -> "${app.key}@$stamp" } ?: app.key
        // Size and shape are baked into the bitmap, so changing them renders new bitmaps.
        return "$icon#${sizePx(state)}#${state.iconCorner}"
    }

    private fun sizePx(state: SettingsState): Int = (baseSizePx * state.iconScale).roundToInt()

    /** Returns the icon if it is already rendered, without blocking. */
    fun cached(app: AppInfo): ImageBitmap? = cache.get(cacheKey(app))

    /** Returns the cached icon or renders it (custom image first, then the app's own icon) off the main thread. */
    suspend fun load(app: AppInfo): ImageBitmap {
        val key = cacheKey(app)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val density = context.resources.displayMetrics.densityDpi
            (loadCustom(app) ?: render(app.info.getIcon(density))).also { cache.put(key, it) }
        }
    }

    private fun loadCustom(app: AppInfo): ImageBitmap? {
        if (app.key !in settings.state.value.customIcons) return null
        val bitmap = BitmapFactory.decodeFile(settings.customIconFile(app.key).path) ?: return null
        val sizePx = sizePx(settings.state.value)
        val scaled = Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        // The decoded file may be immutable (and createScaledBitmap may return it as is).
        val icon = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
        return maskToSquircle(icon).asImageBitmap()
    }

    fun clear(): Unit = cache.evictAll()

    /** Renders any drawable into an icon-sized square bitmap. Also used for shortcut icons. */
    fun render(drawable: Drawable): ImageBitmap {
        val s = sizePx(settings.state.value)
        val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // Layers are 108x108 with a visible 72x72 center, so scale them by 1.5x.
            val inset = s / 4
            listOf(drawable.background, drawable.foreground).forEach { layer ->
                layer ?: return@forEach
                layer.setBounds(-inset, -inset, s + inset, s + inset)
                layer.draw(canvas)
            }
        } else {
            renderLegacy(drawable, canvas, s)
        }
        return maskToSquircle(bitmap).asImageBitmap()
    }

    /** Clears everything outside the icon shape, with antialiased edges. */
    private fun maskToSquircle(bitmap: Bitmap): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val outside = iconOutline(w, h, settings.state.value.iconCorner).asAndroidPath()
        outside.fillType = Path.FillType.INVERSE_WINDING
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        Canvas(bitmap).drawPath(outside, clear)
        return bitmap
    }

    /**
     * Legacy (non-adaptive) icons come with their own baked-in shape. Many OEM icons
     * (e.g. MIUI system apps) are already a filled rounded square: those are stretched
     * edge to edge so our squircle clip replaces their shape, instead of showing an
     * "icon inside an icon". Anything else (round, irregular, transparent) gets a white plate.
     */
    private fun renderLegacy(drawable: Drawable, canvas: Canvas, s: Int) {
        val source = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, s, s)
        drawable.draw(Canvas(source))
        val pixels = IntArray(s * s).also { source.getPixels(it, 0, s, 0, 0, s, s) }

        val box = opaqueBounds(pixels, s)
        if (box != null && fillsBounds(pixels, s, box)) {
            // Fill the background with the icon's own edge color to hide any slivers, then
            // scale the shape slightly past the edges so its rounded corners get clipped away.
            canvas.drawColor(pixels[(box.top + 2) * s + box.centerX()] or OPAQUE)
            val scale = FULL_BLEED_SCALE * s / maxOf(box.width(), box.height())
            val w = box.width() * scale
            val h = box.height() * scale
            val dst = RectF((s - w) / 2f, (s - h) / 2f, (s + w) / 2f, (s + h) / 2f)
            canvas.drawBitmap(source, box, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(Color.WHITE)
            val pad = (s * 0.12f).toInt()
            drawable.setBounds(pad, pad, s - pad, s - pad)
            drawable.draw(canvas)
        }
        source.recycle()
    }

    /** Bounding box of the mostly opaque pixels (ignores soft drop shadows). */
    private fun opaqueBounds(pixels: IntArray, s: Int): Rect? {
        var left = s; var top = s; var right = -1; var bottom = -1
        for (y in 0 until s) for (x in 0 until s) {
            if (pixels[y * s + x] ushr 24 > ALPHA_SOLID) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < left) null else Rect(left, top, right + 1, bottom + 1)
    }

    /**
     * True when the shape is a filled, roughly square tile. Probes points near the corners:
     * they are inside a rounded square (corner radius up to ~30%) but outside a circle.
     */
    private fun fillsBounds(pixels: IntArray, s: Int, box: Rect): Boolean {
        val aspect = box.width().toFloat() / box.height()
        if (aspect !in 0.9f..1.1f || box.width() < s / 2) return false
        val probes = listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.1f to 0.9f, 0.9f to 0.9f, 0.5f to 0.5f, 0.5f to 0.03f, 0.03f to 0.5f)
        return probes.all { (fx, fy) ->
            val x = (box.left + fx * (box.width() - 1)).toInt()
            val y = (box.top + fy * (box.height() - 1)).toInt()
            pixels[y * s + x] ushr 24 > ALPHA_SOLID
        }
    }

    private companion object {
        const val ALPHA_SOLID = 200
        /** About 300 icons at the default size. */
        const val MAX_CACHE_BYTES = 36 * 1024 * 1024
        const val OPAQUE = 0xFF000000.toInt()
        /** How far a legacy tile is scaled past the icon edges. */
        const val FULL_BLEED_SCALE = 1.06f
    }
}
