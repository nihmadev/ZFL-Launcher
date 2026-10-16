package com.zfl.launcher.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import kotlinx.coroutines.delay

/** Writing speed: a gentle start and a long soft landing, like a pen settling. */
private val WriteEasing = CubicBezierEasing(0.42f, 0f, 0.3f, 1f)
private const val WRITE_MS = 2600
private const val HOLD_MS = 700
private const val EXIT_MS = 650
private const val EXIT_SCALE = 0.06f
private val ExitBlur = 18.dp
private val SideMargin = 40.dp

/**
 * The handwritten "hello" of the first launch: the white stroke of `onboarding_hello` is written
 * along its length, rests, then fades out (alpha, scale, blur) before [onFinished].
 * A tap anywhere finishes right away. Every duration follows the animator duration scale; with
 * animations off the finished word is shown and [onFinished] is called at once.
 */
@Composable
internal fun HelloScreen(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val vector = ImageVector.vectorResource(R.drawable.onboarding_hello)
    val stroke = remember(vector) { vector.root.filterIsInstance<VectorPath>().single() }
    val path = remember(stroke) { stroke.pathData.toPath() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, forceClosed = false) } }
    val written = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            written.snapTo(1f)
        } else {
            written.animateTo(1f, tween(WRITE_MS, easing = WriteEasing))
            delay((HOLD_MS * scale).toLong())
            exit.animateTo(1f, tween(EXIT_MS, easing = FastOutSlowInEasing))
        }
        finish()
    }

    val description = stringResource(R.string.onboarding_hello_description)
    Box(
        Modifier
            .fillMaxSize()
            .background(OnboardingStyle.Background)
            .pointerInput(Unit) { detectTapGestures { finish() } }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .padding(horizontal = SideMargin)
                .fillMaxWidth()
                .aspectRatio(vector.viewportWidth / vector.viewportHeight)
                .graphicsLayer {
                    // Layer properties only: the exit never recomposes.
                    val e = exit.value
                    alpha = 1f - e
                    scaleX = 1f + EXIT_SCALE * e
                    scaleY = 1f + EXIT_SCALE * e
                    val blur = ExitBlur.toPx() * e
                    renderEffect = if (blur > 0f) BlurEffect(blur, blur) else null
                }
                .drawWithCache {
                    val viewportScale = size.width / vector.viewportWidth
                    val segment = Path()
                    val style = Stroke(width = stroke.strokeLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind {
                        val drawn = written.value
                        if (drawn <= 0f) return@onDrawBehind
                        segment.reset()
                        measure.getSegment(0f, measure.length * drawn, segment, startWithMoveTo = true)
                        scale(viewportScale, viewportScale, pivot = Offset.Zero) {
                            drawPath(segment, Color.White, style = style)
                        }
                    }
                },
        )
    }
}
