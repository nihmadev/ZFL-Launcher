package com.zfl.launcher.ui.shape

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * A [width] stroke just inside [shape]'s outline, drawn over the content, like
 * [androidx.compose.foundation.border].
 *
 * Use it for [ContinuousRoundedShape] and [IconShape]: for such a generic outline `border`
 * rasterizes the stroke on the CPU into an offscreen bitmap per element and uploads it as a
 * texture, which made long lists of squircle rows stutter as rows scrolled in. This draws the
 * outline of the inset size as a path on the GPU instead; the path is rebuilt only on a resize.
 */
fun Modifier.shapeBorder(width: Dp, brush: Brush, shape: Shape): Modifier = drawWithCache {
    val w = width.toPx()
    val path = Path()
    if (w > 0f && size.width > w && size.height > w) {
        path.addOutline(shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this))
        path.translate(Offset(w / 2f, w / 2f))
    }
    val stroke = Stroke(w)
    onDrawWithContent {
        drawContent()
        if (!path.isEmpty) drawPath(path, brush, style = stroke)
    }
}

/** [shapeBorder] in a single [color]; a transparent one draws nothing. */
fun Modifier.shapeBorder(width: Dp, color: Color, shape: Shape): Modifier =
    if (color.alpha == 0f) this else shapeBorder(width, SolidColor(color), shape)
