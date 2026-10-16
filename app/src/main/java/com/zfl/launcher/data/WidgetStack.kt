package com.zfl.launcher.data

/** Most widgets one stack holds, like iOS. */
const val STACK_MAX_ITEMS: Int = 10

/**
 * A Smart Stack: widgets of one [size] sharing one grid slot, swiped vertically.
 * Holds built-in and hosted widgets but never another stack.
 *
 * @param current index of the widget shown, remembered across restarts like iOS.
 */
data class WidgetStack(
    override val id: String,
    val size: WidgetSize,
    val items: List<PageWidget>,
    val current: Int = 0,
) : PageWidget {
    override val span: Span get() = size.span

    val currentItem: PageWidget? get() = items.getOrNull(current)
}

/** The stack size a widget fits, or null when it can't go into a stack (a stack itself, a 4×1 widget…). */
val PageWidget.stackSize: WidgetSize?
    get() = when (this) {
        is WidgetStack -> null
        is WidgetItem -> size
        is AppWidgetItem -> WidgetSize.entries.firstOrNull { it.span == span }
    }

/** Whether [widget] may join a stack of [size] that already has [count] widgets. */
fun canJoinStack(widget: PageWidget, size: WidgetSize, count: Int): Boolean =
    widget.stackSize == size && count < STACK_MAX_ITEMS

/**
 * Drops wrong-sized and nested items and clamps [WidgetStack.current]. A stack of one
 * widget becomes that widget; an empty one disappears (null).
 */
fun WidgetStack.normalizedStack(): PageWidget? {
    val valid = items.filter { it.stackSize == size }.distinctBy { it.id }.take(STACK_MAX_ITEMS)
    return when (valid.size) {
        0 -> null
        1 -> valid.single()
        else -> copy(items = valid, current = current.coerceIn(0, valid.lastIndex))
    }
}

/** A stack made by dropping [dropped] onto [target]; the dropped widget is shown, as in iOS. Null when not allowed. */
fun stackOf(target: PageWidget, dropped: PageWidget, id: String): WidgetStack? {
    val size = target.stackSize ?: return null
    if (dropped.id == target.id || !canJoinStack(dropped, size, 1)) return null
    return WidgetStack(id, size, listOf(dropped, target), current = 0)
}

/** Adds [widget] in front of the shown one and shows it; null when it doesn't fit (size, limit, duplicate). */
fun WidgetStack.plus(widget: PageWidget): WidgetStack? {
    if (items.any { it.id == widget.id } || !canJoinStack(widget, size, items.size)) return null
    val at = current.coerceIn(0, items.size)
    return copy(items = items.toMutableList().apply { add(at, widget) }, current = at)
}

/** Without the widget [itemId]; the shown widget stays shown when it is another one. */
fun WidgetStack.minus(itemId: String): WidgetStack {
    val index = items.indexOfFirst { it.id == itemId }
    if (index < 0) return this
    val next = if (index < current) current - 1 else current
    return copy(items = items.filterIndexed { i, _ -> i != index }, current = next.coerceIn(0, (items.size - 2).coerceAtLeast(0)))
}

/** Moves the widget at [from] to [to]; the shown widget stays the same one. */
fun WidgetStack.moved(from: Int, to: Int): WidgetStack {
    if (from !in items.indices) return this
    val shown = currentItem?.id
    val list = items.toMutableList()
    val item = list.removeAt(from)
    list.add(to.coerceIn(0, list.size), item)
    return copy(items = list, current = list.indexOfFirst { it.id == shown }.coerceAtLeast(0))
}

fun WidgetStack.showing(index: Int): WidgetStack = copy(current = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))

/** The same stack at [newSize], or null unless every widget supports it ([supports]). */
fun WidgetStack.resizedTo(newSize: WidgetSize, supports: (PageWidget, WidgetSize) -> Boolean): WidgetStack? {
    if (!items.all { supports(it, newSize) }) return null
    return copy(size = newSize, items = items.map { it.withSize(newSize) })
}

private fun PageWidget.withSize(size: WidgetSize): PageWidget = when (this) {
    is WidgetItem -> copy(size = size)
    is AppWidgetItem -> copy(span = size.span)
    is WidgetStack -> this
}

/** Built-in widgets that make a useful Smart Stack of [size]; a Photo widget needs a picked photo, so it is left out. */
fun smartStackKinds(size: WidgetSize): List<WidgetKind> =
    WidgetKind.entries.filter { size in it.sizes && it != WidgetKind.Photo }

/** Sizes for which the gallery offers a Smart Stack (at least two widgets fit). */
val SMART_STACK_SIZES: List<WidgetSize> = WidgetSize.entries.filter { smartStackKinds(it).size >= 2 }

/** A new Smart Stack of every built-in widget of [size] from [smartStackKinds]; ids are derived from [id]. */
fun smartStack(id: String, size: WidgetSize): WidgetStack =
    WidgetStack(id, size, smartStackKinds(size).map { WidgetItem("$id-${it.name.lowercase()}", it, size) })

/** Stack size of a drawn widget; null for stacks and sizes no stack has. */
val PageWidgetEntry.stackSize: WidgetSize?
    get() = when (this) {
        is WidgetStackEntry -> null
        is WidgetEntry -> size
        is AppWidgetEntry -> WidgetSize.entries.firstOrNull { it.span == span }
    }

/**
 * Whether dropping [dragged] on [target] makes a stack (a widget of the same size) or adds
 * to one (a stack of that size with room left). Anything else is an ordinary move.
 */
fun canStackOnto(dragged: HomeEntry, target: HomeEntry): Boolean {
    if (dragged !is PageWidgetEntry || dragged.id == target.id) return false
    val size = dragged.stackSize ?: return false
    return when (target) {
        is WidgetStackEntry -> target.size == size && target.items.size < STACK_MAX_ITEMS
        is PageWidgetEntry -> target.stackSize == size
        else -> false
    }
}

// region Layout operations

/** Top-level widgets and every widget inside a stack. */
internal fun List<PageWidget>.withStackItems(): List<PageWidget> =
    flatMap { if (it is WidgetStack) listOf(it) + it.items else listOf(it) }

/** The stack holding [itemId] anywhere in the layout. */
fun LayoutData.stackHolding(itemId: String): WidgetStack? =
    (pages.flatten() + today).firstNotNullOfOrNull { item -> (item as? WidgetStack)?.takeIf { s -> s.items.any { it.id == itemId } } }

fun LayoutData.findStack(id: String): WidgetStack? =
    (pages.flatten() + today).firstOrNull { it.id == id } as? WidgetStack

/**
 * Replaces the stack [id] (on a page or Today) with [transform]'s result, normalized: a
 * stack left with one widget becomes it, in the stack's cell. Unchanged when it's missing.
 */
fun LayoutData.updateStack(id: String, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY, transform: (WidgetStack) -> WidgetStack): LayoutData {
    val stack = findStack(id) ?: return this
    val next = transform(stack).normalizedStack()
    return replaceTopLevel(id, next).normalized(pageCapacity, dockCapacity)
}

/** Drops widget [dropped] (top level, on a page) onto widget or stack [targetId]: makes or grows a stack there. */
internal fun LayoutData.dropOnWidget(dropped: PageWidget, targetId: String, newStackId: String, pageCapacity: Int, dockCapacity: Int): LayoutData {
    if (dropped is WidgetStack || dropped.id == targetId) return this
    val target = pages.flatten().firstOrNull { it.id == targetId } as? PageWidget ?: return this
    val merged = when (target) {
        is WidgetStack -> target.plus(dropped)
        else -> stackOf(target, dropped, newStackId)
    } ?: return this
    return without(dropped.id).replaceTopLevel(targetId, merged).normalized(pageCapacity, dockCapacity)
}

/**
 * Takes widget [itemId] out of its stack onto the same page (or Today), right after the
 * stack; the page reflows to fit it.
 */
fun LayoutData.takeOutOfStack(stackId: String, itemId: String, pageCapacity: Int, dockCapacity: Int = DEFAULT_DOCK_CAPACITY): LayoutData {
    val stack = findStack(stackId) ?: return this
    val item = stack.items.firstOrNull { it.id == itemId } ?: return this
    val rest = stack.minus(itemId).normalizedStack()
    fun insert(list: List<HomeItem>): List<HomeItem> = list.flatMap { if (it.id == stackId) listOfNotNull(rest, item) else listOf(it) }
    val inherited = if (rest != null && rest.id != stackId) cells[stackId]?.let { mapOf(rest.id to it) }.orEmpty() else emptyMap()
    return copy(
        pages = pages.map(::insert),
        today = today.flatMap { if (it.id == stackId) listOfNotNull(rest, item) else listOf(it) },
        cells = cells + inherited,
    ).normalized(pageCapacity, dockCapacity)
}

/** Swaps the top-level item [id] for [next] (null removes it); a new id inherits the old cell. */
private fun LayoutData.replaceTopLevel(id: String, next: PageWidget?): LayoutData {
    fun swap(list: List<HomeItem>): List<HomeItem> = list.mapNotNull { if (it.id == id) next else it }
    val nextCells = cells.toMutableMap()
    cells[id]?.let { cell ->
        nextCells.remove(id)
        if (next != null) nextCells[next.id] = cell
    }
    return copy(pages = pages.map(::swap), today = today.mapNotNull { if (it.id == id) next else it }, cells = nextCells)
}

/**
 * [item] without the stack element [id]; null when that leaves nothing. Used by
 * [without], so uninstalling an app removes only its widget from a stack.
 */
internal fun HomeItem.withoutStackItem(id: String): HomeItem? =
    if (this is WidgetStack && items.any { it.id == id }) minus(id).normalizedStack() else this

// endregion
