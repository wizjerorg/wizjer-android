package org.wizjer.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.wizjer.android.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * "Przed chwilą", "5 minut temu", "Wczoraj", "3 dni temu", or the date for anything older,
 * with the right plural forms for each language (Polish and Ukrainian have three).
 */
@Composable
fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(time).atZone(zone)
    val current = Instant.ofEpochMilli(now).atZone(zone)
    val minutes = ChronoUnit.MINUTES.between(then, current)
    val days = ChronoUnit.DAYS.between(then.toLocalDate(), current.toLocalDate())

    return when {
        minutes < 1 -> stringResource(R.string.time_just_now) // also covers small clock differences
        minutes < 60 -> pluralStringResource(R.plurals.time_minutes_ago, minutes.toInt(), minutes.toInt())
        days == 0L -> {
            val hours = (minutes / 60).toInt()
            pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        }
        days == 1L -> stringResource(R.string.time_yesterday)
        days < 7 -> pluralStringResource(R.plurals.time_days_ago, days.toInt(), days.toInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            then.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
}
