package com.zfl.launcher

import com.zfl.launcher.data.LayoutData
import kotlinx.coroutines.flow.StateFlow

/**
 * The one path for Home Screen layout changes made on the home screen. Each change is one
 * transformation of the current layout and one save ([save]).
 *
 * User arrangement changes ([arrange]) are refused while the layout is locked ([locked]),
 * whichever control asked for them. Changes that aren't the user rearranging the screen
 * ([sync]: apps installed or removed, widgets whose app is gone, the widget a stack shows)
 * always go through.
 */
class LayoutWriter(
    private val layout: StateFlow<LayoutData?>,
    private val save: (LayoutData) -> Unit,
    private val locked: () -> Boolean,
) {
    val canArrange: Boolean get() = !locked()

    /** Applies a user change; false when the layout is locked, not loaded, or nothing changed. */
    fun arrange(transform: (LayoutData) -> LayoutData): Boolean = canArrange && commit(transform)

    /** Applies a change the lock doesn't cover; false when not loaded or nothing changed. */
    fun sync(transform: (LayoutData) -> LayoutData): Boolean = commit(transform)

    private fun commit(transform: (LayoutData) -> LayoutData): Boolean {
        val current = layout.value ?: return false
        val next = transform(current)
        if (next == current) return false
        save(next)
        return true
    }
}
