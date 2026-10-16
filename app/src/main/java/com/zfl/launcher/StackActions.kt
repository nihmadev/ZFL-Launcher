package com.zfl.launcher

import com.zfl.launcher.data.LayoutData
import com.zfl.launcher.data.PageWidget
import com.zfl.launcher.data.WidgetStack
import com.zfl.launcher.data.minus
import com.zfl.launcher.data.moved
import com.zfl.launcher.data.showing
import com.zfl.launcher.data.takeOutOfStack
import com.zfl.launcher.data.updateStack
import com.zfl.launcher.data.findStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Smart Stack actions of the home screen. Every user action is one layout change through
 * the repository ([update]), so one save; swiping saves only once the stack settles.
 */
class StackActions internal constructor(
    private val layout: StateFlow<LayoutData?>,
    private val update: (LayoutData) -> Unit,
    private val pageCapacity: () -> Int,
    private val dockCapacity: () -> Int,
    private val release: (PageWidget) -> Unit,
    private val closeMenu: () -> Unit,
) {
    /** Id of the stack whose "Edit Stack" sheet is open, or null. */
    val editing: MutableStateFlow<String?> = MutableStateFlow(null)

    fun openEditor(id: String) {
        closeMenu()
        editing.value = id
    }

    fun closeEditor() {
        editing.value = null
    }

    /** The user swiped stack [id] to the widget at [index]. */
    fun show(id: String, index: Int) = change(id) { it.showing(index) }

    fun move(id: String, from: Int, to: Int) = change(id) { it.moved(from, to) }

    /** Removes widget [itemId] from the stack and frees it (a stack left with one widget becomes it). */
    fun remove(id: String, itemId: String) {
        val item = layout.value?.findStack(id)?.items?.firstOrNull { it.id == itemId } ?: return
        change(id) { it.minus(itemId) }
        release(item)
        if (layout.value?.findStack(id) == null) closeEditor()
    }

    /** Puts widget [itemId] on the page next to the stack. */
    fun takeOut(id: String, itemId: String) {
        val current = layout.value ?: return
        val next = current.takeOutOfStack(id, itemId, pageCapacity(), dockCapacity())
        if (next != current) update(next)
        if (next.findStack(id) == null) closeEditor()
    }

    private fun change(id: String, transform: (WidgetStack) -> WidgetStack) {
        val current = layout.value ?: return
        val next = current.updateStack(id, pageCapacity(), dockCapacity(), transform)
        if (next != current) update(next)
    }
}
