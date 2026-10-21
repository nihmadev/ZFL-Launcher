package com.zfl.launcher.island

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real blur behind the expanded card: an empty, untouchable accessibility overlay whose window
 * background blurs whatever is below it ([android.view.Window.setBackgroundBlurRadius]).
 *
 * It is attached before the drawing window, so it always sits under the card, and it is only
 * moved while its radius is 0 (invisible): a blurred area never lags behind a morph. Android
 * turns cross-window blur off in battery saver, with "Disable window blurs" or without GPU
 * support; [supported] follows that, and the card is then drawn opaque.
 */
internal class IslandBlur(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val mutableSupported = MutableStateFlow(manager.isCrossWindowBlurEnabled)
    val supported: StateFlow<Boolean> = mutableSupported
    private val listener = Consumer<Boolean> { mutableSupported.value = it }
    private var dialog: Dialog? = null
    private var radius = 0
    /** The window background: its corner radius is the blurred area's corner radius. */
    private val background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }

    init { manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }

    /** Adds the (invisible) blur window; call before adding the drawing window above it. */
    fun attach() {
        if (dialog != null) return
        val created = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        created.setContentView(View(context))
        created.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setBackgroundDrawable(background)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            attributes = attributes.apply {
                gravity = Gravity.TOP or Gravity.START
                width = 1
                height = 1
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = "ZFL Island blur"
            }
        }
        dialog = created
        radius = 0
        runCatching { created.show() }.onFailure { dialog = null }
    }

    /** Places the blur under a settled card; only while invisible, see the class comment. */
    fun place(x: Int, y: Int, width: Int, height: Int, corner: Float) {
        val window = dialog?.window ?: return
        if (radius != 0) return
        if (background.cornerRadius != corner) {
            background.cornerRadius = corner
            window.setBackgroundDrawable(background)
        }
        window.attributes = window.attributes.apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }
    }

    fun radius(px: Int) {
        val window = dialog?.window ?: return
        val next = px.coerceAtLeast(0)
        if (next == radius) return
        radius = next
        window.setBackgroundBlurRadius(next)
    }

    fun detach() {
        dialog?.let { runCatching { it.dismiss() } }
        dialog = null
        radius = 0
    }

    override fun close() {
        detach()
        manager.removeCrossWindowBlurEnabledListener(listener)
    }
}
