package com.zfl.launcher.ui.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.theme.Tokens

/** Blurred wallpaper shared by all glass surfaces. `null` means no access: a flat tint is drawn instead. */
val LocalGlassSource = compositionLocalOf<GlassSource?> { null }

/**
 * User-adjustable look of glass surfaces: [tintAlpha] of the default white tint, the [edge]
 * highlight, the rendering [mode] and, for Liquid glass, the [refraction] strength (0..1).
 * [mode] is a request: Android 12 and a shader that failed to compile always get Frosted.
 */
data class GlassStyle(
    val tintAlpha: Float = Tokens.GlassTint.alpha,
    val edge: Boolean = true,
    val mode: GlassMode = GlassMode.Frosted,
    val refraction: Float = SettingsState.DEFAULT_GLASS_REFRACTION,
)

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

/** The glass look chosen in these settings; [tintAlpha] overrides the brightness while a slider is dragged. */
fun SettingsState.glassStyle(tintAlpha: Float = glassTint, refraction: Float = glassRefraction): GlassStyle =
    GlassStyle(tintAlpha, glassEdge, glassMode, refraction)

/** Direction of the light on Liquid glass; the default one never moves (light from the top). */
val LocalGlassLight = staticCompositionLocalOf { GlassLight() }

/**
 * Lens of a Liquid glass surface and whether it reacts to touch. Frosted glass ignores it.
 */
enum class GlassProfile(internal val touch: Boolean) {
    /** Dock, cards, sheets: a wide rim, no touch reaction (taps belong to the content). */
    Panel(touch = false),
    /** Capsule buttons and fields: the highlight flashes where touched and the glass gives a little. */
    Button(touch = true),
    /** Small round buttons: like [Button] with a thinner lens. */
    SmallButton(touch = true),
    /** Icon-sized tiles (folders): a thin rim, no touch reaction. */
    Tile(touch = false),
}

/**
 * Draws a glass background clipped to [shape] and the element's content on top of it.
 *
 * Frosted (and the fallback for Android 12 or a failed shader): the slice of the blurred
 * wallpaper right under this element, a light [tint] on top and a thin gradient [stroke]
 * that imitates the highlight on the glass edge.
 *
 * Liquid (Android 13+): a shader computes a thick lens from the wallpaper under the element:
 * the rim refracts it, the body is blurred and tinted, a Fresnel highlight in the [stroke]
 * color follows [LocalGlassLight], and [profile] sets the lens and the touch reaction.
 *
 * An unspecified [tint] follows [LocalGlassStyle] (and, when Liquid, the brightness of the
 * background); the edge is drawn only when the style enables it. Without wallpaper access
 * both modes draw the same flat tint. The element tracks its own window position, so it
 * stays correct while scrolling, with no state and no recomposition when it moves.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    stroke: Color = Tokens.GlassStroke,
    profile: GlassProfile = GlassProfile.Panel,
): Modifier = this then GlassElement(shape, tint, stroke, profile)

private data class GlassElement(
    val shape: Shape,
    val tint: Color,
    val stroke: Color,
    val profile: GlassProfile,
) : ModifierNodeElement<GlassNode>() {
    override fun create(): GlassNode = GlassNode(shape, tint, stroke, profile)

    override fun update(node: GlassNode) = node.update(shape, tint, stroke, profile)

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["shape"] = shape
        properties["tint"] = tint
        properties["stroke"] = stroke
        properties["profile"] = profile
    }
}

/**
 * Frosted glass: the blurred wallpaper painted with a positioned bitmap shader in a single
 * path fill, a tint color filter and a gradient edge. Caches rebuild only when the wallpaper,
 * the tint or the stroke change.
 */
internal class FrostedGlass {
    private var shaderImage: ImageBitmap? = null
    private var shader: Shader? = null
    private var shaderBrush: Brush? = null
    private var filterColor: Color = Color.Unspecified
    private var tintFilter: ColorFilter? = null
    private val matrix = android.graphics.Matrix()
    private var strokeBrush: Brush? = null
    private var strokeColor: Color = Color.Unspecified

    /** Fills [path] with the wallpaper under [windowPos] tinted by [color], or a flat tint without [source]. */
    fun DrawScope.drawBody(path: Path, source: GlassSource?, color: Color, windowPos: Offset) {
        if (source == null) {
            drawPath(path, Tokens.GlassFallback)
            return
        }
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
    }

    /** The top-lit gradient edge in [stroke]. */
    fun DrawScope.drawEdge(path: Path, stroke: Color) {
        val brush = strokeBrush?.takeIf { stroke == strokeColor } ?: Brush.verticalGradient(
            0f to stroke,
            0.5f to stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeMidAlpha),
            1f to stroke.copy(alpha = stroke.alpha * Tokens.GlassStrokeBottomAlpha),
        ).also {
            strokeBrush = it
            strokeColor = stroke
        }
        drawPath(path, brush, style = Stroke(Tokens.GlassStrokeWidth.toPx()))
    }

    /** The gradient spans the element's height, so it is rebuilt after a resize. */
    fun onResize() {
        strokeBrush = null
    }
}
