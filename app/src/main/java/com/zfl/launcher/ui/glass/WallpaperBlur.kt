package com.zfl.launcher.ui.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Environment
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Blurred copy of the wallpaper, laid out exactly like the system draws it on screen
 * but [downscale] times smaller. Glass surfaces sample their region from it.
 */
class GlassSource(val image: ImageBitmap, val downscale: Int)

/**
 * Loads and blurs the system wallpaper for glass surfaces.
 *
 * The wallpaper is drawn by the system behind our transparent window, so we can't blur
 * "what's behind a view" directly. Instead we fetch the bitmap once, blur a small copy
 * and let every glass element draw its own slice of it (see [glass]).
 */
object WallpaperBlur {
    const val DOWNSCALE = 8
    /** Sharp wallpaper shared by previews and the full home backdrop; bounds bitmap memory. */
    const val PREVIEW_DOWNSCALE: Int = 3
    /** Blur radius in downscaled pixels (~56 screen px). */
    private const val RADIUS = 7
    private const val SATURATION = 1.6f

    /** On Android 13+ the wallpaper bitmap is only readable with "All files access". */
    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** Changes whenever the user sets a new wallpaper; -1 if unknown. */
    fun wallpaperId(context: Context): Int = runCatching {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

    /** Returns `null` when the wallpaper is not readable (no permission or live wallpaper). */
    suspend fun load(context: Context): GlassSource? = withContext(Dispatchers.Default) {
        val small = render(context, DOWNSCALE, SATURATION) ?: return@withContext null
        boxBlur(small, RADIUS, passes = 3)
        GlassSource(small.asImageBitmap(), DOWNSCALE)
    }

    /**
     * Sharp wallpaper as it looks on the home screen, [downscale] times smaller.
     * Used by home backdrop blur and the live preview in settings. `null` when not readable.
     */
    suspend fun loadPreview(context: Context, downscale: Int = PREVIEW_DOWNSCALE): ImageBitmap? = withContext(Dispatchers.Default) {
        render(context, downscale, saturation = 1f)?.asImageBitmap()
    }

    /** Draws the wallpaper center-cropped to the screen, like the system does, [downscale] times smaller. */
    private fun render(context: Context, downscale: Int, saturation: Float): Bitmap? {
        if (!hasAccess()) return null
        val drawable = runCatching { WallpaperManager.getInstance(context).drawable }.getOrNull() ?: return null

        val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val sw = screen.width()
        val sh = screen.height()
        val ww = drawable.intrinsicWidth.takeIf { it > 0 } ?: sw
        val wh = drawable.intrinsicHeight.takeIf { it > 0 } ?: sh

        val outW = sw / downscale
        val outH = sh / downscale
        val scale = max(sw.toFloat() / ww, sh.toFloat() / wh) / downscale
        val dx = (outW - ww * scale) / 2f
        val dy = (outH - wh * scale) / 2f

        val small = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(small).apply {
            translate(dx, dy)
            scale(scale, scale)
            drawable.setBounds(0, 0, ww, wh)
            if (saturation != 1f) {
                drawable.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
            }
            drawable.draw(this)
            drawable.colorFilter = null
        }
        return small
    }

    /** Three box blur passes approximate a gaussian. Cheap because the bitmap is tiny. */
    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurLine(src, tmp, w, h, radius, horizontal = true)
            blurLine(tmp, src, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun blurLine(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = input[idx(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                output[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = input[idx(i + r + 1)]
                val rem = input[idx(i - r)]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
