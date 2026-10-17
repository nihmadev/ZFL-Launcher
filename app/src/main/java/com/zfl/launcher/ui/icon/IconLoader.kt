package com.zfl.launcher.ui.icon

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LauncherSettings
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.shape.iconOutline
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders app icons into bitmaps of [baseSizePx] × the user's icon scale, already cut to the
 * user's icon shape (a squircle by default), with the glass edge highlight if enabled, and
 * drawn in the user's [IconAppearance], so every
 * icon looks consistent and the UI draws them as plain images, with no per-frame clipping or filters.
 *
 * The current [IconSpec] (size, shape, resolved style) follows the settings, the system night
 * mode (for Auto) and the wallpaper colors (for Tinted "from wallpaper"). It is snapshot state:
 * [AppIconImage] reads [cacheKey] and reloads only when its own key changes. Rendering happens
 * off the main thread ([drawAdaptive], [drawLegacy], [drawCustom]).
 *
 * Results are kept in an in-memory LRU cache bounded by bytes. Bitmaps of an outdated spec are
 * dropped as soon as the spec changes.
 */
class IconLoader(
    activity: ComponentActivity,
    private val baseSizePx: Int,
    private val settings: LauncherSettings,
) {
    private val resources = activity.resources

    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    // Inputs of the spec; written on the main thread only.
    @Volatile private var prefs: SettingsState = settings.state.value
    private var nightMode: Boolean = activity.resources.configuration.isNightMode()
    private var wallpaper: WallpaperTint? by mutableStateOf(null)

    /** `null` while it waits for the wallpaper colors. Snapshot state, see the class docs. */
    private var spec: IconSpec? by mutableStateOf(prefs.iconSpec(baseSizePx, nightMode, wallpaper))
    private var customIcons: Map<String, Long> by mutableStateOf(prefs.customIcons)

    /** Key suffixes of the current spec; renders of an older spec are not cached. */
    @Volatile private var liveSuffixes: Set<String> = spec?.keySuffixes().orEmpty()

    /** Tint the "from wallpaper" option gives at [saturation]; `null` while the wallpaper colors are loading. */
    fun wallpaperTint(saturation: Float): Int? = wallpaper?.let { tintColor(it.color ?: DEFAULT_ICON_TINT, saturation) }

    /** Main color of the wallpaper (ARGB); `null` while loading or when it has none. Snapshot state. */
    val wallpaperColor: Int? get() = wallpaper?.color

    /** Style of the icons drawn now; `null` while it waits for the wallpaper colors. Snapshot state. */
    val iconStyle: IconStyle? get() = spec?.style

    private val colorsListener = WallpaperManager.OnColorsChangedListener { colors: WallpaperColors?, which: Int ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) setWallpaper(colors)
    }
    private val configListener = Consumer<Configuration> { config ->
        nightMode = config.isNightMode()
        refresh()
    }

    init {
        val wallpapers = WallpaperManager.getInstance(activity)
        activity.lifecycleScope.launch {
            settings.state.collect { state ->
                prefs = state
                customIcons = state.customIcons
                refresh()
            }
        }
        activity.lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) {
                runCatching { wallpapers.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
            }
            // A change reported by the listener in the meantime is newer.
            if (wallpaper == null) setWallpaper(colors)
        }
        // Only the home activity handles uiMode itself; others are recreated with a new loader.
        activity.addOnConfigurationChangedListener(configListener)
        wallpapers.addOnColorsChangedListener(colorsListener, Handler(Looper.getMainLooper()))
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                activity.removeOnConfigurationChangedListener(configListener)
                wallpapers.removeOnColorsChangedListener(colorsListener)
            }
        })
    }

    private fun setWallpaper(colors: WallpaperColors?) {
        wallpaper = WallpaperTint(colors?.primaryColor?.toArgb())
        refresh()
    }

    /** Recomputes the spec; on a change, drops the bitmaps that no longer match it. */
    private fun refresh() {
        val next = prefs.iconSpec(baseSizePx, nightMode, wallpaper)
        if (next == spec) return
        spec = next
        // While waiting for the wallpaper, the shown bitmaps stay as they are.
        val keep = next?.keySuffixes() ?: return
        liveSuffixes = keep
        cache.snapshot().keys.forEach { key -> if (keep.none(key::endsWith)) cache.remove(key) }
    }

    /**
     * Cache key of the icon currently shown for [app], or `null` while the spec is not known yet.
     * It changes with a custom icon, an app update or any setting baked into the bitmap, so
     * callers can use it as an effect key to reload.
     */
    fun cacheKey(app: AppInfo): String? {
        val spec = spec ?: return null
        return iconCacheKey(app.key, app.iconVersion, customIcons[app.key], spec)
    }

    /** Returns the icon if it is already rendered, without blocking. */
    fun cached(app: AppInfo): ImageBitmap? = cacheKey(app)?.let(cache::get)

    /**
     * Returns the cached icon or renders it (custom image first, then the app's own icon) off
     * the main thread. `null` while the spec is not known yet.
     */
    suspend fun load(app: AppInfo): ImageBitmap? {
        val spec = spec ?: return null
        val stamp = customIcons[app.key]
        val key = iconCacheKey(app.key, app.iconVersion, stamp, spec)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val density = resources.displayMetrics.densityDpi
            val icon = stamp?.let { loadCustom(app, spec) } ?: render(app.info.getIcon(density), spec)
            if (liveSuffixes.any(key::endsWith)) cache.put(key, icon)
            icon
        }
    }

    private fun loadCustom(app: AppInfo, spec: IconSpec): ImageBitmap? {
        val bitmap = BitmapFactory.decodeFile(settings.customIconFile(app.key).path) ?: return null
        val s = spec.sizePx
        val scaled = bitmap.scale(s, s)
        // The decoded file may be immutable (and scaling to its own size returns it as is).
        var icon = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
        val style = spec.styleFor(custom = true)
        if (style != IconStyle.Plain) {
            val styled = createBitmap(s, s)
            drawCustom(icon, Canvas(styled), s, style)
            icon = styled
        }
        return finish(icon, spec)
    }

    fun clear(): Unit = cache.evictAll()

    /** Renders any drawable into an icon-sized square bitmap in the current style. Also used for shortcut icons. */
    fun render(drawable: Drawable): ImageBitmap =
        render(drawable, spec ?: prefs.iconSpec(baseSizePx, nightMode, WallpaperTint(null)) ?: error("spec without wallpaper"))

    private fun render(drawable: Drawable, spec: IconSpec): ImageBitmap {
        val s = spec.sizePx
        val bitmap = createBitmap(s, s)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawAdaptive(drawable, canvas, s, spec.style)
        } else {
            drawLegacy(drawable, canvas, s, spec.style)
        }
        return finish(bitmap, spec)
    }

    /** Cuts the icon to its shape and adds the edge highlight. */
    private fun finish(bitmap: Bitmap, spec: IconSpec): ImageBitmap {
        maskToSquircle(bitmap, spec.corner)
        if (spec.edge) drawEdge(bitmap, spec.corner)
        return bitmap.asImageBitmap()
    }

    /** Clears everything outside the icon shape, with antialiased edges. */
    private fun maskToSquircle(bitmap: Bitmap, corner: Float) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val outside = iconOutline(w, h, corner).asAndroidPath()
        outside.fillType = Path.FillType.INVERSE_WINDING
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        Canvas(bitmap).drawPath(outside, clear)
    }

    /**
     * The same top-lit stroke glass surfaces have ([com.zfl.launcher.ui.glass.glass]), kept just
     * inside the outline so the icon itself and its size stay untouched.
     */
    private fun drawEdge(bitmap: Bitmap, corner: Float) {
        val width = Tokens.GlassStrokeWidth.value * resources.displayMetrics.density
        val side = bitmap.width - width
        val path = iconOutline(side, side, corner).asAndroidPath().apply { offset(width / 2, width / 2) }
        val stroke = Tokens.GlassStroke
        val colors = intArrayOf(
            stroke.toArgb(),
            stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeMidAlpha).toArgb(),
            stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeBottomAlpha).toArgb(),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            shader = LinearGradient(0f, 0f, 0f, bitmap.height.toFloat(), colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawPath(path, paint)
    }

    private companion object {
        /** About 300 icons at the default size. */
        const val MAX_CACHE_BYTES = 36 * 1024 * 1024
    }
}

private fun Configuration.isNightMode(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
