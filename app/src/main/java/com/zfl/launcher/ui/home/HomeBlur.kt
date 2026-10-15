package com.zfl.launcher.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.zfl.launcher.ui.glass.WallpaperBlur

/*
 * Blur of the Home Screen behind folders, menus and search.
 *
 * Animating a blur radius re-blurs the whole screen on every frame, which mid-range GPUs
 * can't do at 120 Hz. Instead the home content is recorded once into a layer and drawn
 * twice: sharp ([recordHomeContent]) and overlaid with a blurred, opaque backdrop fading
 * in with alpha p ([BlurredHomeCopy]). The blurred copy includes the wallpaper, which is
 * otherwise drawn by the system behind the transparent launcher window. Only alpha is
 * animated; the fixed-radius effect can reuse unchanged content.
 */

/** Records the foreground; fades it only when no opaque wallpaper can cover the sharp copy. */
fun Modifier.recordHomeContent(layer: GraphicsLayer, progress: State<Float>, hasWallpaper: Boolean): Modifier = this
    .graphicsLayer {
        alpha = if (hasWallpaper) 1f else 1f - progress.value.coerceIn(0f, 1f)
        // Fading each draw call avoids an extra full-screen offscreen pass.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
    .drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

/** Blurs [wallpaper] and the recorded foreground together, then fades in the complete backdrop. */
@Composable
fun BlurredHomeCopy(
    layer: GraphicsLayer,
    wallpaper: ImageBitmap?,
    progress: State<Float>,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // One instance: an unchanged effect lets the GPU reuse the blurred result between frames.
    val effect = remember(radius, density) {
        val px = with(density) { radius.toPx() }
        BlurEffect(px, px, TileMode.Clamp)
    }
    Spacer(
        modifier
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                alpha = p
                // Without the effect a hidden copy costs nothing: an invisible node isn't drawn.
                renderEffect = if (p > 0f) effect else null
                clip = true
            }
            .drawBehind {
                // The preview is center-cropped to the full display at 1/3 scale. Keep that
                // mapping when the keyboard resizes the window: crop, never squash the image.
                if (wallpaper != null) {
                    drawImage(
                        wallpaper,
                        dstSize = IntSize(
                            wallpaper.width * WallpaperBlur.PREVIEW_DOWNSCALE,
                            wallpaper.height * WallpaperBlur.PREVIEW_DOWNSCALE,
                        ),
                    )
                }
                drawLayer(layer)
            },
    )
}
