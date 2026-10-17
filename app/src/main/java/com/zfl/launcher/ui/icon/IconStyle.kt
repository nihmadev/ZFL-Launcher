package com.zfl.launcher.ui.icon

import com.zfl.launcher.settings.SettingsState
import kotlin.math.abs
import kotlin.math.roundToInt

/** How app icons look, like the iOS 18 "Customize" sheet. Stored by [name]. */
enum class IconAppearance {
    /** The apps' own colors. */
    Default,
    /** Dark backdrop, bright foreground. */
    Dark,
    /** Monochrome glyphs in one tint color on a dark backdrop. */
    Tinted,
    /** [Default] in the light system theme, [Dark] in the dark one. */
    Auto;

    /** The appearance actually drawn: resolves [Auto] against the system night mode. */
    fun effective(nightMode: Boolean): IconAppearance = when (this) {
        Auto -> if (nightMode) Dark else Default
        else -> this
    }

    companion object {
        /** Unknown or missing values fall back to [Default]. */
        fun fromStored(name: String?): IconAppearance = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** A concrete look baked into an icon bitmap ([IconAppearance] with Auto and the tint color resolved). */
sealed interface IconStyle {
    /** Part of the icon cache key. */
    val key: String

    data object Plain : IconStyle {
        override val key: String = "p"
    }

    data object Dark : IconStyle {
        override val key: String = "d"
    }

    /** [color] is opaque ARGB. */
    data class Tinted(val color: Int) : IconStyle {
        override val key: String = "t" + Integer.toHexString(color)
    }
}

/** Everything that is baked into icon bitmaps; a new value means new bitmaps. */
data class IconSpec(
    /** The general icon size (Settings › Icons); smaller icons get a thinner edge relative to it. */
    val sizePx: Int,
    val corner: Float,
    val style: IconStyle,
    /** Whether [style] also applies to custom icons picked by the user (otherwise they stay [IconStyle.Plain]). */
    val styleCustomIcons: Boolean,
    /** The glass edge highlight along the icon outline. */
    val edge: Boolean,
) {
    /** Style of one icon: custom icons may opt out of Dark and Tinted. */
    fun styleFor(custom: Boolean): IconStyle = if (custom && !styleCustomIcons) IconStyle.Plain else style

    /** Cache keys made for this spec end with one of these. */
    fun keySuffixes(): Set<String> = setOf(suffix(style), suffix(IconStyle.Plain))

    internal fun suffix(style: IconStyle): String = "#$sizePx#$corner#${if (edge) "e" else ""}${style.key}"
}

/**
 * Cache key of one icon bitmap of [sizePx]. [version] changes when the app is updated,
 * [customStamp] when the user picks another custom icon, so neither can show a stale bitmap.
 */
fun iconCacheKey(appKey: String, version: Int, customStamp: Long?, sizePx: Int, spec: IconSpec): String {
    val custom = if (customStamp != null) "@$customStamp" else ""
    return "$appKey~$version$custom/$sizePx${spec.suffix(spec.styleFor(customStamp != null))}"
}

/** What the launcher knows about the wallpaper: [color] is `null` when it has none (e.g. some live wallpapers). */
data class WallpaperTint(val color: Int?)

/**
 * Resolves the settings into the bitmap spec. Returns `null` while the wallpaper color is
 * still loading ([wallpaper] is `null`) and the icons depend on it, so nothing is rendered twice.
 */
fun SettingsState.iconSpec(baseSizePx: Int, nightMode: Boolean, wallpaper: WallpaperTint?): IconSpec? {
    val style = when (iconAppearance.effective(nightMode)) {
        IconAppearance.Dark -> IconStyle.Dark
        IconAppearance.Tinted -> {
            val base = if (iconTintFromWallpaper) {
                (wallpaper ?: return null).color ?: DEFAULT_ICON_TINT
            } else {
                iconTintColor
            }
            IconStyle.Tinted(tintColor(base, iconTintSaturation))
        }
        else -> IconStyle.Plain
    }
    return IconSpec((baseSizePx * iconScale).roundToInt(), iconCorner, style, tintCustomIcons, iconEdge)
}

// region Tint color

/** Colors offered for the tint; the first one is the default. Stored as plain ARGB values. */
val ICON_TINT_PALETTE: List<Int> = listOf(
    0xFFFFC94D.toInt(), // amber
    0xFFFF8A3D.toInt(), // orange
    0xFFFF5A5F.toInt(), // red
    0xFFFF6FB5.toInt(), // pink
    0xFFB07CFF.toInt(), // purple
    0xFF4C9BFF.toInt(), // blue
    0xFF3FD0C9.toInt(), // teal
    0xFF5ED37A.toInt(), // green
)

/** Tint used when the user's choice is unavailable (no wallpaper colors). */
val DEFAULT_ICON_TINT: Int = ICON_TINT_PALETTE.first()

/** Below this saturation a base color has no meaningful hue: the tint becomes neutral white. */
private const val ACHROMATIC = 0.08f

/** Glyphs are drawn on a near-black backdrop, so the tint keeps at least this luminance. */
internal const val MIN_TINT_LUMA = 0.45f

/**
 * Final tint: the hue of [base] at [saturation] (0 = white, 1 = vivid), at full brightness and
 * lightened if needed to stay readable on the dark backdrop. A hue-less base gives white.
 */
fun tintColor(base: Int, saturation: Float): Int {
    val hsv = rgbToHsv(base)
    val s = if (hsv[1] < ACHROMATIC) 0f else saturation.coerceIn(0f, 1f)
    val color = hsvToRgb(hsv[0], s, 1f)
    val luma = luma(color)
    if (luma >= MIN_TINT_LUMA) return color
    // Mix toward white just enough to reach the minimum luminance (luma is linear in the mix).
    return mix(color, WHITE, (MIN_TINT_LUMA - luma) / (1f - luma))
}

/** Hue in degrees 0..360, saturation and value 0..1. */
fun rgbToHsv(color: Int): FloatArray {
    val r = red(color) / 255f
    val g = green(color) / 255f
    val b = blue(color) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}

/** Opaque ARGB from hue 0..360, saturation and value 0..1. */
fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
    val c = value * saturation
    val h = (hue.mod(360f)) / 60f
    val x = c * (1f - abs(h.mod(2f) - 1f))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = value - c
    return argb(255, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
}

// endregion

// region Color math (plain Kotlin, so it runs in JVM tests)

internal const val WHITE: Int = -1

internal fun alpha(c: Int): Int = c ushr 24
internal fun red(c: Int): Int = (c shr 16) and 0xFF
internal fun green(c: Int): Int = (c shr 8) and 0xFF
internal fun blue(c: Int): Int = c and 0xFF

internal fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/** Perceived brightness 0..1 (Rec. 709 weights on sRGB values; good enough for icons). */
internal fun luma(c: Int): Float = (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f

/** Linear blend of the RGB channels, keeping the alpha of [from]. */
internal fun mix(from: Int, to: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(a: Int, b: Int) = (a + (b - a) * k).roundToInt()
    return argb(alpha(from), ch(red(from), red(to)), ch(green(from), green(to)), ch(blue(from), blue(to)))
}

// endregion
