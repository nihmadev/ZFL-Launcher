package com.zfl.launcher.island

import android.app.KeyguardManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.annotation.SuppressLint
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zfl.launcher.ui.theme.IslandTokens
import kotlin.math.roundToInt
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zfl.launcher.settings.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User actions from the Island content, all re-validated by [IslandRuntime]. */
internal class IslandActions(
    val event: (IslandEvent) -> Unit,
    val command: (Long, IslandCommand, Long?) -> Unit,
    val openPlayer: (Long) -> Unit,
    val timer: (String, Int?) -> Unit,
)

/**
 * The Island as two accessibility overlays on the default display, above the status bar:
 *
 * - a fixed, untouchable drawing window around the camera, as wide as the screen and as tall as
 *   the largest card. It is never resized while visible, so a morph cannot jump when a window
 *   resize lands a frame late;
 * - an invisible touch window exactly over the settled shape, forwarding its touches to the
 *   drawing. Touches anywhere else reach the app below; one outside collapses an expanded card.
 */
internal class IslandWindow(
    /** The connected [IslandWindowService]: accessibility overlays belong to it. */
    private val context: Context,
    private val render: StateFlow<IslandRenderState>,
    private val actions: IslandActions,
    private val onFailure: () -> Unit,
) : AutoCloseable {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val blur = IslandBlur(context)
    private val geometry = MutableStateFlow(measure())
    private var view: ComposeView? = null
    private var touch: IslandTouchView? = null
    private var owner: IslandWindowLifecycle? = null
    private var params: WindowManager.LayoutParams? = null
    private var touchParams: WindowManager.LayoutParams? = null
    /** Size of the touch window, the shape's settled bounds. */
    private var shape = IntSize.Zero
    private var closed = false
    private val main = Handler(Looper.getMainLooper())
    /** Backstop for the disappear morph: the window never outlives it if the content stops drawing. */
    private val exitTimeout = Runnable { if (render.value.model.card == IslandCard.Hidden) hide() }
    private val configuration = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            syncGeometry()
            actions.event(IslandEvent.Time)
        }
        @Deprecated("Deprecated by Android; this callback owns no image cache")
        override fun onLowMemory() = Unit
    }

    init { context.registerComponentCallbacks(configuration) }

    private fun measure(): IslandGeometry {
        val metrics = manager.currentWindowMetrics
        val windowInsets = metrics.windowInsets
        val insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val status = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val camera = windowInsets.displayCutout?.boundingRectTop?.takeUnless { it.isEmpty }
        val config = context.resources.configuration
        return islandGeometry(metrics.bounds.width(), metrics.bounds.height(), insets.left, insets.right, insets.bottom,
            status, camera?.centerX(), camera?.centerY(), context.resources.displayMetrics.density, config.fontScale,
            render.value.style)
    }

    /** Camera center on the screen, the horizontal anchor of both windows. */
    private fun anchorX(): Int = manager.currentWindowMetrics.bounds.width() / 2 + geometry.value.centerOffsetPx

    /** The drawing window: the whole screen width and the tallest card, plus the morph overshoot. */
    private fun drawingSize(): IntSize {
        val density = context.resources.displayMetrics.density
        val height = minOf(geometry.value.maxHeightDp, IslandTokens.CardMaxHeight.value) + IslandTokens.MorphSlack.value
        return IntSize(manager.currentWindowMetrics.bounds.width(), (height * density).roundToInt())
    }

    fun syncGeometry(): IslandGeometry {
        val next = measure()
        geometry.value = next
        params?.let { layout ->
            // Called on every event (touches too): relayout only on a real change, or it flickers.
            val size = drawingSize()
            val x = anchorX() - size.width / 2
            if (layout.width != size.width || layout.height != size.height || layout.x != x || layout.y != next.topPx) {
                layout.width = size.width
                layout.height = size.height
                layout.x = x
                layout.y = next.topPx
                view?.let { runCatching { manager.updateViewLayout(it, layout) }.onFailure { hide(); onFailure() } }
            }
        }
        placeTouch()
        return next
    }

    fun update(visible: Boolean) {
        if (closed) return
        val grants = IslandAccess.read(context)
        val unlocked = !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
        if (!geometry.value.available || !grants.window || IslandAccess.windowContext !== context || !grants.notifications || !grants.connected ||
            !unlocked || !context.getSystemService(PowerManager::class.java).isInteractive) {
            hide()
            return
        }
        if (!visible) {
            // The content plays the disappear morph and then calls [exited].
            if (view != null) main.postDelayed(exitTimeout, EXIT_TIMEOUT_MS)
            return
        }
        main.removeCallbacks(exitTimeout)
        if (view != null) return
        val lifecycle = IslandWindowLifecycle()
        val compose = ComposeView(AppLanguage.wrap(context)).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { IslandContent(render, geometry, blur.supported, actions, ::resize, ::glass, ::exited) }
        }
        val size = drawingSize()
        val layout = overlay(size, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            x = anchorX() - size.width / 2
            title = "ZFL Island"
        }
        val touchView = IslandTouchView(context, { view }, ::touchOffset) {
            if (render.value.model.card.expanded) actions.event(IslandEvent.Collapse)
        }
        owner = lifecycle
        view = compose
        params = layout
        touch = touchView
        try {
            lifecycle.registry.currentState = Lifecycle.State.STARTED
            // Under the drawing window: windows of one type stack in the order they are added.
            blur.attach()
            manager.addView(compose, layout)
        } catch (_: RuntimeException) {
            hide()
            onFailure()
        }
    }

    private fun overlay(size: IntSize, flags: Int): WindowManager.LayoutParams = WindowManager.LayoutParams(
        size.width, size.height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        y = geometry.value.topPx
        // Placed by [islandGeometry] around the camera, inside the status bar area.
        setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    /** Where the touch window sits inside the drawing window. */
    private fun touchOffset(): IntOffset {
        val drawing = params ?: return IntOffset.Zero
        val layout = touchParams ?: return IntOffset.Zero
        return IntOffset(layout.x - drawing.x, layout.y - drawing.y)
    }

    /** The shape's frame changed (a morph started or settled): only the touch window follows it. */
    private fun resize(size: IntSize) {
        shape = size
        placeTouch()
    }

    private fun placeTouch() {
        val touchView = touch ?: return
        if (view == null || shape == IntSize.Zero) return
        val layout = touchParams
        if (layout == null) {
            val created = overlay(shape, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).apply {
                x = anchorX() - shape.width / 2
                title = "ZFL Island touch"
            }
            touchParams = created
            runCatching { manager.addView(touchView, created) }.onFailure { hide(); onFailure() }
            return
        }
        val x = anchorX() - shape.width / 2
        val y = geometry.value.topPx
        if (layout.width == shape.width && layout.height == shape.height && layout.x == x && layout.y == y) return
        layout.width = shape.width
        layout.height = shape.height
        layout.x = x
        layout.y = y
        runCatching { manager.updateViewLayout(touchView, layout) }.onFailure { hide(); onFailure() }
    }

    /**
     * Blur behind a settled card: [progress] 0 hides it, up to 1 at full radius. [size] is the
     * card, centered on the camera like the drawing.
     */
    private fun glass(progress: Float, size: IntSize) {
        val density = context.resources.displayMetrics.density
        if (progress > 0f) {
            val inset = (IslandTokens.Edge.value * 2 * density).roundToInt()
            blur.place(anchorX() - size.width / 2 + inset, geometry.value.topPx + inset, size.width - inset * 2,
                size.height - inset * 2, IslandTokens.CardRadius.value * density)
        }
        blur.radius((progress * IslandTokens.BlurRadius.value * density).roundToInt())
    }

    /** The disappear morph finished; remove the window unless something appeared meanwhile. */
    private fun exited() {
        if (render.value.model.card == IslandCard.Hidden) hide()
    }

    private fun hide() {
        main.removeCallbacks(exitTimeout)
        val oldView = view
        val oldTouch = touch
        view = null
        touch = null
        params = null
        touchParams = null
        shape = IntSize.Zero
        owner?.registry?.currentState = Lifecycle.State.DESTROYED
        owner = null
        oldTouch?.let { if (it.isAttachedToWindow) runCatching { manager.removeViewImmediate(it) } }
        blur.detach()
        oldView?.let {
            it.disposeComposition()
            if (it.isAttachedToWindow) runCatching { manager.removeViewImmediate(it) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        hide()
        blur.close()
        context.unregisterComponentCallbacks(configuration)
    }
}

/**
 * Invisible window over the shape: hands its touches to the drawing window at the matching
 * position. The drawing window itself is untouchable, so it never blocks the screen below.
 */
@SuppressLint("ViewConstructor")
private class IslandTouchView(
    context: Context,
    private val target: () -> View?,
    private val offset: () -> IntOffset,
    private val outside: () -> Unit,
) : View(context) {
    @SuppressLint("ClickableViewAccessibility") // The drawing window's content owns semantics and clicks.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            outside()
            return true
        }
        val drawing = target() ?: return false
        val shift = offset()
        val forwarded = MotionEvent.obtain(event)
        forwarded.offsetLocation(shift.x.toFloat(), shift.y.toFloat())
        drawing.dispatchTouchEvent(forwarded)
        forwarded.recycle()
        return true
    }
}

private const val EXIT_TIMEOUT_MS = 1_500L

/** New owner per attached window; there is no resumed lifecycle or saved media state while hidden. */
private class IslandWindowLifecycle : SavedStateRegistryOwner {
    val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }
}
