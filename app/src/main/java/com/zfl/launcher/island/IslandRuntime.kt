package com.zfl.launcher.island

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import com.zfl.launcher.R
import com.zfl.launcher.settings.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow

internal data class IslandRenderState(
    val model: IslandModel = IslandModel(),
    val artwork: ImageBitmap? = null,
    /** Vivid color of [artwork] for the card tint and waveform, null for gray or missing artwork. */
    val accent: Int? = null,
    val style: IslandStyle = IslandStyle(),
)

/** Android events only feed the reducer. One deadline, no polling, sensors, package or wallpaper queries. */
internal class IslandRuntime(
    private val context: Context,
    windowContext: Context,
    private val accessLost: () -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val render = MutableStateFlow(IslandRenderState())
    private var model = IslandModel()
    private var closed = false
    private var batteryRegistered = false
    private var artworkKey: Pair<Long, Long>? = null
    private var timerEntries: Map<String, IslandTimerEntry> = emptyMap()
    private val deadline = Runnable { dispatch(IslandEvent.Time) }
    private val window = IslandWindow(windowContext, render, IslandActions(::event, ::command, ::openPlayer, ::timerAction)) { accessLost() }
    private val media = IslandMediaSource(context, main, { dispatch(IslandEvent.Sessions(it)) }, { id, version, bitmap, accent ->
        if (artworkKey == id to version && model.card != IslandCard.Hidden) {
            render.value = render.value.copy(artwork = bitmap, accent = accent)
        }
    }, accessLost)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> dispatch(IslandEvent.Visibility(false, false))
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> visibility()
            }
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            battery(intent, isInitialStickyBroadcast)
        }
    }

    init {
        ContextCompat.registerReceiver(context, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun environment(value: IslandEnvironment) {
        dispatch(IslandEvent.Environment(value))
        if (closed) return
        visibility()
        if (value.chargingEnabled && !batteryRegistered) {
            batteryRegistered = true
            val sticky = ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            sticky?.let { battery(it, baseline = true) }
        } else if (!value.chargingEnabled && batteryRegistered) {
            context.unregisterReceiver(batteryReceiver)
            batteryRegistered = false
        }
    }

    /** The user's look; geometry depends on it (pill width, margins, offset). */
    fun style(value: IslandStyle) {
        if (closed || render.value.style == value) return
        render.value = render.value.copy(style = value)
        dispatch(IslandEvent.Time)
    }

    /** The clock activities currently posted, replacing the previous set. */
    fun timers(entries: List<IslandTimerEntry>) {
        timerEntries = entries.associateBy { it.timer.key }
        dispatch(IslandEvent.Timers(entries.map { it.timer }))
    }

    private fun granted(): Boolean {
        val grants = IslandAccess.read(context)
        return grants.window && grants.notifications && grants.connected
    }

    private fun visibility() {
        dispatch(IslandEvent.Visibility(power.isInteractive, !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked))
    }

    private fun battery(intent: Intent, baseline: Boolean) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).toInt() else null
        dispatch(IslandEvent.Battery(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, percent, baseline))
    }

    private fun event(value: IslandEvent) {
        // User actions recheck screen/lock as well as grants before touching another session.
        visibility()
        if (model.visible) dispatch(value)
    }

    private fun dispatch(value: IslandEvent) {
        if (closed) return
        if (!granted()) {
            close()
            accessLost()
            return
        }
        model = reduceIsland(model, value, SystemClock.elapsedRealtime())
        val geometry = window.syncGeometry()
        model = reduceIsland(model, IslandEvent.Space(geometry.available, geometry.expansionAllowed), SystemClock.elapsedRealtime())
        val selected = model.selected.takeIf { model.card != IslandCard.Hidden }
        val nextKey = selected?.let { it.id to it.artworkVersion }
        // Players re-send metadata on play/pause: keep the session's artwork until the reload lands,
        // or the card blinks without its art and tint for a frame.
        val keep = nextKey != null && nextKey.first == artworkKey?.first
        artworkKey = nextKey
        render.value = IslandRenderState(model, render.value.artwork.takeIf { keep }, render.value.accent.takeIf { keep }, render.value.style)
        media.artwork(selected?.id, selected?.artworkVersion ?: 0)
        window.update(model.card != IslandCard.Hidden)
        main.removeCallbacks(deadline)
        model.nextDeadline()?.let { at -> main.postDelayed(deadline, (at - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
    }

    private fun command(expectedId: Long, value: IslandCommand, positionMs: Long?) {
        event(IslandEvent.Interaction)
        if (closed || model.card != IslandCard.MusicExpanded) return
        model.selected?.takeIf { it.id == expectedId }?.let { session ->
            if (value in session.commands && (value != IslandCommand.Seek || session.canSeek)) media.command(session.id, value, positionMs)
        }
    }

    private fun openPlayer(expectedId: Long) {
        event(IslandEvent.Interaction)
        if (closed || model.card != IslandCard.MusicExpanded) return
        model.selected?.takeIf { it.canOpen && it.id == expectedId }?.let { session ->
            media.openPlayer(session.id, stillVisible = {
                !closed && model.card == IslandCard.MusicExpanded && model.selectedId == session.id && granted() &&
                    power.isInteractive && !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked
            }, onUnavailable = {
                Toast.makeText(AppLanguage.wrap(context), R.string.island_player_unavailable, Toast.LENGTH_SHORT).show()
            })
        }
    }

    /** [index] is one of the activity's buttons, null opens its app. */
    private fun timerAction(key: String, index: Int?) {
        event(IslandEvent.Interaction)
        if (closed || model.card != IslandCard.TimerExpanded || model.timer?.key != key) return
        val entry = timerEntries[key] ?: return
        val intent = if (index == null) entry.content else entry.actions.getOrNull(index)
        if (intent == null || !sendFromIsland(context, intent)) {
            Toast.makeText(AppLanguage.wrap(context), R.string.island_action_unavailable, Toast.LENGTH_SHORT).show()
        } else if (index == null) dispatch(IslandEvent.Collapse)
    }

    override fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        context.unregisterReceiver(screenReceiver)
        if (batteryRegistered) context.unregisterReceiver(batteryReceiver)
        batteryRegistered = false
        media.close()
        window.close()
        artworkKey = null
        timerEntries = emptyMap()
        model = IslandModel()
        render.value = IslandRenderState(style = render.value.style)
    }
}
