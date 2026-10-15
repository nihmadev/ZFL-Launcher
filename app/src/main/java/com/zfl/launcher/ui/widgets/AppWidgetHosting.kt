package com.zfl.launcher.ui.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserManager
import android.util.LruCache
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.drawToBitmap
import com.zfl.launcher.data.Span
import com.zfl.launcher.data.appWidgetSpans
import kotlin.math.ceil

/** An app that provides widgets, for the gallery. */
data class WidgetProviderGroup(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val providers: List<AppWidgetProviderInfo>,
)

/**
 * Hosts other apps' widgets (Android's AppWidget framework): one AppWidgetHost for the
 * process, widget ids, provider lookup and the size in grid cells each widget gets.
 * The home activity calls [startListening]/[stopListening] with its lifecycle.
 */
class AppWidgets private constructor(context: Context) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host: AppWidgetHost = ZflWidgetHost(appContext, HOST_ID)

    fun startListening() {
        // Throws if the system's widget service is unavailable (e.g. right after boot).
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun isAlive(appWidgetId: Int): Boolean = info(appWidgetId) != null

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** Ids this host holds that the layout doesn't use anymore (e.g. an add that was interrupted). */
    fun orphanIds(used: Set<Int>): List<Int> = host.appWidgetIds.filter { it !in used }

    /** Apps with widgets, alphabetically, our own excluded. */
    fun providers(): List<WidgetProviderGroup> {
        val pm = appContext.packageManager
        val users = appContext.getSystemService(UserManager::class.java).userProfiles
        return users.flatMap { manager.getInstalledProvidersForProfile(it) }
            .filter { it.provider.packageName != appContext.packageName }
            .groupBy { it.provider.packageName }
            .map { (pkg, infos) ->
                val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                WidgetProviderGroup(pkg, app?.loadLabel(pm)?.toString() ?: pkg, app?.loadIcon(pm), infos)
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Size the widget asks for, in cells (Android 12 target cells, else from its minimum size). */
    fun defaultSpan(info: AppWidgetProviderInfo): Span =
        if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            Span(info.targetCellWidth.coerceIn(1, 4), info.targetCellHeight.coerceIn(1, 4))
        } else {
            Span(cells(info.minWidth), cells(info.minHeight))
        }

    /** Sizes the widget menu offers for this widget. */
    fun spans(info: AppWidgetProviderInfo): List<Span> {
        val resizable = info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE
        val min = Span(cells(info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth), cells(info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight))
        val max = if (info.maxResizeWidth > 0 && info.maxResizeHeight > 0) Span(cells(info.maxResizeWidth), cells(info.maxResizeHeight)) else null
        return appWidgetSpans(defaultSpan(info), resizable, min, max)
    }

    /** Cells needed for a size in px; a cell is about [CELL_DP] wide on a phone. */
    private fun cells(px: Int): Int {
        val dp = px / appContext.resources.displayMetrics.density
        return ceil(dp / CELL_DP).toInt().coerceIn(1, 4)
    }

    fun providerLabel(provider: String): String? {
        val component = ComponentName.unflattenFromString(provider) ?: return null
        return manager.installedProviders.firstOrNull { it.provider == component }?.loadLabel(appContext.packageManager)
    }

    companion object {
        /** Our AppWidgetHost id; any constant works as long as it never changes. */
        private const val HOST_ID = 1027
        private const val CELL_DP = 96f

        @Volatile
        private var instance: AppWidgets? = null

        fun get(context: Context): AppWidgets =
            instance ?: synchronized(this) { instance ?: AppWidgets(context).also { instance = it } }
    }
}

private class ZflWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        ZflWidgetHostView(context)
}

/**
 * Widget view that lets its content handle taps (buttons, lists) but takes over on a
 * long press, like Launcher3: then it opens our widget menu, which may turn into a drag.
 * It also tells the widget its real size, so responsive (Android 12+) layouts fit the cell.
 */
class ZflWidgetHostView(context: Context) : AppWidgetHostView(context) {
    /** Called on a long press with the finger position in window coordinates. */
    var onLongPress: ((Offset) -> Unit)? = null

    /** Corner radius of the clip, in px. */
    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
        }

    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        AppWidgetSnapshots.capture(appWidgetId, this)
        val location = IntArray(2).also(::getLocationInWindow)
        onLongPress?.invoke(Offset(location[0] + downX, location[1] + downY))
    }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
        clipToOutline = true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = event.x
                downY = event.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        // After a long press the gesture is ours: the widget's buttons get a cancel.
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress)
            longPressed = false
        }
        return longPressed || super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val density = resources.displayMetrics.density
        updateAppWidgetSize(Bundle(), listOf(SizeF(w / density, h / density)))
    }
}

/**
 * Last image of each hosted widget, taken on long press: the menu and drag show it, since
 * one widget id can't be shown live in two views at once.
 */
object AppWidgetSnapshots {
    private val cache = LruCache<Int, ImageBitmap>(4)

    fun capture(appWidgetId: Int, view: View) {
        if (view.width == 0 || view.height == 0) return
        runCatching { view.drawToBitmap() }.getOrNull()?.let { cache.put(appWidgetId, it.asImageBitmap()) }
    }

    fun get(appWidgetId: Int): ImageBitmap? = cache.get(appWidgetId)
}
