package com.zfl.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.CalendarEvent
import com.zfl.launcher.data.monthCells
import com.zfl.launcher.data.weekdays
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/** The red circle around today, sampled from the iOS27L calendar widget. */
private val TodayRed = Color(0xFFD5372F)

/** Today's date; changes at midnight (the clock ticks once a minute). */
@Composable
private fun rememberToday(): LocalDate {
    val now by rememberNow(60_000)
    return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Small "Month": month name and the days of the month, today in a red circle. */
@Composable
internal fun MonthWidget() {
    val today = rememberToday()
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp)) {
        MonthGrid(today, cellHeight = 18.dp, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
    }
}

/** Medium "Up Next": weekday and date with the next events on the left, the month on the right. */
@Composable
internal fun UpNextWidget() {
    val today = rememberToday()
    val environment = LocalWidgetEnvironment.current
    val locale = currentLocale()
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                today.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale).uppercase(locale),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(today.dayOfMonth.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(6.dp))
            when {
                !environment.calendarAllowed -> Text(stringResource(R.string.calendar_no_permission), color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
                environment.events.isEmpty() -> Text(stringResource(R.string.calendar_no_events), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                else -> environment.events.take(2).forEach { EventRow(it) }
            }
        }
        Spacer(Modifier.width(12.dp))
        MonthGrid(today, cellHeight = 17.dp, fontSize = 10.sp, modifier = Modifier.width(170.dp))
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    val zone = ZoneId.systemDefault()
    val time = if (event.allDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.begin))
    }
    val day = Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
    val dayLabel = if (day == LocalDate.now(zone)) "" else day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, currentLocale()) + " · "
    Row(Modifier.padding(bottom = 6.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .background(Color(event.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(event.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dayLabel + time, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Month name, weekday initials and day numbers, weeks starting on the locale's first day. */
@Composable
private fun MonthGrid(today: LocalDate, cellHeight: Dp, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val month = YearMonth.from(today)
    val cells = monthCells(month, firstDay)
    Column(modifier) {
        Text(
            month.month.getDisplayName(JavaTextStyle.FULL_STANDALONE, locale).uppercase(locale),
            color = Color.White,
            fontSize = fontSize * 1.05f,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdays(firstDay).forEach { day ->
                DayCell(day.getDisplayName(JavaTextStyle.NARROW_STANDALONE, locale), cellHeight, fontSize, bold = true)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day -> DayCell(day?.toString().orEmpty(), cellHeight, fontSize, today = day == today.dayOfMonth) }
                repeat(7 - week.size) { DayCell("", cellHeight, fontSize) }
            }
        }
    }
}

@Composable
private fun DayCell(text: String, height: Dp, fontSize: TextUnit, bold: Boolean = false, today: Boolean = false) {
    Box(Modifier.size(height + 2.dp, height), contentAlignment = Alignment.Center) {
        if (today) Box(Modifier.size(height).background(TodayRed, CircleShape))
        Text(
            text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = if (bold || today) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Gallery icon of the calendar: white tile with the weekday in red and today's date. */
@Composable
internal fun CalendarIcon(size: Dp) {
    val today = rememberToday()
    val locale = currentLocale()
    Column(
        Modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(size * 0.22f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(today.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale), color = TodayRed, fontSize = (size.value * 0.2f).sp, fontWeight = FontWeight.SemiBold)
        Text(today.dayOfMonth.toString(), color = Color.Black, fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.44f).sp)
    }
}

/** The UI locale, read so that composables update when the user switches languages. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
