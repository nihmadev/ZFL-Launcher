package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.Forecast
import com.zfl.launcher.data.WeatherCondition
import com.zfl.launcher.data.WeatherState
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.data.displayTemperature
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

private val Dim = Color.White.copy(alpha = 0.7f)
private val Divider = Color.White.copy(alpha = 0.18f)

/** Weather in one of three sizes, sized after the iOS27L widget; without a place or data it asks to set one up. */
@Composable
internal fun WeatherWidget(size: WidgetSize) {
    val weather = LocalWidgetEnvironment.current.weather
    val forecast = weather.forecast
    if (weather.place == null || forecast == null) {
        WeatherPlaceholder(weather.place == null)
        return
    }
    // Glyphs cut a thin gap into the cloud in the card's own color, like SF Symbols.
    CompositionLocalProvider(LocalGlyphCutout provides weatherColors(forecast.condition, forecast.isDay).first()) {
        when (size) {
            WidgetSize.Small -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                SnapshotContent(weather, forecast)
            }
            WidgetSize.Medium -> Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
                HeaderRow(weather, forecast)
                Spacer(Modifier.weight(1f))
                HourlyRow(forecast, weather.fahrenheit)
            }
            WidgetSize.Large -> Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)) {
                HeaderRow(weather, forecast)
                Spacer(Modifier.height(14.dp))
                HourlyRow(forecast, weather.fahrenheit)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
                Spacer(Modifier.height(10.dp))
                DailyList(forecast, weather.fahrenheit)
            }
        }
    }
}

@Composable
private fun ColumnScope.SnapshotContent(weather: WeatherState, forecast: Forecast) {
    PlaceName(weather.place?.name.orEmpty(), 16.sp)
    Temperature(forecast.temperature, weather.fahrenheit, 48.sp)
    Spacer(Modifier.weight(1f))
    WeatherGlyph(forecast.condition, forecast.isDay, 22.dp)
    Spacer(Modifier.height(4.dp))
    ConditionText(forecast.condition, 15.sp)
    HighLow(forecast, weather.fahrenheit, 15.sp)
}

@Composable
private fun HeaderRow(weather: WeatherState, forecast: Forecast) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            PlaceName(weather.place?.name.orEmpty(), 17.sp)
            Temperature(forecast.temperature, weather.fahrenheit, 56.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Spacer(Modifier.height(2.dp))
            WeatherGlyph(forecast.condition, forecast.isDay, 22.dp)
            Spacer(Modifier.height(6.dp))
            ConditionText(forecast.condition, 17.sp)
            HighLow(forecast, weather.fahrenheit, 17.sp)
        }
    }
}

@Composable
private fun HourlyRow(forecast: Forecast, fahrenheit: Boolean) {
    val format = DateTimeFormatter.ofPattern("HH")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        forecast.upcomingHours(HOURS_SHOWN).forEach { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hour.time.format(format), color = Dim, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                WeatherGlyph(hour.condition, hour.isDay, 24.dp)
                Spacer(Modifier.height(3.dp))
                Text(degrees(hour.temperature, fahrenheit), color = Color.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val HOURS_SHOWN = 6

@Composable
private fun DailyList(forecast: Forecast, fahrenheit: Boolean) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        forecast.daily.forEach { day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                WeatherGlyph(day.condition, isDay = true, size = 22.dp)
                Spacer(Modifier.width(40.dp))
                Text(degrees(day.max, fahrenheit), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
                Text(degrees(day.min, fahrenheit), color = Dim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(46.dp))
            }
        }
    }
}

@Composable
private fun PlaceName(name: String, size: TextUnit) {
    // "Samara, Samara Oblast, Russia" → "Samara": the widget shows the city, like iOS.
    Text(name.substringBefore(','), color = Color.White, fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
}

@Composable
private fun Temperature(celsius: Double, fahrenheit: Boolean, size: TextUnit) {
    Text(degrees(celsius, fahrenheit), color = Color.White, fontSize = size, fontWeight = FontWeight.Light, lineHeight = size * 1.05f)
}

@Composable
private fun ConditionText(condition: WeatherCondition, size: TextUnit) {
    Text(stringResource(condition.labelRes), color = Color.White, fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
private fun HighLow(forecast: Forecast, fahrenheit: Boolean, size: TextUnit) {
    Text(
        stringResource(R.string.weather_high_low, degrees(forecast.todayMax, fahrenheit), degrees(forecast.todayMin, fahrenheit)),
        color = Color.White,
        fontSize = size,
        lineHeight = size * 1.2f,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun WeatherPlaceholder(noPlace: Boolean) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        WeatherGlyph(WeatherCondition.PartlyCloudy, isDay = true, size = 28.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (noPlace) R.string.weather_set_location else R.string.weather_loading),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "14°C", with the unit like the iOS27L widget. */
private fun degrees(celsius: Double, fahrenheit: Boolean): String =
    "${displayTemperature(celsius, fahrenheit)}°" + if (fahrenheit) "F" else "C"

/** Color of the gap cut around clouds in [WeatherGlyph]; the widget sets it to its background. */
private val LocalGlyphCutout = compositionLocalOf { Color(0xFF101623) }

/**
 * Condition icon built from Material glyphs, like the SF Symbols iOS uses: the sun or moon
 * peeks out behind a cloud that is outlined by a thin gap in the card color, the night sky
 * gets small stars, rain and drizzle hang under a cloud.
 */
@Composable
internal fun WeatherGlyph(condition: WeatherCondition, isDay: Boolean, size: Dp, tint: Color = Color.White) {
    val sky = if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime
    val skyTint = if (isDay) SunYellow else tint
    Box(Modifier.size(size)) {
        when (condition) {
            WeatherCondition.Clear -> {
                Icon(sky, null, tint = skyTint, modifier = Modifier.size(size * if (isDay) 1f else 0.82f).align(Alignment.BottomEnd))
                if (!isDay) Stars(size, tint)
            }
            WeatherCondition.MainlyClear, WeatherCondition.PartlyCloudy -> {
                Icon(sky, null, tint = skyTint, modifier = Modifier.size(size * 0.58f).align(Alignment.TopEnd))
                if (!isDay) Stars(size, tint)
                CutoutCloud(size * 0.82f, tint, Modifier.align(Alignment.BottomStart))
            }
            WeatherCondition.Cloudy -> Icon(Icons.Rounded.Cloud, null, tint = tint, modifier = Modifier.size(size))
            WeatherCondition.Fog -> Icon(Icons.Rounded.Dehaze, null, tint = tint, modifier = Modifier.size(size))
            WeatherCondition.Thunderstorm -> Icon(Icons.Rounded.Thunderstorm, null, tint = tint, modifier = Modifier.size(size))
            WeatherCondition.Snow, WeatherCondition.SnowShowers -> Icon(Icons.Rounded.AcUnit, null, tint = tint, modifier = Modifier.size(size))
            WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.FreezingRain, WeatherCondition.Showers -> {
                val drops = if (condition == WeatherCondition.Drizzle) Icons.Rounded.Grain else Icons.Rounded.WaterDrop
                Icon(drops, null, tint = RainBlue, modifier = Modifier.size(size * 0.4f).align(Alignment.BottomCenter).offset(y = size * 0.06f))
                CutoutCloud(size * 0.8f, tint, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** A cloud with a thin border of the card color, so it reads apart from what is behind it. */
@Composable
private fun CutoutCloud(size: Dp, tint: Color, modifier: Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Cloud, null, tint = LocalGlyphCutout.current, modifier = Modifier.size(size * 1.16f))
        Icon(Icons.Rounded.Cloud, null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun BoxScope.Stars(size: Dp, tint: Color) {
    Icon(Icons.Rounded.AutoAwesome, null, tint = tint, modifier = Modifier.size(size * 0.34f).align(Alignment.TopStart).offset(x = size * 0.08f))
}

private val SunYellow = Color(0xFFFFD60A)
private val RainBlue = Color(0xFF64D2FF)

val WeatherCondition.labelRes: Int
    get() = when (this) {
        WeatherCondition.Clear -> R.string.weather_clear
        WeatherCondition.MainlyClear -> R.string.weather_mainly_clear
        WeatherCondition.PartlyCloudy -> R.string.weather_partly_cloudy
        WeatherCondition.Cloudy -> R.string.weather_cloudy
        WeatherCondition.Fog -> R.string.weather_fog
        WeatherCondition.Drizzle -> R.string.weather_drizzle
        WeatherCondition.Rain -> R.string.weather_rain
        WeatherCondition.FreezingRain -> R.string.weather_freezing_rain
        WeatherCondition.Snow -> R.string.weather_snow
        WeatherCondition.Showers -> R.string.weather_showers
        WeatherCondition.SnowShowers -> R.string.weather_snow_showers
        WeatherCondition.Thunderstorm -> R.string.weather_thunderstorm
    }
