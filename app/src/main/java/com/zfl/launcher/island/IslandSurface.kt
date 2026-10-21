package com.zfl.launcher.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Island shape: one surface that springs between the sizes of its contents, from a capsule
 * around the camera to a card, with the old content blurring out and the new one blurring in.
 *
 * Every content is measured at its final size, so the target is known in the first frame. The
 * reported size (the window) covers the start, the target and the spring's overshoot, and only
 * changes when a morph starts and when it settles: the window is not resized every frame. The
 * animated size is read in layout and draw only, never in composition.
 *
 * Shapes lower than the capsule (the appear/disappear dot) stay centered on the camera.
 * The first composition morphs from [initial]. [onSettled] reports each target the shape comes to rest at.
 */
@Composable
internal fun <K : Any> IslandMorph(
    initial: K,
    target: K,
    accent: () -> Color?,
    /** 0 for the opaque card, 1 for glass over the blurred backdrop (see [IslandBlur]). */
    translucency: () -> Float,
    /** Largest content size: the screen, not the window, which follows [onFrame]. */
    maxSize: IntSize,
    modifier: Modifier = Modifier,
    onFrame: (IntSize) -> Unit,
    onSettled: (K, IntSize) -> Unit,
    content: @Composable (K) -> Unit,
) {
    val transitionState = remember { MutableTransitionState(initial) }
    transitionState.targetState = target
    val transition = rememberTransition(transitionState, label = "island")
    val state = remember { IslandMorphState() }
    val scope = rememberCoroutineScope()
    val settled by rememberUpdatedState(onSettled)
    val frameChanged by rememberUpdatedState(onFrame)
    LaunchedEffect(state) {
        snapshotFlow { state.frame }.collect { frame ->
            if (frame != IntSize.Zero) frameChanged(frame)
        }
    }
    val accentColor by rememberUpdatedState(accent)
    val clearness by rememberUpdatedState(translucency)
    val keys = listOf(transition.currentState, transition.targetState).distinct()
    Layout(
        content = {
            keys.forEach { item ->
                key(item) {
                    val shown by transition.animateFloat(
                        transitionSpec = {
                            if (targetState == item) tween(Motion.ISLAND_IN_MS, Motion.ISLAND_IN_DELAY_MS, Motion.IosEasing)
                            else tween(Motion.ISLAND_OUT_MS)
                        },
                        label = "islandContent",
                    ) { if (it == item) 1f else 0f }
                    Box(Modifier.layoutId(item).graphicsLayer {
                        alpha = shown
                        val scale = MORPH_CONTENT_SCALE + (1f - MORPH_CONTENT_SCALE) * shown
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        val blur = (1f - shown) * MORPH_BLUR_DP * density
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                    }) { content(item) }
                }
            }
        },
        modifier = modifier.drawWithContent {
            val current = state.animation?.value ?: return@drawWithContent
            val compact = IslandTokens.CompactHeight.toPx()
            val w = current.width.toFloat().coerceAtMost(size.width)
            val h = current.height.toFloat().coerceAtMost(size.height)
            val left = (size.width - w) / 2
            val top = ((compact - h) / 2).coerceAtLeast(0f)
            val radius = minOf(h / 2, IslandTokens.CardRadius.toPx())
            // A plain rounded rectangle: the blurred backdrop under it has exactly these corners.
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius))) }
            // 0 for the black capsule that hides the camera, 1 for the glass card.
            val glass = ((h - compact) / (IslandTokens.GlassBlendHeight.toPx() - compact)).coerceIn(0f, 1f)
            translate(left, top) {
                drawPath(path, IslandTokens.Pill, alpha = 1f - glass)
                if (glass > 0f) {
                    drawPath(path, lerp(IslandTokens.CardBase, IslandTokens.CardGlass, clearness()), alpha = glass)
                    accentColor()?.let { tint ->
                        drawPath(path, Brush.verticalGradient(
                            0f to tint.copy(alpha = IslandTokens.ACCENT_TOP_ALPHA),
                            1f to tint.copy(alpha = IslandTokens.ACCENT_BOTTOM_ALPHA),
                            endY = h,
                        ), alpha = glass)
                        // The artwork's glow in the top corner, like the reference.
                        val artwork = Offset((IslandTokens.CardPadding + IslandTokens.Artwork / 2).toPx(),
                            (IslandTokens.CardTop + IslandTokens.Artwork / 2).toPx())
                        drawPath(path, Brush.radialGradient(listOf(tint.copy(alpha = IslandTokens.ACCENT_GLOW_ALPHA), Color.Transparent),
                            center = artwork, radius = w * ACCENT_GLOW_RADIUS), alpha = glass)
                    }
                    drawPath(path, Brush.radialGradient(listOf(IslandTokens.Specular, Color.Transparent),
                        center = Offset(w * SPECULAR_X, 0f), radius = w * SPECULAR_RADIUS), alpha = glass)
                }
            }
            translate(left, top) { clipPath(path) { translate(-left, -top) { this@drawWithContent.drawContent() } } }
            if (glass > 0f) translate(left, top) {
                drawPath(path, Brush.verticalGradient(
                    0f to IslandTokens.EdgeTop, 0.45f to IslandTokens.EdgeMid, 1f to IslandTokens.EdgeBottom, endY = h,
                ), alpha = glass, style = Stroke(IslandTokens.Edge.toPx()))
            }
        },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = maxSize.width, maxHeight = maxSize.height)
        val placeables = measurables.map { it.measure(loose) }
        val targetIndex = measurables.indexOfFirst { it.layoutId == transition.targetState }.coerceAtLeast(0)
        val goal = placeables.getOrNull(targetIndex)?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
        val dot = IslandTokens.Dot.roundToPx()
        val compact = IslandTokens.CompactHeight.roundToPx()
        val animation = state.animation ?: Animatable(IntSize(dot, dot), IntSize.VectorConverter).also { state.animation = it }
        if (goal != state.goal) {
            state.goal = goal
            val from = animation.value
            val slack = IslandTokens.MorphSlack.roundToPx()
            // Never wider than the screen: WindowManager would shift an oversized window sideways.
            state.frame = IntSize((maxOf(from.width, goal.width) + slack * 2).coerceAtMost(maxSize.width),
                (maxOf(from.height, goal.height, compact) + slack).coerceAtMost(maxSize.height))
            state.job?.cancel()
            val settledTarget = transition.targetState
            state.job = scope.launch {
                animation.animateTo(goal, spring(Motion.ISLAND_DAMPING, Motion.ISLAND_STIFFNESS, IntSize(1, 1)))
                state.frame = goal
                settled(settledTarget, goal)
            }
        }
        // Until the window has taken the new frame (one frame later) the content is clipped to it.
        val width = constraints.constrainWidth(state.frame.width)
        val height = constraints.constrainHeight(maxOf(state.frame.height, compact))
        layout(width, height) {
            placeables.forEach { it.place((width - it.width) / 2, ((compact - it.height) / 2).coerceAtLeast(0)) }
        }
    }
}

private class IslandMorphState {
    var goal: IntSize? = null
    var animation: Animatable<IntSize, AnimationVector2D>? = null
    var job: Job? = null
    /** Window size: read in measurement, so only its changes relayout the window. */
    var frame: IntSize by mutableStateOf(IntSize.Zero)
}

/** Incoming content grows from this scale while it unblurs, anchored at the camera. */
private const val MORPH_CONTENT_SCALE = 0.86f
private const val MORPH_BLUR_DP = 10f
private const val ACCENT_GLOW_RADIUS = 0.6f
private const val SPECULAR_X = 0.22f
private const val SPECULAR_RADIUS = 0.7f
