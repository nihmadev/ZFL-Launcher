package com.zfl.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.zfl.launcher.ui.icon.WHITE
import com.zfl.launcher.ui.icon.hsvToRgb
import com.zfl.launcher.ui.icon.luma
import com.zfl.launcher.ui.icon.mix
import com.zfl.launcher.ui.icon.rgbToHsv

/**
 * Interactive color of the UI: links, buttons, cursors, slider fills ([primary], with
 * [onPrimary] for text on it) and the "on" track of switches ([switchOn]). The defaults are
 * the iOS system blue and green; Dynamic Color derives all three from the wallpaper.
 */
@Immutable
data class AccentColors(val primary: Color, val onPrimary: Color, val switchOn: Color) {
    companion object {
        val Default: AccentColors = AccentColors(Tokens.Accent, Color.White, Tokens.SwitchOn)

        /**
         * Dynamic Color for a wallpaper whose main color is [wallpaperColor] (ARGB), or the
         * defaults when it is off, the colors are not known yet or the wallpaper has no hue.
         */
        fun resolve(dynamic: Boolean, wallpaperColor: Int?): AccentColors {
            if (!dynamic) return Default
            val accent = dynamicAccent(wallpaperColor) ?: return Default
            val onAccent = if (luma(accent) > ON_ACCENT_DARK_LUMA) Color.Black else Color.White
            return AccentColors(Color(accent), onAccent, Color(accent))
        }
    }
}

val LocalAccent = staticCompositionLocalOf { AccentColors.Default }

/** Below this saturation the wallpaper is grey: it has no hue worth using. */
private const val MIN_WALLPAPER_SATURATION = 0.12f
private const val MIN_ACCENT_SATURATION = 0.5f
private const val MAX_ACCENT_SATURATION = 0.8f
/** The accent is text and controls on dark glass: it keeps at least this luminance. */
internal const val MIN_ACCENT_LUMA = 0.4f
/** Accents brighter than this get black text on top (yellow, light green). */
private const val ON_ACCENT_DARK_LUMA = 0.6f

/**
 * Accent (opaque ARGB) for a wallpaper color: its hue, vivid and bright enough to stand out
 * on the dark glass of the launcher; `null` for no color or a hue-less (grey) wallpaper.
 */
fun dynamicAccent(wallpaperColor: Int?): Int? {
    wallpaperColor ?: return null
    val hsv = rgbToHsv(wallpaperColor)
    if (hsv[1] < MIN_WALLPAPER_SATURATION) return null
    val color = hsvToRgb(hsv[0], hsv[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION), 1f)
    val l = luma(color)
    if (l >= MIN_ACCENT_LUMA) return color
    // Deep blues and purples: mix toward white just enough to reach the minimum luminance.
    return mix(color, WHITE, (MIN_ACCENT_LUMA - l) / (1f - l))
}
