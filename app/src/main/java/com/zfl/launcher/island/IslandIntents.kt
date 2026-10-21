package com.zfl.launcher.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build

/** Sends an app's own intent from the visible Island window; false when the app refused it. */
internal fun sendFromIsland(context: Context, intent: PendingIntent): Boolean {
    val options = ActivityOptions.makeBasic()
    if (Build.VERSION.SDK_INT >= 36) {
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
    } else if (Build.VERSION.SDK_INT >= 34) {
        @Suppress("DEPRECATION") // Replaced by ALLOW_IF_VISIBLE only on Android 16.
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
    return runCatching { intent.send(context, 0, null, null, null, null, options.toBundle()) }.isSuccess
}

/**
 * The artwork's most vivid color, lifted so it reads on the dark card, like the tint iOS takes
 * from album art. Gray artwork gives null. Runs on the media worker over a small thumbnail.
 */
internal fun islandAccent(bitmap: Bitmap): Int? {
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var weight = 0.0
    val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / ACCENT_SAMPLES)
    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
        val pixel = bitmap.getPixel(x, y)
        Color.colorToHSV(pixel, hsv)
        val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
        r += Color.red(pixel) * w
        g += Color.green(pixel) * w
        b += Color.blue(pixel) * w
        weight += w
    }
    if (weight < MIN_WEIGHT) return null
    Color.colorToHSV(Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt()), hsv)
    if (hsv[1] < MIN_SATURATION) return null
    hsv[1] = hsv[1].coerceIn(ACCENT_SATURATION_MIN, ACCENT_SATURATION_MAX)
    hsv[2] = hsv[2].coerceAtLeast(ACCENT_VALUE)
    return Color.HSVToColor(hsv)
}

private const val ACCENT_SAMPLES = 24
private const val MIN_WEIGHT = 0.5
private const val MIN_SATURATION = 0.12f
private const val ACCENT_SATURATION_MIN = 0.45f
private const val ACCENT_SATURATION_MAX = 0.8f
private const val ACCENT_VALUE = 0.88f
