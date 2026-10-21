package com.zfl.launcher.ui.icon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader not provided") }

/**
 * Just the icon image (the bitmap is already cut to a squircle and styled by [IconLoader]).
 * Loads asynchronously, rendered at the size it is drawn. [dimmed] darkens it the way iOS
 * does while the icon is pressed.
 *
 * @param renderSize size of the bitmap when [size] changes every frame (a slider preview):
 *   the saved size, so dragging doesn't render a bitmap per frame.
 */
@Composable
fun AppIconImage(
    app: AppInfo,
    modifier: Modifier = Modifier,
    size: Dp = LocalSettingsState.current.iconSize,
    dimmed: Boolean = false,
    renderSize: Dp = size,
) {
    val loader = LocalIconLoader.current
    val sizePx = with(LocalDensity.current) { renderSize.roundToPx() }.coerceAtLeast(1)
    // Changes with a custom icon, an app update, the size, shape or appearance, which
    // triggers a reload. Reading it subscribes this scope only, not its parents.
    val cacheKey = loader.cacheKey(app, sizePx)
    val slot = remember(app.key) {
        val cached = loader.cached(app, sizePx)
        IconSlot(if (cached != null) cacheKey else null, cached)
    }
    // An icon already in the cache starts no effect: rows scrolling in stay cheap to compose.
    if (cacheKey != null && cacheKey != slot.key) {
        LaunchedEffect(cacheKey) {
            // The previous bitmap stays visible until the new one is ready: no blink on a restyle.
            slot.bitmap = loader.cached(app, sizePx) ?: loader.load(app, sizePx) ?: return@LaunchedEffect
            slot.key = cacheKey
        }
    }
    val label = app.label
    Spacer(
        modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Image
            }
            // The bitmap is read in the draw phase: loading it only redraws, never recomposes.
            .drawBehind {
                val image = slot.bitmap ?: return@drawBehind
                drawImage(
                    image = image,
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                    // SrcAtop keeps the squircle's transparent corners untouched.
                    colorFilter = if (dimmed) PressedDim else null,
                )
            },
    )
}

/** Bitmap shown by [AppIconImage] and the cache key it was loaded for; the bitmap is snapshot state. */
private class IconSlot(var key: String?, bitmap: ImageBitmap?) {
    var bitmap: ImageBitmap? by mutableStateOf(bitmap)
}

private val PressedDim = ColorFilter.tint(Color.Black.copy(alpha = 0.3f), BlendMode.SrcAtop)

/** Name under an icon at [scale] × the label size, in sp, so the system font size applies too. */
@Composable
fun IconLabel(text: String, scale: Float = LocalSettingsState.current.labelScale) {
    Text(
        text = text,
        style = iconLabelStyle(scale),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun iconLabelStyle(scale: Float): TextStyle = TextStyle(
    color = Tokens.TextPrimary,
    fontSize = Tokens.LabelSize * scale,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 4f),
)

/** Height of one line of [IconLabel] at [scale], measured with the current density and font scale. */
@Composable
fun rememberLabelLineHeight(scale: Float): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    return remember(scale, density) {
        with(density) { measurer.measure(LABEL_SAMPLE, iconLabelStyle(scale), maxLines = 1).size.height.toDp() }
    }
}

/** Ascenders and descenders, so the line is as tall as any name. */
private const val LABEL_SAMPLE = "Ág"
