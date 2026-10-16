package com.zfl.launcher.ui.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import com.zfl.launcher.ui.theme.Tokens

/** Blurred wallpaper shared by all glass surfaces. `null` means no access: a flat tint is drawn instead. */
val LocalGlassSource = compositionLocalOf<GlassSource?> { null }

/** User-adjustable look of glass surfaces: [tintAlpha] of the default white tint and the [edge] highlight. */
data class GlassStyle(val tintAlpha: Float = Tokens.GlassTint.alpha, val edge: Boolean = true)

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

/**
 * Draws a frosted glass background clipped to [shape]: the slice of the blurred
 * wallpaper right under this element, a light [tint] on top and a thin gradient
 * [stroke] that imitates the highlight on the glass edge. An unspecified [tint] follows
 * [LocalGlassStyle]; the edge is drawn only when the style enables it.
 *
 * The element tracks its own window position, so it stays correct while scrolling.
 * The wallpaper is painted with a positioned bitmap shader in a single path fill: no
 * clipping, no state and no recomposition when the element moves.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    stroke: Color = Tokens.GlassStroke,
): Modifier = this then GlassElement(shape, tint, stroke)

private data class GlassElement(val shape: Shape, val tint: Color, val stroke: Color) : ModifierNodeElement<GlassNode>() {
    override fun create(): GlassNode = GlassNode(shape, tint, stroke)

    override fun update(node: GlassNode) = node.update(shape, tint, stroke)

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["shape"] = shape
        properties["tint"] = tint
        properties["stroke"] = stroke
    }
}

private class GlassNode(
    private var shape: Shape,
    private var tint: Color,
    private var stroke: Color,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var windowPos = Offset.Zero

    // Outline and edge highlight, rebuilt only when the size or shape changes.
    private var outlineSize = Size.Unspecified
    private var outlineDirection = LayoutDirection.Ltr
    private val path = Path()
    private var strokeBrush: Brush? = null

    // Wallpaper shader, rebuilt only when the blurred wallpaper changes.
    private var shaderImage: ImageBitmap? = null
    private var shader: Shader? = null
    private var shaderBrush: Brush? = null
    private var filterColor: Color = Color.Unspecified
    private var tintFilter: ColorFilter? = null
    private val matrix = android.graphics.Matrix()

    fun update(shape: Shape, tint: Color, stroke: Color) {
        if (shape != this.shape) {
            this.shape = shape
            outlineSize = Size.Unspecified
        }
        this.tint = tint
        if (stroke != this.stroke) {
            this.stroke = stroke
            strokeBrush = null
        }
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val pos = coordinates.positionInWindow()
        if (pos != windowPos) {
            windowPos = pos
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (size != outlineSize || layoutDirection != outlineDirection) {
            path.reset()
            path.addOutline(shape.createOutline(size, layoutDirection, this))
            outlineSize = size
            outlineDirection = layoutDirection
            strokeBrush = null
        }
        // Read in the draw phase, so a new wallpaper (or blur turned off) redraws this node.
        val source = currentValueOf(LocalGlassSource)
        val style = currentValueOf(LocalGlassStyle)
        if (source != null) {
            val color = if (tint.isSpecified) tint else Color.White.copy(alpha = style.tintAlpha)
            if (color != filterColor || tintFilter == null) {
                filterColor = color
                tintFilter = ColorFilter.tint(color, BlendMode.SrcOver)
            }
            if (source.image !== shaderImage) {
                shaderImage = source.image
                shader = ImageShader(source.image, TileMode.Clamp, TileMode.Clamp)
                shaderBrush = ShaderBrush(shader!!)
            }
            // The shader maps the downscaled wallpaper onto the screen, shifted to this element.
            val k = source.downscale.toFloat()
            matrix.setScale(k, k)
            matrix.postTranslate(-windowPos.x, -windowPos.y)
            shader!!.setLocalMatrix(matrix)
            drawPath(path, shaderBrush!!, colorFilter = tintFilter)
        } else {
            drawPath(path, Tokens.GlassFallback)
        }
        if (!style.edge) {
            drawContent()
            return
        }
        val brush = strokeBrush ?: Brush.verticalGradient(
            0f to stroke,
            0.5f to stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeMidAlpha),
            1f to stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeBottomAlpha),
        ).also { strokeBrush = it }
        drawPath(path, brush, style = Stroke(Tokens.GlassStrokeWidth.toPx()))
        drawContent()
    }
}
