package com.zfl.launcher.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.zfl.launcher.settings.PageTransition
import kotlin.math.abs

/**
 * Applies a page swipe effect to one page of a horizontal pager.
 *
 * [offset] is the page position relative to the settled page, in pages:
 * `0` = fully on screen, `1` = one page to the right, `-1` = one page to the left.
 * It is a lambda so it's read in the draw phase and scrolling doesn't recompose.
 */
fun Modifier.pageTransition(effect: PageTransition, offset: () -> Float): Modifier =
    if (effect == PageTransition.Default) this else graphicsLayer {
        val o = offset()
        val distance = abs(o).coerceAtMost(1f)
        when (effect) {
            PageTransition.Default -> Unit

            // Pages swing around their bottom edge like cards on a wheel.
            PageTransition.Rotation -> {
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationZ = o * 20f
            }

            // Each page stays in place and flips over its vertical axis.
            PageTransition.Flip -> {
                translationX = -o * size.width
                rotationY = o * 180f
                cameraDistance = 12f * density
                alpha = if (distance < 0.5f) 1f else 0f
            }

            // The page shrinks and fades a bit while it slides away.
            PageTransition.ZoomIn -> {
                val scale = 1f - 0.22f * distance
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.35f * distance
            }

            // Pages cross-fade in place instead of sliding.
            PageTransition.Alpha -> {
                translationX = -o * size.width
                alpha = 1f - distance
            }
        }
    }
