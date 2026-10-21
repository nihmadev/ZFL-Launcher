package com.zfl.launcher.ui.icon

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.badgeSum
import com.zfl.launcher.data.badgeText
import com.zfl.launcher.settings.BadgeStyle
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch

/** Whether an icon draws its notification badge: never, now, or not now (edit mode: it shrinks away). */
enum class IconBadge { None, Shown, Hidden }

/**
 * Badge counts for launcher icons. [counts] is read only inside each icon's derived state, so
 * one app's change recomposes that icon alone. Null in [LocalBadges] while badges are off.
 */
@Stable
class BadgeSource(val counts: State<Map<String, Int>>, val style: BadgeStyle)

val LocalBadges = staticCompositionLocalOf<BadgeSource?> { null }

/** Count for [key] ([com.zfl.launcher.data.AppInfo.badgeKey]); the caller recomposes only when it changes. */
@Composable
fun badgeCount(source: BadgeSource, key: String): Int {
    val count by remember(source, key) { derivedStateOf { source.counts.value[key] ?: 0 } }
    return count
}

/** A folder's count: the sum over [keys], recomposing only when the sum changes. */
@Composable
fun badgeCount(source: BadgeSource, keys: List<String>): Int {
    val count by remember(source, keys) { derivedStateOf { badgeSum(keys, source.counts.value) } }
    return count
}

/**
 * How far a badge pokes out above (and past the side of) an icon of [iconSize]. A clipping
 * container whose first row starts at its edge (a lazy grid) needs this much padding there.
 */
fun badgeOverhang(iconSize: Dp): Dp = iconSize * Tokens.BadgeOverhangRatio

/**
 * Badge of a group of apps (a folder, an App Library cluster): the sum of their counts over
 * the group's tile, with the count read out after [name]. [Modifier] alone while badges are off.
 */
@Composable
fun groupBadgeModifier(apps: List<AppInfo>, name: String, badge: IconBadge): Modifier {
    val badges = LocalBadges.current.takeIf { badge != IconBadge.None } ?: return Modifier
    val keys = remember(apps) { apps.map { it.badgeKey } }
    val count = if (badge == IconBadge.Shown) badgeCount(badges, keys) else 0
    val drawn = Modifier.notificationBadge(count, badges.style == BadgeStyle.Dot)
    if (count == 0) return drawn
    val description = pluralStringResource(R.plurals.icon_badge_description, count, name, count)
    return drawn.semantics { contentDescription = description }
}

/**
 * Draws a notification badge over the top-end corner of the content (an icon of the size this
 * modifier gets), scaled with it. Drawn in the icon's own draw pass: no layer, no offscreen
 * buffer, nothing added to the cached icon bitmap. Appears and disappears with a short scale
 * animation (system animator duration scale applies); costs nothing before the first count.
 */
fun Modifier.notificationBadge(count: Int, dot: Boolean): Modifier = this then BadgeElement(count, dot)

private data class BadgeElement(val count: Int, val dot: Boolean) : ModifierNodeElement<BadgeNode>() {
    override fun create(): BadgeNode = BadgeNode(count, dot)
    override fun update(node: BadgeNode): Unit = node.update(count, dot)
    override fun InspectorInfo.inspectableProperties() {
        name = "notificationBadge"
        properties["count"] = count
        properties["dot"] = dot
    }
}

private class BadgeNode(private var count: Int, private var dot: Boolean) : Modifier.Node(), DrawModifierNode {
    /** Last count above zero, still drawn while the badge shrinks away. */
    private var shown = count
    /** Created with the first count; a badge already there when the icon appears doesn't animate. */
    private var scale: Animatable<Float, AnimationVector1D>? = if (count > 0) Animatable(1f) else null

    fun update(count: Int, dot: Boolean) {
        if (count > 0) shown = count
        val changed = this.count != count || this.dot != dot
        this.count = count
        this.dot = dot
        val target = if (count > 0) 1f else 0f
        val current = scale ?: if (count > 0) Animatable(0f).also { scale = it } else null
        if (current != null && current.targetValue != target) {
            coroutineScope.launch { current.animateTo(target, Motion.pop()) }
        }
        if (changed) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val progress = scale?.value ?: return
        if (progress <= 0f || shown <= 0) return
        val icon = size.minDimension
        val height = icon * if (dot) Tokens.BadgeDotRatio else Tokens.BadgeSizeRatio
        val text = if (dot) null else badgeText(shown)
        val paint = BadgePaint.apply { textSize = height * Tokens.BadgeTextRatio }
        val textWidth = text?.let(paint::measureText) ?: 0f
        val width = if (text == null || text.length == 1) height else maxOf(height, textWidth + height * Tokens.BadgePaddingRatio * 2)
        // The badge pokes out past the corner by the same share of the icon on both axes.
        val overhang = icon * Tokens.BadgeOverhangRatio
        val left = if (layoutDirection == LayoutDirection.Rtl) -overhang else size.width + overhang - width
        val top = -overhang
        val center = Offset(left + width / 2, top + height / 2)
        scale(progress, pivot = center) {
            val radius = CornerRadius(height / 2)
            drawRoundRect(Tokens.BadgeColor, Offset(left, top), Size(width, height), radius)
            val stroke = icon * Tokens.BadgeStrokeRatio
            drawRoundRect(Tokens.BadgeStroke, Offset(left, top), Size(width, height), radius, style = Stroke(stroke))
            if (text != null) {
                val baseline = center.y - (paint.ascent() + paint.descent()) / 2
                drawContext.canvas.nativeCanvas.drawText(text, center.x, baseline, paint)
            }
        }
    }
}

/** Shared by every badge; icons are drawn on the main thread only. */
private val BadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Tokens.BadgeText.toArgb()
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, BADGE_FONT_WEIGHT, false)
}

private const val BADGE_FONT_WEIGHT = 600
