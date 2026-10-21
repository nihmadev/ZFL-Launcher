package com.zfl.launcher.island

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Platform subscriptions and Binder work live on one worker, owned only by the connected runtime. */
internal class IslandMediaSource(
    private val context: Context,
    private val main: Handler,
    private val onSessions: (List<IslandSession>) -> Unit,
    private val onArtwork: (Long, Long, ImageBitmap?, Int?) -> Unit,
    private val onAccessLost: () -> Unit,
) : AutoCloseable {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val thread = HandlerThread("IslandMedia").apply { start() }
    private val worker = Handler(thread.looper)
    private val closed = AtomicBoolean(false)
    private val entries = LinkedHashMap<MediaSession.Token, Entry>()
    private var nextId = 0L
    private val artworkRequest = AtomicLong()
    private var artworkKey: Pair<Long, Long>? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { replace(it.orEmpty()) }

    private inner class Entry(val controller: MediaController, val id: Long) {
        var artworkVersion = 0L
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                artworkVersion++
                publish()
            }
            override fun onSessionDestroyed() {
                entries.remove(controller.sessionToken)
                runCatching { controller.unregisterCallback(this) }
                publish()
            }
            override fun onSessionEvent(event: String, extras: android.os.Bundle?) = Unit
        }
    }

    init {
        worker.post {
            if (!closed.get()) try {
                manager.addOnActiveSessionsChangedListener(sessionsListener, IslandAccess.component(context), worker)
                replace(manager.getActiveSessions(IslandAccess.component(context)))
            } catch (_: SecurityException) {
                postMain(onAccessLost)
            }
        }
    }

    private fun replace(controllers: List<MediaController>) {
        if (closed.get()) return
        val tokens = controllers.map { it.sessionToken }.toSet()
        entries.keys.filter { it !in tokens }.forEach { token ->
            entries.remove(token)?.let { runCatching { it.controller.unregisterCallback(it.callback) } }
        }
        val ordered = controllers.mapNotNull { controller ->
            val token = controller.sessionToken
            val entry = entries[token] ?: Entry(controller, ++nextId).also {
                try {
                    controller.registerCallback(it.callback, worker)
                } catch (_: RuntimeException) {
                    return@mapNotNull null
                }
            }
            token to entry
        }
        entries.clear()
        entries.putAll(ordered)
        publish()
    }

    private fun publish() {
        if (closed.get()) return
        val snapshots = entries.values.mapNotNull { entry ->
            try {
                val controller = entry.controller
                val metadata = controller.metadata
                val playback = controller.playbackState
                IslandSession(
                    id = entry.id,
                    playback = islandPlayback(playback?.state),
                    title = (metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        ?: metadata?.getText(MediaMetadata.METADATA_KEY_TITLE))?.toString()?.take(MAX_TEXT),
                    artist = (metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.toString()?.take(MAX_TEXT),
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
                    positionMs = playback?.position?.takeIf { it >= 0 },
                    positionAtMs = playback?.lastPositionUpdateTime ?: 0,
                    speed = playback?.playbackSpeed ?: 0f,
                    commands = islandCommands(playback?.actions ?: 0),
                    canOpen = controller.sessionActivity?.isActivity == true,
                    artworkVersion = entry.artworkVersion,
                )
            } catch (_: RuntimeException) { null }
        }
        postMain { onSessions(snapshots) }
    }

    /** Only the selected, visible session gets an owned thumbnail. No URI loading of any kind. */
    fun artwork(id: Long?, version: Long = 0) {
        worker.post {
            val key = id?.let { it to version }
            if (key == artworkKey || closed.get()) return@post
            artworkKey = key
            val request = artworkRequest.incrementAndGet()
            if (id == null) return@post
            val bitmap = runCatching {
                val metadata = entries.values.firstOrNull { it.id == id }?.controller?.metadata
                val source = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                source?.let(::thumbnail)
            }.getOrNull()
            val accent = bitmap?.let { runCatching { islandAccent(it) }.getOrNull() }
            val image = bitmap?.asImageBitmap()
            postMain { if (request == artworkRequest.get()) onArtwork(id, version, image, accent) }
        }
    }

    /** Check current capabilities again at dispatch; never optimistically change player state. */
    fun command(id: Long, command: IslandCommand, seekMs: Long? = null) {
        worker.post {
            if (closed.get()) return@post
            val controller = entries.values.firstOrNull { it.id == id }?.controller ?: return@post
            runCatching {
                if (command !in islandCommands(controller.playbackState?.actions ?: 0)) return@runCatching
                val controls = controller.transportControls
                when (command) {
                    IslandCommand.Play -> controls.play()
                    IslandCommand.Pause -> controls.pause()
                    IslandCommand.Previous -> controls.skipToPrevious()
                    IslandCommand.Next -> controls.skipToNext()
                    IslandCommand.Seek -> {
                        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                        if (duration > 0 && seekMs != null) controls.seekTo(seekMs.coerceIn(0, duration))
                    }
                }
            }.onFailure { publish() }
        }
    }

    fun openPlayer(id: Long, stillVisible: () -> Boolean, onUnavailable: () -> Unit) {
        worker.post {
            if (closed.get()) return@post
            val intent = runCatching { entries.values.firstOrNull { it.id == id }?.controller?.sessionActivity }.getOrNull()
            postMain {
                if (!stillVisible()) return@postMain
                if (intent == null || !intent.isActivity || !sendFromIsland(context, intent)) onUnavailable()
            }
        }
    }

    private fun postMain(action: () -> Unit) {
        main.post { if (!closed.get()) action() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.post {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
            entries.values.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
            entries.clear()
            artworkKey = null
            thread.quitSafely()
        }
    }

    private companion object {
        const val MAX_TEXT = 512
        const val ARTWORK_SIDE = 192
        const val MAX_SOURCE_BYTES = 16 * 1024 * 1024

        fun thumbnail(source: Bitmap): Bitmap? {
            if (source.isRecycled || source.width <= 0 || source.height <= 0 || source.allocationByteCount > MAX_SOURCE_BYTES) return null
            val scale = minOf(1f, ARTWORK_SIDE.toFloat() / maxOf(source.width, source.height))
            // Always own a bounded software copy; never recycle a bitmap owned by MediaController.
            val width = (source.width * scale).roundToInt().coerceAtLeast(1)
            val height = (source.height * scale).roundToInt().coerceAtLeast(1)
            val result = createBitmap(width, height)
            android.graphics.Canvas(result).drawBitmap(source, null, android.graphics.Rect(0, 0, width, height),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return result
        }
    }
}

internal fun islandPlayback(state: Int?): IslandPlayback = when (state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> IslandPlayback.Playing
    PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING, PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> IslandPlayback.Buffering
    PlaybackState.STATE_PAUSED -> IslandPlayback.Paused
    else -> IslandPlayback.Inactive
}

internal fun islandCommands(actions: Long): Set<IslandCommand> = buildSet {
    if (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L) add(IslandCommand.Play)
    if (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L) add(IslandCommand.Pause)
    if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) add(IslandCommand.Previous)
    if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) add(IslandCommand.Next)
    if (actions and PlaybackState.ACTION_SEEK_TO != 0L) add(IslandCommand.Seek)
}
