package com.zfl.launcher.ui.glass

import android.graphics.Paint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.zfl.launcher.ui.shape.CONTINUOUS_LIMIT_RATIO
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.shape.IconShape
import com.zfl.launcher.ui.theme.LiquidGlassTokens
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The shader's view of [shape] at [size], or null for shapes it can't describe (those
 * elements stay Frosted). Continuous corners become superellipses, see [GlassOutline].
 */
internal fun glassOutlineOf(shape: Shape, size: Size, layoutDirection: LayoutDirection, density: Density): GlassOutline? {
    val w = size.width
    val h = size.height
    return when (shape) {
        is ContinuousRoundedShape -> GlassOutline.continuous(w, h, shape.cornerRadius(size, density))
        is IconShape -> {
            val side = min(w, h)
            if (shape.cornerRatio <= CONTINUOUS_LIMIT_RATIO) {
                GlassOutline.continuous(w, h, side * shape.cornerRatio)
            } else {
                val r = side * shape.cornerRatio.coerceAtMost(0.5f)
                GlassOutline.rounded(w, h, r, r, r, r)
            }
        }
        is RoundedCornerShape -> {
            val start = shape.topStart.toPx(size, density)
            val end = shape.topEnd.toPx(size, density)
            val bottomEnd = shape.bottomEnd.toPx(size, density)
            val bottomStart = shape.bottomStart.toPx(size, density)
            if (layoutDirection == LayoutDirection.Ltr) {
                GlassOutline.rounded(w, h, start, end, bottomEnd, bottomStart)
            } else {
                GlassOutline.rounded(w, h, end, start, bottomStart, bottomEnd)
            }
        }
        RectangleShape -> GlassOutline.rounded(w, h, 0f, 0f, 0f, 0f)
        else -> null
    }
}

/** Per-draw input of [LiquidGlassPainter]; owned and refilled by the node, never reallocated. */
internal class LiquidGlassFrame {
    var outline: GlassOutline? = null
    var windowPos: Offset = Offset.Zero
    var rim: Float = 0f
    var refraction: Float = 0f
    val tone: GlassTone = GlassTone()
    var highlight: Color = Tokens.GlassStroke
    var edge: Boolean = true
    /** Tilt of the light from its base direction, radians. */
    var lightAngle: Float = 0f
    var touchX: Float = 0f
    var touchY: Float = 0f
    var touchAmount: Float = 0f
}

/** Draws one Liquid Glass element with the shared [LiquidGlassShader]. */
@RequiresApi(LIQUID_GLASS_MIN_SDK)
internal class LiquidGlassPainter {
    private val paint = Paint()

    /** Returns false (nothing drawn) when the shader is unavailable; the caller then draws Frosted. */
    fun DrawScope.draw(frame: LiquidGlassFrame, source: GlassSource): Boolean {
        val outline = frame.outline ?: return false
        val sharp = source.sharp ?: return false
        val body = source.body ?: return false
        val shader = LiquidGlassShader.get() ?: return false
        LiquidGlassShader.bind(shader, sharp, body, 1f / source.sharpDownscale)
        LiquidGlassShader.setConstants(shader, this)

        val tone = frame.tone
        shader.setFloatUniform("size", outline.width, outline.height)
        shader.setFloatUniform("origin", frame.windowPos.x, frame.windowPos.y)
        shader.setFloatUniform("corners", outline.topLeft, outline.topRight, outline.bottomRight, outline.bottomLeft)
        shader.setFloatUniform("exponent", outline.exponent)
        shader.setFloatUniform("lens", frame.rim, frame.refraction)
        shader.setFloatUniform("tint", tone.red, tone.green, tone.blue, tone.alpha)
        // The stroke color of the call site colors the rim; its alpha is relative to the default stroke.
        val hl = frame.highlight
        val strength = if (frame.edge) LiquidGlassTokens.HighlightStrength * tone.highlight * hl.alpha / Tokens.GlassStroke.alpha else 0f
        shader.setFloatUniform("rimColor", hl.red, hl.green, hl.blue, strength)
        // Angle 0 = light from the top of the screen (y grows downwards), negative = from the left.
        val angle = LIGHT_BASE + frame.lightAngle
        shader.setFloatUniform("lightDir", sin(angle), -cos(angle))
        shader.setFloatUniform("shadowOpacity", LiquidGlassTokens.ShadowOpacity * tone.shadow)
        shader.setFloatUniform(
            "touch",
            frame.touchX, frame.touchY, frame.touchAmount,
            min(outline.width, outline.height) * LiquidGlassTokens.TouchGlowRadius,
        )

        paint.shader = shader
        // The shadow reaches past the element; the glass itself is anti-aliased by the shader.
        val m = LiquidGlassTokens.ShadowRadius.toPx() + LiquidGlassTokens.ShadowOffsetY.toPx()
        drawIntoCanvas { it.nativeCanvas.drawRect(-m, -m, outline.width + m, outline.height + m, paint) }
        return true
    }

    private companion object {
        val LIGHT_BASE: Float = Math.toRadians(LiquidGlassTokens.LightBaseDegrees.toDouble()).toFloat()
    }
}
