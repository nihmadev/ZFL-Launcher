package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zfl.launcher.data.AppWidgetEntry
import com.zfl.launcher.data.PageWidgetEntry
import com.zfl.launcher.data.WidgetStackEntry
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.CalendarEvent
import com.zfl.launcher.data.WeatherCondition
import com.zfl.launcher.data.WeatherState
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens

/**
 * Size every widget is designed at (iOS widget points). The content is laid out at this
 * size and scaled to the space it gets, so a widget looks the same on any grid or screen.
 */
val WidgetSize.designSize: DpSize
    get() = when (this) {
        WidgetSize.Small -> DpSize(170.dp, 170.dp)
        WidgetSize.Medium -> DpSize(364.dp, 170.dp)
        WidgetSize.Large -> DpSize(364.dp, 382.dp)
    }

/** Corner radius at [designSize]; it scales with the widget. */
private val WidgetRadius = 22.dp

/** Live data that widgets show. Provided once by the home screen. */
@Immutable
data class WidgetEnvironment(
    val weather: WeatherState = WeatherState(),
    val suggestions: List<AppInfo> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val calendarAllowed: Boolean = false,
)

val LocalWidgetEnvironment = compositionLocalOf { WidgetEnvironment() }

/**
 * A widget's card filling the size given by [modifier]. The content is laid out on a canvas
 * with the design height (see [designSize]) and the card's aspect ratio, then scaled to the
 * card, so text keeps its proportions while the card uses all the space it gets.
 */
@Composable
fun WidgetBody(entry: WidgetEntry, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val design = entry.size.designSize
        val scale = min(maxWidth / design.width.value, maxHeight / design.height.value).value
        val canvas = DpSize(maxWidth / scale, maxHeight / scale)
        val shape = ContinuousRoundedShape(WidgetRadius * scale)
        Box(
            Modifier
                .fillMaxSize()
                .widgetBackground(entry, shape)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(canvas)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) { WidgetContent(entry) }
        }
    }
}

@Composable
private fun WidgetContent(entry: WidgetEntry) {
    when (entry.kind) {
        WidgetKind.Clock -> if (entry.size == WidgetSize.Small) AnalogClockWidget() else WorldClockWidget()
        WidgetKind.Calendar -> if (entry.size == WidgetSize.Small) MonthWidget() else UpNextWidget()
        WidgetKind.Weather -> WeatherWidget(entry.size)
        WidgetKind.Photo -> PhotoWidget(entry)
        WidgetKind.Battery -> BatteryWidget()
        WidgetKind.Suggestions -> SuggestionsWidget(entry.size)
    }
}

@Composable
private fun Modifier.widgetBackground(entry: WidgetEntry, shape: ContinuousRoundedShape): Modifier = when (entry.kind) {
    WidgetKind.Suggestions -> glass(shape, tint = Color.White.copy(alpha = 0.06f))
    WidgetKind.Weather -> {
        val forecast = LocalWidgetEnvironment.current.weather.forecast
        background(weatherBrush(forecast?.condition, forecast?.isDay ?: false), shape)
            .border(1.dp, WidgetStroke, shape)
    }
    else -> background(Tokens.WidgetBackground, shape).border(1.dp, WidgetStroke, shape)
}

private val WidgetStroke = Color.White.copy(alpha = 0.10f)

/** Sky colors behind the weather widget (top to bottom), measured from the iOS27L night widget. */
internal fun weatherColors(condition: WeatherCondition?, isDay: Boolean): List<Color> {
    val overcast = condition != null && condition !in setOf(WeatherCondition.Clear, WeatherCondition.MainlyClear, WeatherCondition.PartlyCloudy)
    return when {
        !isDay -> listOf(Color(0xFF101623), Color(0xFF232938))
        overcast -> listOf(Color(0xFF4E5F73), Color(0xFF7A8A9C))
        else -> listOf(Color(0xFF2C6CC9), Color(0xFF5C9DE2))
    }
}

internal fun weatherBrush(condition: WeatherCondition?, isDay: Boolean): Brush = Brush.verticalGradient(weatherColors(condition, isDay))

/**
 * What a tap at [position] (relative to the widget, 0..1 on both axes) refers to: the
 * suggested app under the finger for [WidgetKind.Suggestions], null otherwise.
 */
fun suggestionAt(entry: WidgetEntry, suggestions: List<AppInfo>, position: Offset): AppInfo? {
    if (entry.kind != WidgetKind.Suggestions) return null
    val columns = if (entry.size == WidgetSize.Small) 2 else 4
    val col = (position.x * columns).toInt().coerceIn(0, columns - 1)
    val row = (position.y * 2).toInt().coerceIn(0, 1)
    return suggestions.getOrNull(row * columns + col)
}

/** Number of apps a suggestions widget shows. */
fun WidgetSize.suggestionCount(): Int = if (this == WidgetSize.Small) 4 else 8

// region Names shown in the gallery and menus

val WidgetKind.titleRes: Int
    get() = when (this) {
        WidgetKind.Clock -> R.string.widget_clock
        WidgetKind.Calendar -> R.string.widget_calendar
        WidgetKind.Weather -> R.string.widget_weather
        WidgetKind.Photo -> R.string.widget_photo
        WidgetKind.Battery -> R.string.widget_battery
        WidgetKind.Suggestions -> R.string.widget_suggestions
    }

val WidgetKind.descriptionRes: Int
    get() = when (this) {
        WidgetKind.Clock -> R.string.widget_clock_description
        WidgetKind.Calendar -> R.string.widget_calendar_description
        WidgetKind.Weather -> R.string.widget_weather_description
        WidgetKind.Photo -> R.string.widget_photo_description
        WidgetKind.Battery -> R.string.widget_battery_description
        WidgetKind.Suggestions -> R.string.widget_suggestions_description
    }

/** Name of one size variant, e.g. "World Clock" or "Hourly Outlook". */
fun variantTitleRes(kind: WidgetKind, size: WidgetSize): Int = when (kind) {
    WidgetKind.Clock -> if (size == WidgetSize.Small) R.string.widget_variant_clock else R.string.widget_variant_world_clock
    WidgetKind.Calendar -> if (size == WidgetSize.Small) R.string.widget_variant_month else R.string.widget_variant_up_next
    WidgetKind.Weather -> when (size) {
        WidgetSize.Small -> R.string.widget_variant_weather_snapshot
        WidgetSize.Medium -> R.string.widget_variant_hourly
        WidgetSize.Large -> R.string.widget_variant_full_forecast
    }
    WidgetKind.Photo -> when (size) {
        WidgetSize.Small -> R.string.widget_variant_photo_small
        WidgetSize.Medium -> R.string.widget_variant_photo_medium
        WidgetSize.Large -> R.string.widget_variant_photo_large
    }
    WidgetKind.Battery -> R.string.widget_variant_status
    WidgetKind.Suggestions -> R.string.widget_variant_app_suggestion
}

// endregion

/**
 * Any page widget's card: a built-in widget, or another app's widget hosted live
 * ([live] = false draws its last snapshot instead, for previews and the dragged copy).
 *
 * @param onLongPress long press on a live hosted widget, with the finger in window coordinates.
 */
@Composable
fun PageWidgetBody(entry: PageWidgetEntry, modifier: Modifier = Modifier, live: Boolean = true, onLongPress: ((Offset) -> Unit)? = null) {
    when (entry) {
        is WidgetEntry -> WidgetBody(entry, modifier)
        is AppWidgetEntry -> if (live) HostedWidget(entry, modifier, onLongPress) else HostedSnapshot(entry, modifier)
        // Previews (menu, drag) show the stack's current widget; the home screen uses WidgetStackBody.
        is WidgetStackEntry -> PageWidgetBody(entry.currentItem, modifier, live, onLongPress)
    }
}

/**
 * Another app's widget, live. With a [cache] (a stack) the host view of a widget id is
 * created once and reattached, instead of being inflated again each time it is shown.
 */
@Composable
internal fun HostedWidget(
    entry: AppWidgetEntry,
    modifier: Modifier,
    onLongPress: ((Offset) -> Unit)?,
    cache: MutableMap<Int, ZflWidgetHostView>? = null,
    onView: ((ZflWidgetHostView) -> Unit)? = null,
) {
    val context = LocalContext.current
    val widgets = remember { AppWidgets.get(context) }
    val info = remember(entry.appWidgetId) { widgets.info(entry.appWidgetId) }
    if (info == null) {
        HostedSnapshot(entry, modifier)
        return
    }
    val radius = with(LocalDensity.current) { HostedRadius.toPx() }
    val currentLongPress by rememberUpdatedState(onLongPress)
    AndroidView(
        factory = { ctx ->
            cache?.get(entry.appWidgetId)?.also { (it.parent as? ViewGroup)?.removeView(it) }
                ?: (widgets.host.createView(ctx, entry.appWidgetId, info) as ZflWidgetHostView).also { cache?.put(entry.appWidgetId, it) }
        },
        update = { view ->
            view.cornerRadius = radius
            view.onLongPress = { pointer -> currentLongPress?.invoke(pointer) }
            onView?.invoke(view)
        },
        modifier = modifier,
    )
}

/** Last image of a hosted widget, or a plain card with its name when there is none yet. */
@Composable
internal fun HostedSnapshot(entry: AppWidgetEntry, modifier: Modifier) {
    val shape = ContinuousRoundedShape(HostedRadius)
    val snapshot = AppWidgetSnapshots.get(entry.appWidgetId)
    Box(modifier.clip(shape).background(Tokens.WidgetBackground), contentAlignment = Alignment.Center) {
        if (snapshot != null) {
            Image(snapshot, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            val context = LocalContext.current
            val label = remember(entry.provider) { AppWidgets.get(context).providerLabel(entry.provider) }
            Text(label.orEmpty(), color = Tokens.TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Corner radius of hosted widgets, close to Android 12's system widget radius. */
private val HostedRadius = 22.dp
