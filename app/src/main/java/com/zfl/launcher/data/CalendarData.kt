package com.zfl.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * Cells of a month view: weeks start on [firstDayOfWeek]; days before the 1st are null.
 * Trailing empty cells are not added, so the last row may be shorter.
 */
fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<Int?> {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    return List(lead) { null } + (1..month.lengthOfMonth()).toList()
}

/** The seven weekdays in display order, starting from [firstDayOfWeek]. */
fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = List(7) { firstDayOfWeek.plus(it.toLong()) }

/** An upcoming calendar event for the "Up Next" widget. Times are epoch millis. */
data class CalendarEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

/** Reads upcoming events from the system calendar (needs READ_CALENDAR). */
object CalendarEvents {
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that haven't ended yet, over the next [days] days, soonest first. */
    suspend fun upcoming(context: Context, limit: Int = 3, days: Int = 7): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + days * DAY_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val end = c.getLong(2)
                        if (end < now) continue
                        add(CalendarEvent(c.getString(0).orEmpty(), c.getLong(1), end, c.getInt(3) == 1, c.getInt(4)))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
