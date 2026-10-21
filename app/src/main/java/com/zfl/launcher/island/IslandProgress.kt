package com.zfl.launcher.island

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * iOS now-playing scrubber: elapsed time, a thin track that thickens under the finger and the
 * remaining time. The only progress clock: 1 Hz, only in a visible card, only while playing.
 */
@Composable
internal fun IslandProgress(session: IslandSession, seek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var drag by remember(session.id, session.durationMs, session.canSeek) { mutableStateOf<Float?>(null) }
    LaunchedEffect(session.id, session.playback, session.positionMs, session.positionAtMs, session.speed) {
        now = SystemClock.elapsedRealtime()
        if (session.playback == IslandPlayback.Playing && session.speed.isFinite() && session.speed != 0f) {
            while (isActive) {
                delay(IslandTiming.PROGRESS_MS)
                now = SystemClock.elapsedRealtime()
            }
        }
    }
    val duration = session.durationMs ?: return
    val position = session.progressPosition(now) ?: return
    val fraction = drag ?: (position.toFloat() / duration)
    val shownMs = (fraction * duration).toLong()
    val label = stringResource(R.string.island_seek)
    val onSeek by rememberUpdatedState(seek)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val track by animateDpAsState(if (drag != null) IslandTokens.TrackActive else IslandTokens.Track, Motion.press(), label = "track")
    val style = TextStyle(fontSize = IslandTokens.Time, lineHeight = IslandTokens.TimeLine, color = IslandTokens.TextSecondary,
        fontFeatureSettings = "tnum")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IslandTokens.Gap)) {
        Text(islandDuration(shownMs / 1000), style = style)
        Canvas(
            Modifier.weight(1f).height(IslandTokens.Control / 2)
                .progressSemantics(fraction)
                .semantics {
                    contentDescription = label
                    if (session.canSeek) setProgress { onSeek((it.coerceIn(0f, 1f) * duration).toLong()); true }
                }
                .then(if (session.canSeek) Modifier.pointerInput(duration, rtl) {
                    fun at(x: Float): Float = ((if (rtl) size.width - x else x) / size.width).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        drag = at(down.position.x)
                        val completed = horizontalDrag(down.id) { change ->
                            drag = at(change.position.x)
                            change.consume()
                        }
                        drag?.takeIf { completed }?.let { onSeek((it * duration).toLong()) }
                        drag = null
                    }
                } else Modifier),
        ) {
            val h = track.toPx()
            val top = (size.height - h) / 2
            val radius = CornerRadius(h / 2)
            drawRoundRect(IslandTokens.TrackColor, Offset(0f, top), Size(size.width, h), radius)
            val filled = size.width * fraction.coerceIn(0f, 1f)
            drawRoundRect(IslandTokens.TrackFill, Offset(if (rtl) size.width - filled else 0f, top), Size(filled, h), radius)
        }
        Text("-" + islandDuration((duration - shownMs).coerceAtLeast(0) / 1000), style = style)
    }
}
