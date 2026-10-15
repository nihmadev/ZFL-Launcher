package com.zfl.launcher.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Weather condition groups, mapped from WMO weather codes (as used by Open-Meteo). */
enum class WeatherCondition {
    Clear, MainlyClear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, FreezingRain, Snow, Showers, SnowShowers, Thunderstorm;

    companion object {
        /** WMO code table: https://open-meteo.com/en/docs (section "WMO Weather interpretation codes"). */
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0 -> Clear
            1 -> MainlyClear
            2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55 -> Drizzle
            56, 57, 66, 67 -> FreezingRain
            61, 63, 65 -> Rain
            71, 73, 75, 77 -> Snow
            80, 81, 82 -> Showers
            85, 86 -> SnowShowers
            95, 96, 99 -> Thunderstorm
            else -> Cloudy
        }
    }
}

data class HourForecast(val time: LocalDateTime, val temperature: Double, val condition: WeatherCondition, val isDay: Boolean)

data class DayForecast(val date: LocalDate, val max: Double, val min: Double, val condition: WeatherCondition)

/** A forecast for one place. Temperatures are in °C; conversion happens when displayed. */
data class Forecast(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val todayMax: Double,
    val todayMin: Double,
    /** Hours from the one current at fetch time, a day ahead; see [upcomingHours]. */
    val hourly: List<HourForecast>,
    /** The following days, starting tomorrow. */
    val daily: List<DayForecast>,
    val fetchedAt: Long,
    /** Offset of the place's time zone, so "now" there can be computed later. */
    val utcOffsetSeconds: Int = 0,
) {
    /** The next [count] hours from the current hour at the place (the cache may be up to 30 min old). */
    fun upcomingHours(count: Int, nowMillis: Long = System.currentTimeMillis()): List<HourForecast> {
        val now = LocalDateTime.ofEpochSecond(nowMillis / 1000, 0, ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.dropWhile { it.time.isBefore(hour) }.take(count).ifEmpty { hourly.takeLast(count) }
    }
}

/** A place the weather is shown for. [current] = follows the device location. */
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double, val current: Boolean = false)

/** Hours kept from a forecast: enough to stay correct until the next refresh and offline for a while. */
private const val HOURS_KEPT = 24
private const val DAYS_SHOWN = 5

/**
 * Parses an Open-Meteo `/v1/forecast` response requested with `timezone=auto` and
 * `current=temperature_2m,weather_code,is_day`, `hourly=temperature_2m,weather_code,is_day`,
 * `daily=weather_code,temperature_2m_max,temperature_2m_min`. Times are local to the place.
 */
fun parseForecast(json: String, fetchedAt: Long): Forecast {
    val root = JSONObject(json)
    val current = root.getJSONObject("current")
    val now = LocalDateTime.parse(current.getString("time"))

    val hourly = root.getJSONObject("hourly")
    val hourTimes = hourly.getJSONArray("time")
    val firstHour = (0 until hourTimes.length()).firstOrNull { i ->
        !LocalDateTime.parse(hourTimes.getString(i)).isBefore(now.withMinute(0))
    } ?: 0
    val hours = (firstHour until minOf(firstHour + HOURS_KEPT, hourTimes.length())).map { i ->
        HourForecast(
            time = LocalDateTime.parse(hourTimes.getString(i)),
            temperature = hourly.getJSONArray("temperature_2m").getDouble(i),
            condition = WeatherCondition.fromWmo(hourly.getJSONArray("weather_code").getInt(i)),
            isDay = hourly.getJSONArray("is_day").getInt(i) == 1,
        )
    }

    val daily = root.getJSONObject("daily")
    val dayTimes = daily.getJSONArray("time")
    val days = (0 until dayTimes.length()).map { i ->
        DayForecast(
            date = LocalDate.parse(dayTimes.getString(i)),
            max = daily.getJSONArray("temperature_2m_max").getDouble(i),
            min = daily.getJSONArray("temperature_2m_min").getDouble(i),
            condition = WeatherCondition.fromWmo(daily.getJSONArray("weather_code").getInt(i)),
        )
    }
    val today = days.firstOrNull { it.date == now.toLocalDate() } ?: days.first()

    return Forecast(
        temperature = current.getDouble("temperature_2m"),
        condition = WeatherCondition.fromWmo(current.getInt("weather_code")),
        isDay = current.getInt("is_day") == 1,
        todayMax = today.max,
        todayMin = today.min,
        hourly = hours,
        daily = days.filter { it.date.isAfter(today.date) }.take(DAYS_SHOWN),
        fetchedAt = fetchedAt,
        utcOffsetSeconds = root.optInt("utc_offset_seconds"),
    )
}

/** Parses an Open-Meteo geocoding `/v1/search` response into places ("City, Region, Country"). */
fun parsePlaces(json: String): List<WeatherPlace> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return List(results.length()) { i ->
        val r = results.getJSONObject(i)
        val name = listOf(r.optString("name"), r.optString("admin1"), r.optString("country"))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        WeatherPlace(name, r.getDouble("latitude"), r.getDouble("longitude"))
    }
}

/** Rounds a °C value for display, converting to °F when asked. */
fun displayTemperature(celsius: Double, fahrenheit: Boolean): Int =
    Math.round(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).toInt()
