package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.YearMonth

class WeatherDataTest {
    private val json = """
        {"utc_offset_seconds":14400,"current":{"time":"2026-10-15T19:30","temperature_2m":14.2,"weather_code":1,"is_day":0},
         "hourly":{"time":["2026-10-15T18:00","2026-10-15T19:00","2026-10-15T20:00","2026-10-15T21:00","2026-10-15T22:00","2026-10-15T23:00","2026-10-16T00:00","2026-10-16T01:00"],
                   "temperature_2m":[16,15,14,13,12,11,11,10],"weather_code":[0,1,2,3,61,45,95,71],"is_day":[1,0,0,0,0,0,0,0]},
         "daily":{"time":["2026-10-15","2026-10-16","2026-10-17"],"weather_code":[1,3,61],
                  "temperature_2m_max":[22.0,18.0,17.0],"temperature_2m_min":[11.0,7.0,7.0]}}
    """.trimIndent()

    @Test
    fun parsesCurrentHourlyAndDaily() {
        val forecast = parseForecast(json, fetchedAt = 1L)
        assertEquals(WeatherCondition.MainlyClear, forecast.condition)
        assertEquals(22.0, forecast.todayMax, 0.0)
        assertEquals(11.0, forecast.todayMin, 0.0)
        // Hourly starts with the current hour (19:00), six hours.
        assertEquals(listOf(19, 20, 21, 22, 23, 0), forecast.upcomingHours(6, at("2026-10-15T19:30")).map { it.time.hour })
        assertEquals(WeatherCondition.Rain, forecast.hourly[3].condition)
        // Shown later from the cache, the row starts at the hour that is current at the place.
        assertEquals(listOf(21, 22, 23, 0, 1), forecast.upcomingHours(6, at("2026-10-15T21:10")).map { it.time.hour })
        // Daily starts tomorrow.
        assertEquals(listOf(16, 17), forecast.daily.map { it.date.dayOfMonth })
    }

    /** Epoch millis of a local time at the test place (UTC+4). */
    private fun at(local: String): Long = LocalDateTime.parse(local).toEpochSecond(ZoneOffset.ofHours(4)) * 1000

    @Test
    fun convertsToFahrenheit() {
        assertEquals(57, displayTemperature(14.0, fahrenheit = true))
        assertEquals(-4, displayTemperature(-4.4, fahrenheit = false))
    }

    @Test
    fun monthStartsOnLocaleWeekday() {
        // October 2026 starts on a Thursday.
        val mondayFirst = monthCells(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(listOf(null, null, null, 1), mondayFirst.take(4))
        val sundayFirst = monthCells(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, sundayFirst.indexOf(1))
        assertEquals(31, mondayFirst.last())
    }
}
