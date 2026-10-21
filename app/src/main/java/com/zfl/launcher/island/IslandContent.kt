package com.zfl.launcher.island

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Motion
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates the Island: picks the content for the current card and hands it to [IslandMorph].
 * A compact card squeezes under the finger and expands on tap; a card collapses on a swipe up,
 * a tap outside (see [IslandWindow]) or its timeout. [exited] fires when the disappear morph ends.
 */
@Composable
internal fun IslandContent(
    state: StateFlow<IslandRenderState>,
    bounds: StateFlow<IslandGeometry>,
    blurSupported: StateFlow<Boolean>,
    actions: IslandActions,
    resize: (IntSize) -> Unit,
    glass: (Float, IntSize) -> Unit,
    exited: () -> Unit,
) {
    val render by state.collectAsStateWithLifecycle()
    val geometry by bounds.collectAsStateWithLifecycle()
    val canBlur by blurSupported.collectAsStateWithLifecycle()
    // The last visible content keeps showing while the shape morphs away into the camera.
    val last = remember { arrayOf(render) }
    if (render.model.card != IslandCard.Hidden) last[0] = render
    val shown = last[0]
    val style = render.style
    val card = render.model.card
    // The card the shape last came to rest at, and its size: only a settled card gets blur.
    var settled by remember { mutableStateOf<Pair<IslandCard, IntSize>?>(null) }
    val held = remember { arrayOf(IslandCard.Hidden) }
    val wantsGlass = style.blur && canBlur && card.expanded && settled?.first == card
    val clear by animateFloatAsState(if (wantsGlass) 1f else 0f,
        tween(if (wantsGlass) Motion.ISLAND_IN_MS else Motion.ISLAND_OUT_MS), label = "islandGlass")
    // A collapsing card waits for its blur to fade, so the blurred area never sticks out of it.
    val target = if (!card.expanded && held[0].expanded && clear > 0f) held[0] else card
    held[0] = target
    LaunchedEffect(target) { if (settled?.first != target) settled = null }
    val onGlass by rememberUpdatedState(glass)
    LaunchedEffect(Unit) {
        snapshotFlow { clear to settled?.second }.collect { (progress, size) -> onGlass(if (size == null) 0f else progress, size ?: IntSize.Zero) }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed && !card.expanded) PRESSED_SCALE else 1f, Motion.press(), label = "islandPress")
    val canExpand = render.model.expansionAllowed && (card == IslandCard.MusicCompact || card == IslandCard.TimerCompact)
    CompositionLocalProvider(LocalIslandStyle provides style) {
        IslandMorph(
            initial = IslandCard.Hidden,
            target = target,
            accent = {
                when (shown.model.card) {
                    IslandCard.TimerCompact, IslandCard.TimerExpanded -> IslandTokens.Orange
                    else -> shown.accent?.takeIf { style.artworkTint }?.let(::Color)
                }
            },
            translucency = { clear },
            maxSize = with(LocalDensity.current) {
                IntSize((geometry.widthDp + style.sideMargin * 2).dp.roundToPx(), geometry.maxHeightDp.dp.roundToPx())
            },
            modifier = Modifier
                .graphicsLayer {
                    scaleX = squeeze
                    scaleY = squeeze
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .pointerInput(actions) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        actions.event(IslandEvent.Interaction)
                        waitForUpOrCancellation(pass = PointerEventPass.Final)
                        actions.event(IslandEvent.Interaction)
                    }
                }
                .then(if (card.expanded) Modifier.pointerInput(actions) {
                    var travel = 0f
                    detectVerticalDragGestures(onDragStart = { travel = 0f }, onDragEnd = {
                        if (travel < -COLLAPSE_SWIPE_DP.dp.toPx()) actions.event(IslandEvent.Collapse)
                    }) { _, dy -> travel += dy }
                } else Modifier)
                .clickable(interaction, indication = null, enabled = canExpand, role = Role.Button,
                    onClickLabel = stringResource(R.string.island_expand)) { actions.event(IslandEvent.Expand) },
            onFrame = resize,
            onSettled = { item, size ->
                settled = item to size
                if (item == IslandCard.Hidden) exited()
            },
        ) { item ->
            val model = shown.model
            val accent = shown.accent?.takeIf { style.artworkTint }?.let(::Color)
            when (item) {
                IslandCard.Hidden -> Box(Modifier.size(IslandTokens.Dot))
                IslandCard.Idle -> Box(Modifier.size(IslandTokens.IdleWidth, IslandTokens.CompactHeight))
                IslandCard.MusicCompact -> model.selected?.let { CompactMusic(it, shown.artwork, accent) } ?: CompactPlaceholder()
                IslandCard.MusicExpanded -> model.selected?.let { media ->
                    MusicCard(media, shown.artwork, accent, geometry.widthDp.dp,
                        { action, position -> actions.command(media.id, action, position) }, { actions.openPlayer(media.id) })
                } ?: CompactPlaceholder()
                IslandCard.Charging -> CompactCharging(model.batteryPercent)
                IslandCard.TimerCompact -> model.timer?.let { CompactTimer(it) } ?: CompactPlaceholder()
                IslandCard.TimerExpanded -> model.timer?.let { timer ->
                    TimerCard(timer, geometry.widthDp.dp) { actions.timer(timer.key, it) }
                } ?: CompactPlaceholder()
            }
        }
    }
}

private const val PRESSED_SCALE = 0.94f
private const val COLLAPSE_SWIPE_DP = 24
