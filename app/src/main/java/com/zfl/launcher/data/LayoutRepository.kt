package com.zfl.launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide owner of the Home Screen layout. The home screen edits it and the settings
 * screen can replace it (layout import), so both must share one instance.
 */
class LayoutRepository private constructor(context: Context) {
    val store: LayoutStore = LayoutStore(context.applicationContext)

    /** Writes happen one at a time, in order. */
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _layout = MutableStateFlow<LayoutData?>(null)
    /** The current layout; null until [load] finished (and on a fresh install). */
    val layout: StateFlow<LayoutData?> = _layout

    private var loaded = false

    /** Reads the saved layout once; later calls return the in-memory one. */
    suspend fun load(): LayoutData? {
        if (!loaded) {
            val saved = withContext(io) { store.load() }
            if (_layout.value == null) _layout.value = saved
            loaded = true
        }
        return _layout.value
    }

    fun update(next: LayoutData) {
        _layout.value = next
        scope.launch {
            runCatching { store.save(next) }.onFailure { Log.w(TAG, "Couldn't save the layout", it) }
        }
    }

    /**
     * Moves the layout to [grid] ([withGrid]): one transformation and one save, grid and cells
     * in the same file. Nothing changes when an item doesn't fit ([GridChange.Blocked]).
     *
     * @param initial layout to start from when none was saved yet (a fresh install).
     */
    suspend fun changeGrid(grid: GridSpec, initial: () -> LayoutData): GridChange {
        val current = load() ?: initial()
        val change = current.withGrid(grid)
        if (change is GridChange.Applied && change.layout != _layout.value) update(change.layout)
        return change
    }

    companion object {
        private const val TAG = "LayoutRepository"

        @Volatile
        private var instance: LayoutRepository? = null

        fun get(context: Context): LayoutRepository =
            instance ?: synchronized(this) { instance ?: LayoutRepository(context).also { instance = it } }
    }
}
