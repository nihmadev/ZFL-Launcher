package com.zfl.launcher.ui.home

import com.zfl.launcher.data.PageWidgetEntry
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.pointer.PointerEvent
import com.zfl.launcher.data.AppEntry
import com.zfl.launcher.data.DEFAULT_DOCK_CAPACITY
import com.zfl.launcher.data.DropPosition
import com.zfl.launcher.data.FolderEntry
import com.zfl.launcher.data.HomeEntry
import com.zfl.launcher.data.HomeState
import com.zfl.launcher.data.PlaceholderEntry
import com.zfl.launcher.data.boundedCell
import com.zfl.launcher.data.cascadePages
import com.zfl.launcher.data.itemAtCell
import com.zfl.launcher.data.packCells
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.span
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a dragged item was picked up. */
enum class DragSource { Page, Dock, Folder }

/** An item being dragged. [pointer] is in window coordinates. */
@Stable
class DragSession(
    val entry: HomeEntry,
    val source: DragSource,
    /** Finger position relative to the icon's top-left corner, so the icon doesn't jump. */
    val touchOffset: Offset,
    val start: Offset,
    /** Size of the picked-up icon or widget card, in px. */
    val itemSize: Size,
) {
    var pointer: Offset by mutableStateOf(start)
    /** False while it's only a long-press (menu open); true once the finger moved. */
    var active: Boolean by mutableStateOf(false)

    /**
     * The point that decides where the item goes: the visual center of a dragged icon, so
     * putting it on top of another icon works no matter where the icon was grabbed.
     * Widgets keep following the finger, since their card spans several cells.
     */
    val hotspot: Offset
        get() = if (entry is PageWidgetEntry) pointer else pointer - touchOffset + itemSize.center
}

/** Root-level drag tracking, exact cell previews, folder merging and edge page flips. */
@Stable
class DragController(
    private val scope: CoroutineScope,
    private val slopPx: Float,
    private val edgePx: Float,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** The finger moved after a long-press: close the menu and enter edit mode. */
        fun onDragActivated()
        fun onDrop(id: String, position: DropPosition)
        fun onFlipPage(page: Int)
        /** The finger left the open folder: close it and keep dragging on the home screen. */
        fun onLeaveFolder()
    }

    var session: DragSession? by mutableStateOf(null)
        private set

    /** Where the item lands if released now; null = back to where it came from. */
    var target: DropPosition? by mutableStateOf(null)
        private set

    /** Item under the finger that would become (or receive) a folder. */
    var mergeCandidate: String? by mutableStateOf(null)
        private set
    var mergeArmed: Boolean by mutableStateOf(false)
        private set

    // Inputs kept up to date by the screen.
    var state: HomeState = HomeState()
    /** Home page under the finger; -1 for the Today page. */
    var currentPage: Int = 0
    var screenWidth: Float = 0f
    var openFolder: FolderEntry? = null
    var pageGrid: GridGeometry? = null
    var dockBounds: Rect? = null
    /** Dock slots chosen in settings. */
    var dockCapacity: Int = DEFAULT_DOCK_CAPACITY
    var folderPanel: Rect? = null
    var folderGrid: GridGeometry? = null
    var folderPage: Int = 0

    private var mergeJob: Job? = null
    private var reorderJob: Job? = null
    /** Latest slot under the finger; icons move there only after a rest, the drop marker at once. */
    var pendingTarget: DropPosition? by mutableStateOf(null)
        private set
    private var restPoint: Offset = Offset.Zero
    private var edgeJob: Job? = null
    private var leaveFolderJob: Job? = null
    private var dropOrigin: Pair<String, Offset>? = null

    val isDragging: Boolean get() = session?.active == true
    val draggedId: String? get() = session?.takeIf { it.active }?.entry?.id

    /** Long-press in normal mode: remember the item; dragging begins only if the finger moves. */
    fun prepare(entry: HomeEntry, source: DragSource, iconBounds: Rect, pointer: Offset) {
        session = DragSession(entry, source, pointer - iconBounds.topLeft, pointer, iconBounds.size)
    }

    /** Edit mode: the finger already moved past the touch slop, so drag right away. */
    fun start(entry: HomeEntry, source: DragSource, iconBounds: Rect, pointer: Offset) {
        session = DragSession(entry, source, pointer - iconBounds.topLeft, pointer, iconBounds.size).also { it.active = true }
        target = initialTarget(entry, source)
    }

    /** Root pointer handler (Initial pass). Consumes events while a drag is in progress. */
    fun onPointerEvent(event: PointerEvent) {
        val s = session ?: return
        val change = event.changes.firstOrNull() ?: return
        if (!change.pressed) {
            if (s.active) drop(s) else session = null
            return
        }
        s.pointer = change.position
        if (!s.active && (change.position - s.start).getDistance() > slopPx) {
            s.active = true
            target = initialTarget(s.entry, s.source)
            callbacks.onDragActivated()
        }
        if (s.active) updateTarget(s)
        change.consume()
    }

    /** One-shot: where the item was released, so its new slot can animate from there. */
    fun takeDropOrigin(id: String): Offset? =
        dropOrigin?.takeIf { it.first == id }?.second?.also { dropOrigin = null }

    // region Preview lists

    fun previewPages(position: DropPosition? = target): List<List<HomeEntry>> {
        val dragged = session?.takeIf { it.active }?.entry ?: return state.pages
        val pages = state.pages.map { page -> page.filter { it.id != dragged.id } }.toMutableList()
        // An extra empty page at the end lets the user drop on a new page.
        if (pages.last().isNotEmpty()) pages += emptyList<HomeEntry>()
        (position as? DropPosition.PageCell)?.let { t ->
            if (t.page in pages.indices) {
                // The gap is as big as what is dragged: a widget opens a widget-sized hole.
                pages[t.page] = pages[t.page].toMutableList().apply { add(0, PlaceholderEntry(dragged.span)) }
            }
        }
        val cells = previewCells(position)
        return cascadePages(pages, Tokens.GridColumns * state.rows, dropEmpty = false, cell = { cells[it.id] }) { it.span }
    }

    /** The marker and preview use the same reservations as the persisted move. */
    fun previewCells(position: DropPosition? = target): Map<String, Int> {
        val dragged = session?.takeIf { it.active }?.entry ?: return state.cells
        val rest = state.cells - dragged.id
        val t = position as? DropPosition.PageCell ?: return rest
        return rest + ("placeholder" to boundedCell(t.cell, dragged.span, state.rows))
    }

    fun previewDock(position: DropPosition? = target): List<HomeEntry> {
        val dragged = draggedId ?: return state.dock
        val dock = state.dock.filter { it.id != dragged }.toMutableList()
        (position as? DropPosition.Dock)?.let { dock.add(it.index.coerceIn(0, dock.size), PlaceholderEntry()) }
        return dock
    }

    fun previewFolder(folder: FolderEntry, position: DropPosition? = target): List<HomeEntry> {
        val apps: MutableList<HomeEntry> = folder.apps.map(::AppEntry).filter { it.id != draggedId }.toMutableList()
        (position as? DropPosition.InFolder)?.takeIf { it.folderId == folder.id }?.let {
            apps.add(it.index.coerceIn(0, apps.size), PlaceholderEntry())
        }
        return apps
    }

    // region Drop marker

    /** Where the item lands if released now (no marker while it would make a folder). */
    private val markerTarget: DropPosition?
        get() = if (session?.active != true || mergeCandidate != null) null else pendingTarget ?: target

    /** Cell and footprint of the drop marker on home [page], or null if it is elsewhere. */
    fun pageMarker(page: Int): Pair<Int, Span>? {
        val t = markerTarget as? DropPosition.PageCell ?: return null
        if (t.page != page) return null
        val items = previewPages(t).getOrNull(page) ?: return null
        val index = items.indexOfFirst { it is PlaceholderEntry }.takeIf { it >= 0 } ?: return null
        val spans = items.map { it.span }
        val positions = previewCells(t)
        val cell = packCells(spans, Tokens.GridColumns, state.rows, items.map { positions[it.id] })[index] ?: return null
        return cell to spans[index]
    }

    /** Dock slot of the drop marker and the number of slots it is spread over. */
    fun dockMarker(): Pair<Int, Int>? {
        val t = markerTarget as? DropPosition.Dock ?: return null
        val items = previewDock(t)
        return items.indexOfFirst { it is PlaceholderEntry }.takeIf { it >= 0 }?.let { it to items.size }
    }

    /** Index of the drop marker among [folder]'s apps. */
    fun folderMarker(folder: FolderEntry): Int? {
        val t = markerTarget as? DropPosition.InFolder ?: return null
        return previewFolder(folder, t).indexOfFirst { it is PlaceholderEntry }.takeIf { it >= 0 }
    }

    // endregion

    private fun initialTarget(entry: HomeEntry, source: DragSource): DropPosition? = when (source) {
        DragSource.Folder -> openFolder?.let { folder ->
            DropPosition.InFolder(folder.id, folder.apps.indexOfFirst { it.key == entry.id }.coerceAtLeast(0))
        }
        DragSource.Dock -> DropPosition.Dock(state.dock.indexOfFirst { it.id == entry.id }.coerceAtLeast(0))
        DragSource.Page -> state.pages.withIndex().firstNotNullOfOrNull { (page, items) ->
            items.indexOfFirst { it.id == entry.id }.takeIf { it >= 0 }?.let { DropPosition.PageCell(page, state.cells[entry.id] ?: it) }
        }
    }

    private fun updateTarget(s: DragSession) {
        val p = s.hotspot

        // 1. Inside an open folder.
        val folder = openFolder
        val panel = folderPanel
        if (folder != null && panel != null) {
            if (panel.contains(p)) {
                leaveFolderJob?.cancel()
                leaveFolderJob = null
                folderGrid?.let { grid ->
                    val visible = previewFolder(folder)
                    val cell = grid.cellAt(p) + folderPage * FOLDER_PAGE_SIZE
                    setMerge(null)
                    if (visible.getOrNull(cell) !is PlaceholderEntry) {
                        retarget(DropPosition.InFolder(folder.id, cell.coerceAtMost(visible.size - 1).coerceAtLeast(0)))
                    }
                }
            } else if (leaveFolderJob == null) {
                leaveFolderJob = scope.launch {
                    delay(LEAVE_FOLDER_DELAY_MS)
                    leaveFolderJob = null
                    callbacks.onLeaveFolder()
                }
            }
            return
        }

        // On the Today page nothing can be dropped (widgets there are arranged from the menu).
        if (currentPage < 0) {
            setMerge(null)
            retarget(null)
            return
        }

        // 2. Over the dock (widgets don't go there).
        val dock = dockBounds
        if (dock != null && p.y >= dock.top && s.entry !is PageWidgetEntry) {
            cancelEdgeFlip()
            updateDockTarget(s, dock, p)
            return
        }

        // 3. Over a home screen page.
        val grid = pageGrid ?: return
        // Page flipping follows the finger itself: that's what the user pushes against the edge.
        handlePageEdge(s.pointer)
        val pages = previewPages()
        val page = currentPage.coerceIn(0, pages.lastIndex)
        val visible = pages[page]
        val spans = visible.map { it.span }
        val positions = previewCells()
        val cells = packCells(spans, grid.columns, grid.rows, visible.map { positions[it.id] })
        val cell = grid.cellAt(p)
        val anchor = if (s.entry is PageWidgetEntry) {
            grid.cellAt(s.pointer - s.touchOffset + Offset(grid.cellWidth / 2, grid.cellHeight / 2))
        } else cell
        val destination = DropPosition.PageCell(page, boundedCell(anchor, s.entry.span, grid.rows, grid.columns))
        val underIndex = itemAtCell(spans, cells, cell, grid.columns)
        val under = underIndex?.let(visible::get)
        when {
            under == null -> {
                setMerge(null)
                retarget(destination)
            }
            under is PlaceholderEntry -> {
                setMerge(null)
                retarget(destination)
            }
            canMerge(s, under) && isOver(under, (p - grid.iconCenter(cells[underIndex]!!)).getDistance(), grid.iconSize) -> {
                retarget(null)
                setMerge(under.id)
            }
            else -> {
                setMerge(null)
                retarget(destination, REORDER_OVER_ICON_DELAY_MS)
            }
        }
    }

    private fun updateDockTarget(s: DragSession, dock: Rect, p: Offset) {
        val others = state.dock.filter { it.id != s.entry.id }
        val canInsert = s.source == DragSource.Dock || others.size < dockCapacity
        val slots = if (canInsert) others.size + 1 else others.size
        val slotWidth = dock.width / slots.coerceAtLeast(1)
        val slot = ((p.x - dock.left) / slotWidth).toInt().coerceIn(0, (slots - 1).coerceAtLeast(0))

        // Hovering the middle of a dock icon makes a folder there too.
        val preview = previewDock()
        val under = preview.getOrNull(((p.x - dock.left) / (dock.width / preview.size.coerceAtLeast(1))).toInt())
        val underCenterX = preview.indexOf(under).let { dock.left + (it + 0.5f) * dock.width / preview.size.coerceAtLeast(1) }
        if (under != null && canMerge(s, under) &&
            isOver(under, kotlin.math.abs(p.x - underCenterX), pageGrid?.iconSize ?: dock.height)
        ) {
            retarget(null)
            setMerge(under.id)
            return
        }
        setMerge(null)
        if (canInsert) retarget(
            DropPosition.Dock(slot),
            if (under != null && under !is PlaceholderEntry) REORDER_OVER_ICON_DELAY_MS else REORDER_DELAY_MS,
        )
    }

    /**
     * Icons make room only after the finger rests on a new slot for a moment, like iOS.
     * Without this delay icons would slide away before you could reach their center
     * to make a folder. `null` cancels a pending change and keeps the current target.
     */
    private fun retarget(position: DropPosition?, delayMs: Long = REORDER_DELAY_MS) {
        val pointer = session?.pointer ?: return
        // Any real movement postpones the reflow: icons only make room once the finger rests.
        val moved = (pointer - restPoint).getDistance() > slopPx / 2
        if (moved) restPoint = pointer
        if (position == pendingTarget && !moved) return
        pendingTarget = position
        reorderJob?.cancel()
        if (position == null || position == target) return
        reorderJob = scope.launch {
            delay(delayMs)
            target = position
        }
    }

    private fun canMerge(s: DragSession, under: HomeEntry): Boolean =
        s.entry is AppEntry && under.id != s.entry.id && under !is PlaceholderEntry && under !is PageWidgetEntry

    /**
     * Whether the dragged icon covers an icon whose center is [distance] px away. The zone is
     * big (the icons overlap by about half) and sticky: once a target is picked, it is
     * released only past a wider radius, so small finger jitter never flips the intent.
     */
    private fun isOver(under: HomeEntry, distance: Float, iconSize: Float): Boolean =
        distance < iconSize * if (under.id == mergeCandidate) MERGE_EXIT_RADIUS else MERGE_ENTER_RADIUS

    /** Dropping into an existing folder is unambiguous, so it arms at once; two apps need a short hover. */
    private fun setMerge(id: String?) {
        if (id == mergeCandidate) return
        mergeCandidate = id
        mergeJob?.cancel()
        mergeArmed = id != null && previewEntry(id) is FolderEntry
        if (id != null && !mergeArmed) {
            mergeJob = scope.launch {
                delay(MERGE_DELAY_MS)
                mergeArmed = true
            }
        }
    }

    private fun previewEntry(id: String): HomeEntry? =
        state.dock.firstOrNull { it.id == id } ?: state.pages.firstNotNullOfOrNull { page -> page.firstOrNull { it.id == id } }

    /** Holding the item at the left/right screen edge flips to the neighbor page. */
    private fun handlePageEdge(p: Offset) {
        val direction = when {
            p.x < edgePx -> -1
            p.x > screenWidth - edgePx -> 1
            else -> 0
        }
        if (direction == 0) {
            cancelEdgeFlip()
            return
        }
        if (edgeJob != null) return
        edgeJob = scope.launch {
            while (true) {
                delay(EDGE_FLIP_DELAY_MS)
                val next = currentPage + direction
                // Never flip into the Today page (-1) while dragging.
                if (next < 0 || next >= previewPages().size) break
                callbacks.onFlipPage(next)
                currentPage = next
                session?.let { if (it.active) updateTarget(it) }
            }
            edgeJob = null
        }
    }

    private fun cancelEdgeFlip() {
        edgeJob?.cancel()
        edgeJob = null
    }

    private fun drop(s: DragSession) {
        val position = if (mergeArmed) mergeCandidate?.let { DropPosition.Merge(it) } else null
        // Land where the marker shows, even if the icons haven't made room yet.
        val finalPosition = position ?: pendingTarget ?: target
        dropOrigin = s.entry.id to (s.pointer - s.touchOffset)
        reset()
        if (finalPosition != null) callbacks.onDrop(s.entry.id, finalPosition)
    }

    private fun reset() {
        reorderJob?.cancel()
        pendingTarget = null
        session = null
        target = null
        setMerge(null)
        cancelEdgeFlip()
        leaveFolderJob?.cancel()
        leaveFolderJob = null
    }

    companion object {
        const val FOLDER_PAGE_SIZE = 9
        /** The dragged icon's center this close to an icon center (fraction of icon size) targets a folder. */
        private const val MERGE_ENTER_RADIUS = 0.55f
        /** A picked folder target is kept until the dragged icon moves this far away. */
        private const val MERGE_EXIT_RADIUS = 0.8f
        private const val MERGE_DELAY_MS = 200L
        /** Rest needed to reflow over empty space. */
        private const val REORDER_DELAY_MS = 180L
        /** Rest needed to push aside an icon the dragged one only partly covers. */
        private const val REORDER_OVER_ICON_DELAY_MS = 380L
        private const val EDGE_FLIP_DELAY_MS = 550L
        private const val LEAVE_FOLDER_DELAY_MS = 350L
    }
}
