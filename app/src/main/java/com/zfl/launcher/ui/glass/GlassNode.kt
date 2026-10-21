package com.zfl.launcher.ui.glass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import com.zfl.launcher.ui.theme.LiquidGlassTokens

/**
 * Node behind [glass]: tracks the window position, draws Liquid glass when the style asks
 * for it and it can be drawn, Frosted glass otherwise, then the content on top.
 *
 * Everything that changes while the element is on screen (position, light, touch) is read
 * in the draw phase; nothing here causes a recomposition.
 */
internal class GlassNode(
    private var shape: Shape,
    private var tint: Color,
    private var stroke: Color,
    private var profile: GlassProfile,
) : DelegatingNode(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var windowPos = Offset.Zero

    // Outline, rebuilt only when the size, direction or shape change.
    private var outlineSize = Size.Unspecified
    private var outlineDirection = LayoutDirection.Ltr
    private val path = Path()
    private var outlineValid = false

    private val frosted = FrostedGlass()

    // Liquid glass state: created on first use on Android 13+.
    private var liquid: LiquidGlassPainter? = null
    private val frame = LiquidGlassFrame()
    private var liquidDrawn = false

    // Background brightness under the element, recomputed when it moves or the wallpaper changes.
    private var toneValid = false
    private var toneSource: GlassSource? = null
    private var tonePos = Offset.Unspecified
    private var toneSize = Size.Unspecified
    private var toneAlpha = 0f
    private var luminance = 0.5f

    // Touch reaction of buttons, see [GlassPress]. Only touch profiles get a pointer node: on a
    // panel it would swallow touches meant for what lies under it (a swipe that starts on the
    // dock must still turn the page).
    private var touchNode: GlassTouchNode? = null
    private val press = GlassPress { coroutineScope }

    override fun onAttach() {
        syncTouchNode()
    }

    fun update(shape: Shape, tint: Color, stroke: Color, profile: GlassProfile) {
        if (shape != this.shape) {
            this.shape = shape
            outlineValid = false
        }
        if (tint != this.tint) {
            this.tint = tint
            toneValid = false
        }
        this.stroke = stroke
        if (profile != this.profile) {
            this.profile = profile
            if (isAttached) syncTouchNode()
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
        if (!outlineValid || size != outlineSize || layoutDirection != outlineDirection) {
            path.reset()
            path.addOutline(shape.createOutline(size, layoutDirection, this))
            frame.outline = glassOutlineOf(shape, size, layoutDirection, this)
            outlineSize = size
            outlineDirection = layoutDirection
            outlineValid = true
            frosted.onResize()
        }
        // Read in the draw phase, so a new wallpaper, style or light redraws this node.
        val source = currentValueOf(LocalGlassSource)
        val style = currentValueOf(LocalGlassStyle)

        liquidDrawn = false
        if (style.mode == GlassMode.Liquid && source?.sharp != null && Build.VERSION.SDK_INT >= LIQUID_GLASS_MIN_SDK) {
            // A pressed button grows and follows the finger, content included; the hit area stays put.
            if (press.measure(size.width, size.height, LiquidGlassTokens.PressGrow.toPx(), LiquidGlassTokens.DragStretch.toPx())) {
                withTransform({
                    translate(press.translationX, press.translationY)
                    scale(press.scaleX, press.scaleY, center)
                }) {
                    liquidDrawn = drawLiquid(source, style)
                    if (liquidDrawn) this@draw.drawContent()
                }
            } else {
                liquidDrawn = drawLiquid(source, style)
                if (liquidDrawn) drawContent()
            }
            if (liquidDrawn) return
        }

        // Frosted, or the flat tint without a wallpaper.
        val color = if (tint.isSpecified) tint else Color.White.copy(alpha = style.tintAlpha)
        with(frosted) {
            drawBody(path, source, color, windowPos, style.fallback)
            if (style.edge) drawEdge(path, stroke)
        }
        drawContent()
    }

    @RequiresApi(LIQUID_GLASS_MIN_SDK)
    private fun DrawScope.drawLiquid(source: GlassSource, style: GlassStyle): Boolean {
        updateTone(source, style)
        val rimToken = when (profile) {
            GlassProfile.Panel -> LiquidGlassTokens.PanelRim
            GlassProfile.Button -> LiquidGlassTokens.ButtonRim
            GlassProfile.SmallButton -> LiquidGlassTokens.SmallButtonRim
            GlassProfile.Tile -> LiquidGlassTokens.TileRim
        }
        val rim = effectiveRim(rimToken.toPx(), size.width, size.height)
        frame.windowPos = windowPos
        frame.rim = rim
        // Never further than the middle: the sample would come out on the other side.
        frame.refraction = min(
            rim * LiquidGlassTokens.MaxRefraction * style.refraction.coerceIn(0f, 1f),
            min(size.width, size.height) / 2f,
        )
        frame.highlight = stroke
        frame.edge = style.edge
        frame.lightAngle = currentValueOf(LocalGlassLight).angle
        val touch = press.touch
        frame.touchX = touch.x
        frame.touchY = touch.y
        frame.touchAmount = press.amount.coerceIn(0f, 1f)
        val painter = liquid ?: LiquidGlassPainter().also { liquid = it }
        return with(painter) { draw(frame, source) }
    }

    /** Recomputes the tint for the background brightness only when something it depends on changed. */
    private fun updateTone(source: GlassSource, style: GlassStyle) {
        if (toneValid && source === toneSource && windowPos == tonePos && outlineSize == toneSize && style.tintAlpha == toneAlpha) return
        toneValid = true
        toneSource = source
        tonePos = windowPos
        toneSize = outlineSize
        toneAlpha = style.tintAlpha
        val k = source.downscale.toFloat()
        luminance = source.luminance?.mean(
            windowPos.x / k, windowPos.y / k,
            (windowPos.x + outlineSize.width) / k, (windowPos.y + outlineSize.height) / k,
        ) ?: luminance
        if (tint.isSpecified) {
            fixedTone(tint.red, tint.green, tint.blue, tint.alpha, luminance, frame.tone)
        } else {
            glassTone(luminance, style.tintAlpha, frame.tone)
        }
    }

    private fun syncTouchNode() {
        val node = touchNode
        if (profile.touch && node == null) {
            touchNode = delegate(GlassTouchNode(press, ::touchEnabled))
        } else if (!profile.touch && node != null) {
            undelegate(node)
            touchNode = null
        }
    }

    /** Frosted glass has nothing to light up: its buttons don't react. */
    private fun touchEnabled(): Boolean = liquidDrawn

    override fun onDetach() {
        press.reset()
    }
}
